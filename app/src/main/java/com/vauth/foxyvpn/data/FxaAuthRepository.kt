package com.vauth.foxyvpn.data

import android.util.Base64
import com.vauth.foxyvpn.data.model.BatchImportItemResult
import com.vauth.foxyvpn.data.model.RuntimeAuth
import com.vauth.foxyvpn.data.model.VpnAccount
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "FxaAuthRepository"

enum class SessionStatus {
    ACTIVE,
    NEEDS_LOGIN,
    UNREACHABLE,
}

class FxaApiError(message: String, val errno: Int?, val statusCode: Int?) : Exception(message)

val FxaApiError.isUnconfirmedSession: Boolean
    get() =
        errno == 104 ||
            (statusCode == 400 &&
                message?.contains("Unconfirmed session", ignoreCase = true) == true)

fun Throwable.isUnconfirmedSession(): Boolean =
    (this as? FxaApiError)?.isUnconfirmedSession == true ||
        message?.contains("Unconfirmed session", ignoreCase = true) == true

class FxaRefreshFailure(message: String, val permanent: Boolean, cause: Throwable? = null) :
    Exception(message, cause)

class FastlyBlockedException(message: String, cause: Throwable? = null) : Exception(message, cause)

private const val FXA_AUTH_SERVER = "https://api.accounts.firefox.com/v1"
private const val FXA_OAUTH_SERVER = "https://oauth.accounts.firefox.com/v1"
private const val FIREFOX_CLIENT_ID = "5882386c6d801776"
private const val OAUTH_SCOPE = "profile https://identity.mozilla.com/apps/vpn"
private const val PROTOCOL_VERSION = "identity.mozilla.com/picl/v1/"
private const val FXA_MAX_CHALLENGE_ATTEMPTS = 5
private const val DEFAULT_ACCESS_TOKEN_TTL_SECONDS = 24 * 60 * 60

// Fork additions: legacy email/password sign-in crypto constants.
private const val PBKDF2_ROUNDS = 1000
private const val STRETCHED_PW_LEN = 32
private const val HKDF_LEN = 32
private const val VERIFICATION_METHOD_EMAIL_2FA = "email-2fa"
private const val FXA_ERRNO_INVALID_PARAMETER = 107

private const val WEBCHANNEL_LOGIN_URL =
    "https://accounts.firefox.com/?context=fx_desktop_v3&service=sync&action=email"

private val jsonMediaType = "application/json".toMediaType()

class FxaAuthRepository(
    private val tokenStore: TokenStore,
    private val challengeSolver: FastlyChallengeSolver =
        FastlyChallengeSolver(ControlPlaneHttp.cookieJar),
    // Fork addition: multi-account storage (pending slot + account list).
    private val accountStore: AccountStore? = null,
) {
    private val refreshMutex = Mutex()
    private val loginMutex = Mutex()
    private val client =
        ControlPlaneHttp.client
            .newBuilder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    val storageError = tokenStore.storageError

    fun getBrowserLoginUrl(): String = WEBCHANNEL_LOGIN_URL

    // ==================================================================
    // FORK ADDITIONS (veloracode) — everything below in this banner is
    // the fork's layer; upstream v2 has no counterpart for any of it.
    // Storage split: the pending sign-in slot and the account list live
    // in AccountStore; upstream's TokenStore keeps ONLY the active
    // session. All FxA traffic reuses upstream's executeFxaRequest /
    // oauthToken / crypto helpers above — no parallel auth stack.
    // ==================================================================

    private fun accounts(): AccountStore =
        accountStore ?: error("Multi-account storage is not wired in this build.")

    /** The signed-in account's email from the OAuth profile endpoint, or null. */
    private suspend fun fetchProfileEmail(accessToken: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("$FXA_OAUTH_SERVER/profile")
                    .applyFirefoxHeaders()
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build()
                ControlPlaneHttp.client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val text = resp.body?.string().orEmpty()
                    if (text.isEmpty()) null
                    else JSONObject(text).optString("email").ifBlank { null }
                }
            }.getOrNull()
        }

    /** Profile email of the session currently in the TokenStore, if resolvable. */
    suspend fun profileEmailForCurrentSession(): String? {
        val auth = tokenStore.loadAuth() ?: return null
        return fetchProfileEmail(auth.accessToken)
    }

    // ------------------------------------------------------------------
    // Pending sign-in (email verification code). FxA returns new-device
    // sessions unverified; the emailed code must be submitted to
    // /session/verify_code before any token can be minted. The pending
    // session is persisted in AccountStore — keeping it only in memory
    // lost it whenever the process died while the user was reading the
    // code in their mail app (the v1 fork's dead-end bug).
    // ------------------------------------------------------------------

    enum class PendingKind { LEGACY, ACCOUNT }

    data class PendingLogin(val email: String, val sessionToken: String, val kind: PendingKind)

    /** The pending verification-code sign-in, if any. Survives process death. */
    fun pendingLogin(): PendingLogin? {
        val stored = accountStore?.loadPendingLogin() ?: return null
        val kind = runCatching { PendingKind.valueOf(stored.third) }.getOrDefault(PendingKind.LEGACY)
        return PendingLogin(stored.first, stored.second, kind)
    }

    /** Drops the pending sign-in (the server-side session is left to expire). */
    fun abandonPendingLogin() {
        accountStore?.clearPendingLogin()
    }

    private fun setPending(email: String, sessionToken: String, kind: PendingKind) {
        accounts().savePendingLogin(email, sessionToken, kind.name)
    }

    private fun clearPending() {
        accountStore?.clearPendingLogin()
    }

    private fun requirePending(): PendingLogin =
        pendingLogin() ?: error("No pending sign-in. Start again so a new code can be sent.")

    /** Submits the emailed code for the pending session (FxA: POST /session/verify_code). */
    private suspend fun verifySessionWithCode(sessionToken: String, code: String) {
        val cleanCode = sanitizeCode(code)
        require(cleanCode.isNotEmpty()) { "Enter the confirmation code from the email." }
        fxaDo("POST", "/session/verify_code", sessionToken = sessionToken, jsonBody = JSONObject().put("code", cleanCode))
    }

    /** Asks FxA to email a fresh code for the pending session (POST /session/resend_code). */
    suspend fun resendPendingCode(): Result<Unit> = runCatching {
        val pending = requirePending()
        fxaDo("POST", "/session/resend_code", sessionToken = pending.sessionToken, jsonBody = JSONObject())
        Unit
    }

    companion object {
        fun normalizeEmail(email: String): String =
            email.replace(Regex("[\\p{Z}\\u200B-\\u200D\\uFEFF\\u00A0\\s]+"), "").trim().lowercase()

        fun sanitizeCode(code: String): String =
            code.replace(Regex("[\\p{Z}\\u200B-\\u200D\\uFEFF\\u00A0\\s]+"), "").trim()

        data class BatchParsedItem(
            val raw: String,
            val email: String,
            val password: String? = null,
            val accessToken: String? = null,
            val refreshToken: String? = null,
        )

        /**
         * Accepts a JSON array of {"email", "password"? , "accessToken"/"access_token"?,
         * "refreshToken"/"refresh_token"?} objects, one JSON object per line, or
         * line-based entries separated by "----", ":", "|", ",", ";" or whitespace
         * ("email:password", "email----password", ...). The password keeps the
         * rest of the line, so it may itself contain ":" or ",".
         */
        fun parseBatchInput(rawText: String): List<BatchParsedItem> {
            val items = mutableListOf<BatchParsedItem>()
            val trimmed = rawText.trim()
            if (trimmed.isEmpty()) return items

            fun fromJsonObject(obj: JSONObject, raw: String): BatchParsedItem? {
                val email = obj.optString("email").trim().ifBlank { return null }
                return BatchParsedItem(
                    raw = raw,
                    email = email,
                    password = obj.optString("password").ifBlank { null },
                    accessToken = obj.optString("accessToken").ifBlank { null }
                        ?: obj.optString("access_token").ifBlank { null },
                    refreshToken = obj.optString("refreshToken").ifBlank { null }
                        ?: obj.optString("refresh_token").ifBlank { null },
                )
            }

            if (trimmed.startsWith("[")) {
                val arr = runCatching { JSONArray(trimmed) }.getOrNull() ?: return items
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    fromJsonObject(obj, obj.toString())?.let { items += it }
                }
                return items
            }

            lineLoop@ for (line in trimmed.lines()) {
                val entry = line.trim()
                if (entry.isEmpty()) continue
                if (entry.startsWith("{")) {
                    val obj = runCatching { JSONObject(entry) }.getOrNull() ?: continue
                    fromJsonObject(obj, entry)?.let { items += it }
                    continue
                }
                // "----", "," and ";" are tried before the generic split below:
                // the generic character class does not contain them, so a line
                // like "email----password" used to be read as one long email
                // with no password. The candidate address must be a bare email
                // (no other separator inside it), so "email,password:with:colon"
                // keeps "password:with:colon" whole and "email:pass,word" still
                // splits on the ":" as before. (Fix round 1, carried verbatim.)
                for (sep in listOf("----", ",", ";")) {
                    val idx = entry.indexOf(sep)
                    if (idx <= 0) continue
                    val emailPart = entry.substring(0, idx).trim()
                    val passwordPart = entry.substring(idx + sep.length).trim()
                    val bareEmail = emailPart.contains("@") &&
                        emailPart.none { it == ':' || it == '|' || it == ',' || it == ';' || it.isWhitespace() }
                    if (bareEmail && passwordPart.isNotEmpty()) {
                        items += BatchParsedItem(raw = entry, email = emailPart, password = passwordPart)
                        continue@lineLoop
                    }
                }
                val parts = entry.split(Regex("[:|\\t ]+"), limit = 2).map { it.trim() }
                if (parts.size == 2 && parts[0].contains("@")) {
                    items += BatchParsedItem(raw = entry, email = parts[0], password = parts[1].ifBlank { null })
                } else if (parts.size == 1 && entry.contains("@")) {
                    items += BatchParsedItem(raw = entry, email = entry)
                }
            }
            return items
        }
    }

    // ------------------------------------------------------------------
    // Legacy email/password sign-in (secondary path; the browser
    // sign-in above remains primary, exactly as in the v1 fork's
    // post-review layout).
    // ------------------------------------------------------------------

    /** Starts the legacy sign-in. Result true = an emailed code is now required. */
    suspend fun startLogin(email: String, password: String): Result<Boolean> = runCatching {
        val cleanEmail = normalizeEmail(email)
        val data = loginAttempt(cleanEmail, password)
        val sessionToken = data.getString("sessionToken")
        val verified = data.optBoolean("verified", false)
        if (!verified) {
            setPending(cleanEmail, sessionToken, PendingKind.LEGACY)
            true
        } else {
            completeLegacyLogin(cleanEmail, sessionToken)
            false
        }
    }

    /**
     * Completes whichever sign-in is pending (legacy single session or an
     * account add/import) by submitting the emailed verification code.
     */
    suspend fun submitTwoFactorCode(code: String): Result<Boolean> = runCatching {
        val pending = requirePending()
        verifySessionWithCode(pending.sessionToken, code)
        when (pending.kind) {
            PendingKind.LEGACY -> completeLegacyLogin(pending.email, pending.sessionToken)
            PendingKind.ACCOUNT -> completePendingAccount(pending)
        }
        false
    }

    /**
     * Installs a verified legacy session as THE session (through
     * upstream's epoch-guarded completeLogin) and registers it as the
     * active account in the fork's account list.
     */
    private suspend fun completeLegacyLogin(email: String, sessionToken: String) {
        completeLogin(sessionToken, tokenStore.sessionEpoch)
        clearPending()
        val auth = tokenStore.loadAuth() ?: return
        if (email.isBlank()) return
        registerAccount(email, auth, markActive = true)
    }

    /** Upserts an account entry by email (id preserved) and optionally marks it active. */
    private fun registerAccount(email: String, auth: RuntimeAuth, markActive: Boolean): VpnAccount {
        val store = accounts()
        val existing = store.getAccounts().firstOrNull { it.email.equals(email, ignoreCase = true) }
        val entry = existing?.copy(auth = auth, lastError = null)
            ?: VpnAccount(email = email, auth = auth)
        store.addAccount(entry)
        if (markActive) store.setActiveAccountId(entry.id)
        return entry
    }

    private suspend fun loginAttempt(email: String, password: String, withVerificationMethod: Boolean = true): JSONObject {
        val body = JSONObject().apply {
            put("email", email)
            put("authPW", deriveAuthPw(email, password))
            if (withVerificationMethod) put("verificationMethod", VERIFICATION_METHOD_EMAIL_2FA)
        }
        return try {
            fxaDo("POST", "/account/login", jsonBody = body)
        } catch (e: FxaApiError) {
            if (e.errno == FXA_ERRNO_INVALID_PARAMETER && withVerificationMethod) {
                loginAttempt(email, password, withVerificationMethod = false)
            } else {
                throw e
            }
        }
    }

    private suspend fun fxaDo(
        method: String,
        path: String,
        sessionToken: String? = null,
        jsonBody: JSONObject,
    ): JSONObject = executeFxaRequest(
        baseUrl = FXA_AUTH_SERVER,
        path = path,
        method = method,
        jsonBody = jsonBody,
        sessionToken = sessionToken,
    )

    private fun deriveQuickStretch(email: String, password: String): ByteArray {
        val salt = "${PROTOCOL_VERSION}quickStretch:$email".toByteArray(Charsets.UTF_8)
        return pbkdf2HmacSha256(password.toByteArray(Charsets.UTF_8), salt, PBKDF2_ROUNDS, STRETCHED_PW_LEN)
    }

    private fun deriveAuthPw(email: String, password: String): String {
        val quickStretched = deriveQuickStretch(email, password)
        return hkdf(quickStretched, "${PROTOCOL_VERSION}authPW", HKDF_LEN).toHex()
    }

    // ------------------------------------------------------------------
    // Per-account token refresh: the same grant as upstream's session
    // refresh, but storage-free — the caller persists the result into
    // the account list. Used for non-active accounts and token imports.
    // ------------------------------------------------------------------

    suspend fun ensureFreshAccessTokenForAccount(account: VpnAccount, force: Boolean = false): RuntimeAuth {
        val current = account.auth
        if (!force) {
            val nowSeconds = System.currentTimeMillis() / 1000
            if (current.expiresAtEpochSeconds - nowSeconds > 60L) return current
        }
        return refreshAuth(current)
    }

    /** Shared refresh-grant implementation; pure w.r.t. storage. */
    private suspend fun refreshAuth(current: RuntimeAuth): RuntimeAuth {
        val refreshToken = current.refreshToken
            ?: throw FxaRefreshFailure(
                "The stored session has no refresh token; sign in again.",
                permanent = true,
            )

        val body = JSONObject().apply {
            put("client_id", FIREFOX_CLIENT_ID)
            put("grant_type", "refresh_token")
            put("refresh_token", refreshToken)
            put("scope", OAUTH_SCOPE)
        }

        val tokenData = try {
            executeFxaRequest(
                baseUrl = FXA_OAUTH_SERVER,
                path = "/oauth/token",
                jsonBody = body,
            )
        } catch (rejected: FxaApiError) {
            throw FxaRefreshFailure(
                rejected.message ?: "FxA rejected the refresh token",
                permanent = rejected.isPermanentRefreshRejection(),
                cause = rejected,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (transport: Exception) {
            throw FxaRefreshFailure(
                transport.message ?: "Could not reach the Firefox Accounts server",
                permanent = false,
                cause = transport,
            )
        }

        val accessToken = tokenData.optString("access_token").takeIf { it.isNotBlank() }
            ?: throw FxaRefreshFailure(
                "FxA returned no access token for the refresh grant",
                permanent = false,
            )

        return RuntimeAuth(
            accessToken = accessToken,
            refreshToken = tokenData.optString("refresh_token", "").ifBlank { null } ?: refreshToken,
            expiresAtEpochSeconds = expiryFrom(tokenData),
        )
    }

    // ------------------------------------------------------------------
    // Batch import. Every login goes through the FxA path above
    // (loginAttempt/oauthToken/refreshAuth); no new auth logic.
    // ------------------------------------------------------------------

    /** Outcome of starting an account sign-in (add-account / batch item). */
    sealed interface AccountLoginStep {
        data class Completed(val account: VpnAccount) : AccountLoginStep
        data class NeedsCode(val email: String) : AccountLoginStep
    }

    /**
     * Starts a sign-in whose result is a stored [VpnAccount]. When FxA
     * returns the session unverified (the normal case for a new device now),
     * the session is kept as the pending login and [AccountLoginStep.NeedsCode]
     * is returned; finish it with [submitPendingAccountCode].
     */
    suspend fun beginAccountLogin(email: String, password: String): Result<AccountLoginStep> = runCatching {
        val cleanEmail = normalizeEmail(email)
        val data = loginAttempt(cleanEmail, password)
        val sessionToken = data.getString("sessionToken")
        val verified = data.optBoolean("verified", false)
        if (!verified) {
            setPending(cleanEmail, sessionToken, PendingKind.ACCOUNT)
            AccountLoginStep.NeedsCode(cleanEmail)
        } else {
            finishAccountLogin(cleanEmail, sessionToken)
        }
    }

    /** Verifies the pending account sign-in with the emailed code and stores the account. */
    suspend fun submitPendingAccountCode(code: String): Result<VpnAccount> = runCatching {
        val pending = requirePending()
        check(pending.kind == PendingKind.ACCOUNT) { "The pending sign-in is not an account sign-in." }
        verifySessionWithCode(pending.sessionToken, code)
        completePendingAccount(pending)
    }

    private suspend fun completePendingAccount(pending: PendingLogin): VpnAccount {
        val completed = finishAccountLogin(pending.email, pending.sessionToken)
        clearPending()
        return completed.account
    }

    /** Mints the OAuth tokens for a verified session and stores the account (not the session). */
    private suspend fun finishAccountLogin(email: String, sessionToken: String): AccountLoginStep.Completed {
        val tokenData = oauthToken(sessionToken)
        val account = registerAccount(
            email = email,
            auth = RuntimeAuth(
                accessToken = tokenData.getString("access_token"),
                refreshToken = tokenData.optString("refresh_token", "").ifBlank { null },
                expiresAtEpochSeconds = expiryFrom(tokenData),
            ),
            markActive = false,
        )
        return AccountLoginStep.Completed(account)
    }

    /** One step of the guided batch import for a single parsed entry. */
    sealed interface GuidedImportStep {
        data class Done(val result: BatchImportItemResult) : GuidedImportStep
        data class NeedsCode(val item: BatchParsedItem) : GuidedImportStep
    }

    /**
     * Imports one batch entry. Password entries that FxA leaves unverified
     * come back as [GuidedImportStep.NeedsCode] with the session kept pending;
     * the caller collects the code (see [submitPendingAccountCode]) and then
     * moves on to the next entry. Token entries never need a code.
     */
    suspend fun importBatchItem(item: BatchParsedItem): GuidedImportStep {
        if (!item.password.isNullOrBlank()) {
            return when (val step = beginAccountLogin(item.email, item.password).getOrElse { failure ->
                if (failure is CancellationException) throw failure
                return GuidedImportStep.Done(
                    BatchImportItemResult(item.email, success = false, error = failure.message ?: "Import failed"),
                )
            }) {
                is AccountLoginStep.Completed ->
                    GuidedImportStep.Done(BatchImportItemResult(item.email, success = true, account = step.account))
                is AccountLoginStep.NeedsCode -> GuidedImportStep.NeedsCode(item)
            }
        }
        val result = runCatching {
            val auth = when {
                !item.accessToken.isNullOrBlank() -> {
                    val provisional = RuntimeAuth(
                        accessToken = item.accessToken,
                        refreshToken = item.refreshToken,
                        expiresAtEpochSeconds = 0L,
                    )
                    ensureFreshAccessTokenForAccount(
                        VpnAccount(email = item.email, auth = provisional),
                        force = true,
                    )
                }
                else -> error("No password or access token provided for this entry.")
            }
            val account = registerAccount(email = normalizeEmail(item.email), auth = auth, markActive = false)
            BatchImportItemResult(identifier = item.email, success = true, account = account)
        }.getOrElse { failure ->
            if (failure is CancellationException) throw failure
            BatchImportItemResult(
                identifier = item.email,
                success = false,
                error = failure.message ?: "Import failed",
            )
        }
        return GuidedImportStep.Done(result)
    }

    /**
     * Non-interactive batch import: every entry is attempted in order and
     * entries that need an email verification code are reported as failures
     * (the pending session is dropped). The interactive flows use
     * [importBatchItem] instead so the user can enter each code.
     */
    suspend fun batchImportAccounts(
        rawText: String,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> },
    ): List<BatchImportItemResult> {
        val items = parseBatchInput(rawText)
        val results = mutableListOf<BatchImportItemResult>()
        items.forEachIndexed { index, item ->
            onProgress(index + 1, items.size, item.email)
            val result = when (val step = importBatchItem(item)) {
                is GuidedImportStep.Done -> step.result
                is GuidedImportStep.NeedsCode -> {
                    abandonPendingLogin()
                    BatchImportItemResult(
                        identifier = item.email,
                        success = false,
                        error = "This account needs an email verification code; use the guided import to enter it.",
                    )
                }
            }
            results += result
        }
        return results
    }

    /**
     * Logs one account in and stores it. Strict variant of
     * [beginAccountLogin]: fails when FxA asks for an email verification
     * code (interactive callers use begin/submit so the code can be typed).
     */
    suspend fun loginSingleAccount(email: String, password: String): Result<VpnAccount> =
        beginAccountLogin(email, password).map { step ->
            when (step) {
                is AccountLoginStep.Completed -> step.account
                is AccountLoginStep.NeedsCode -> {
                    abandonPendingLogin()
                    error("This account needs an email verification code to finish signing in.")
                }
            }
        }

    fun buildOAuthHawkHeader(sessionToken: String): Pair<String, String> {
        val clean = sessionToken.trim().replace(Regex("[\\s\"]"), "").lowercase()
        require(Regex("^[0-9a-f]{64}$").matches(clean)) {
            "The sign-in page did not hand over a usable session token"
        }
        val url = "$FXA_AUTH_SERVER/oauth/token".toHttpUrl()
        val body =
            JSONObject().apply {
                put("client_id", FIREFOX_CLIENT_ID)
                put("grant_type", "fxa-credentials")
                put("scope", OAUTH_SCOPE)
                put("access_type", "offline")
            }
        val bodyStr = body.toString()
        val (tokenId, hmacKey) = deriveHawkCredentials(clean)
        val header = hawkHeader("POST", url, tokenId, hmacKey, bodyStr.toByteArray(Charsets.UTF_8))
        return header to bodyStr
    }

    suspend fun completeBrowserLoginWithTokens(
        accessToken: String,
        refreshToken: String?,
        expiresInSeconds: Long,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            val epoch = tokenStore.sessionEpoch
            ControlPlaneHttp.cookieJar.syncFromWebView()
            runCatching {
                    loginMutex.withLock {
                        check(tokenStore.sessionEpoch == epoch) { "Sign-in was superseded" }
                        val ttl =
                            if (expiresInSeconds > 0) expiresInSeconds
                            else DEFAULT_ACCESS_TOKEN_TTL_SECONDS.toLong()
                        tokenStore.completeLoginIfCurrent(
                            RuntimeAuth(
                                accessToken = accessToken.trim(),
                                refreshToken = refreshToken?.trim()?.ifBlank { null },
                                expiresAtEpochSeconds = System.currentTimeMillis() / 1000 + ttl,
                            ),
                            epoch,
                        )
                        Unit
                    }
                }
                .onFailure { if (it is CancellationException) throw it }
        }

    suspend fun completeBrowserLogin(sessionToken: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val epoch = tokenStore.sessionEpoch
            ControlPlaneHttp.cookieJar.syncFromWebView()
            runCatching {
                    loginMutex.withLock {
                        val clean = sessionToken.trim().replace(Regex("[\\s\"]"), "").lowercase()
                        require(Regex("^[0-9a-f]{64}$").matches(clean)) {
                            "The sign-in page did not hand over a usable session token"
                        }
                        check(tokenStore.sessionEpoch == epoch) { "Sign-in was superseded" }
                        FirefoxAccountHeaders.refresh(client)
                        completeLogin(clean, epoch)
                    }
                }
                .onFailure { if (it is CancellationException) throw it }
        }

    suspend fun restoreSession(): SessionStatus =
        withContext(Dispatchers.IO) { restoreSessionInternal() }

    private suspend fun restoreSessionInternal(): SessionStatus {
        val epoch = tokenStore.sessionEpoch
        val stored =
            runCatching { tokenStore.loadAuth() }.getOrNull() ?: return SessionStatus.NEEDS_LOGIN
        if (stored.refreshToken == null && !tokenStore.hasValidAccessToken()) {
            return if (tokenStore.clearIfCurrent(epoch)) SessionStatus.NEEDS_LOGIN
            else if (tokenStore.hasValidAccessToken()) SessionStatus.ACTIVE
            else SessionStatus.UNREACHABLE
        }
        if (tokenStore.hasValidAccessToken()) return SessionStatus.ACTIVE

        return try {
            ensureFreshAccessToken()
            SessionStatus.ACTIVE
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: FxaRefreshFailure) {
            if (failure.permanent) {
                AppLogger.w(TAG, "FxA rejected the stored session; signing out", failure)
                if (tokenStore.clearIfCurrent(epoch)) SessionStatus.NEEDS_LOGIN
                else if (tokenStore.hasValidAccessToken()) SessionStatus.ACTIVE
                else SessionStatus.UNREACHABLE
            } else {
                AppLogger.w(
                    TAG,
                    "could not renew the session right now; staying signed in",
                    failure,
                )
                SessionStatus.UNREACHABLE
            }
        } catch (unexpected: Exception) {
            AppLogger.w(TAG, "unexpected error while restoring the session", unexpected)
            SessionStatus.UNREACHABLE
        }
    }

    suspend fun ensureFreshAccessToken(force: Boolean = false): RuntimeAuth =
        withContext(Dispatchers.IO) { ensureFreshAccessTokenInternal(force) }

    private suspend fun ensureFreshAccessTokenInternal(force: Boolean): RuntimeAuth =
        refreshMutex.withLock {
            val epoch = tokenStore.sessionEpoch
            val current =
                tokenStore.loadAuth()
                    ?: run {
                        tokenStore.storageError.value?.let {
                            throw FxaRefreshFailure(it, permanent = false)
                        }
                        throw FxaRefreshFailure("Not signed in", permanent = true)
                    }
            if (!force && tokenStore.hasValidAccessToken()) return@withLock current

            val refreshToken =
                current.refreshToken
                    ?: throw FxaRefreshFailure(
                        "The stored session has no refresh token; sign in again.",
                        permanent = true,
                    )

            val body =
                JSONObject().apply {
                    put("client_id", FIREFOX_CLIENT_ID)
                    put("grant_type", "refresh_token")
                    put("refresh_token", refreshToken)
                    put("scope", OAUTH_SCOPE)
                }

            val tokenData =
                try {
                    FirefoxAccountHeaders.refresh(client)
                    executeFxaRequest(FXA_OAUTH_SERVER, "/oauth/token", jsonBody = body)
                } catch (rejected: FxaApiError) {
                    throw FxaRefreshFailure(
                        rejected.message ?: "FxA rejected the refresh token",
                        permanent = rejected.isPermanentRefreshRejection(),
                        cause = rejected,
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (transport: Exception) {
                    throw FxaRefreshFailure(
                        transport.message ?: "Could not reach the Firefox Accounts server",
                        permanent = false,
                        cause = transport,
                    )
                }

            val accessToken =
                tokenData.optString("access_token").takeIf { it.isNotBlank() }
                    ?: throw FxaRefreshFailure(
                        "FxA returned no access token for the refresh grant",
                        permanent = false,
                    )

            val renewed =
                RuntimeAuth(
                    accessToken = accessToken,
                    refreshToken =
                        tokenData.optString("refresh_token", "").ifBlank { null } ?: refreshToken,
                    expiresAtEpochSeconds = expiryFrom(tokenData),
                )
            tokenStore.saveAuthIfCurrent(renewed, epoch)
            renewed
        }

    suspend fun currentAccessToken(): String? =
        withContext(Dispatchers.IO) {
            try {
                ensureFreshAccessToken().accessToken
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: FxaRefreshFailure) {
                if (!failure.permanent && tokenStore.hasValidAccessToken())
                    tokenStore.loadAuth()?.accessToken
                else null
            }
        }

    suspend fun refreshAccessToken(): RuntimeAuth? =
        try {
            ensureFreshAccessToken(force = true)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: FxaRefreshFailure) {
            AppLogger.w(TAG, "access token renewal failed", failure)
            null
        }

    private fun FxaApiError.isPermanentRefreshRejection(): Boolean {
        return statusCode == 400 || statusCode == 401 || statusCode == 403
    }

    private fun expiryFrom(tokenData: JSONObject): Long {
        val expiresIn =
            tokenData.optInt("expires_in", 0).takeIf { it > 0 } ?: DEFAULT_ACCESS_TOKEN_TTL_SECONDS
        return System.currentTimeMillis() / 1000 + expiresIn
    }

    private suspend fun completeLogin(sessionToken: String, epoch: Long) {
        val tokenData = oauthToken(sessionToken)
        coroutineContext.ensureActive()
        tokenStore.completeLoginIfCurrent(
            RuntimeAuth(
                accessToken =
                    tokenData.optString("access_token").takeIf { it.isNotBlank() }
                        ?: error("Firefox Accounts returned no access token"),
                refreshToken = tokenData.optString("refresh_token", "").ifBlank { null },
                expiresAtEpochSeconds = expiryFrom(tokenData),
            ),
            epoch,
        )
    }

    private suspend fun oauthToken(sessionToken: String): JSONObject {
        val body =
            JSONObject().apply {
                put("client_id", FIREFOX_CLIENT_ID)
                put("grant_type", "fxa-credentials")
                put("scope", OAUTH_SCOPE)
                put("access_type", "offline")
            }
        return executeFxaRequest(
            baseUrl = FXA_AUTH_SERVER,
            path = "/oauth/token",
            jsonBody = body,
            sessionToken = sessionToken,
        )
    }

    private suspend fun executeFxaRequest(
        baseUrl: String,
        path: String,
        method: String = "POST",
        jsonBody: JSONObject,
        sessionToken: String? = null,
    ): JSONObject =
        withTimeout(120_000L) {
            withContext(Dispatchers.IO) {
                val url = "$baseUrl$path".toHttpUrl()
                val origin = "${url.scheme}://${url.host}"
                val bodyBytes = jsonBody.toString().toByteArray(Charsets.UTF_8)
                val hawkCreds = sessionToken?.let { deriveHawkCredentials(it) }

                fun buildRequest(): Request {
                    val builder =
                        Request.Builder()
                            .url(url)
                            .applyFirefoxHeaders()
                            .header("Content-Type", "application/json")
                    if (hawkCreds != null) {
                        builder.header(
                            "Authorization",
                            hawkHeader(method, url, hawkCreds.first, hawkCreds.second, bodyBytes),
                        )
                    }
                    builder.method(method, bodyBytes.toRequestBody(jsonMediaType))
                    return builder.build()
                }

                var response: Response = client.newCall(buildRequest()).awaitResponse()
                var attempts = 1
                while (response.code == 406 && attempts < FXA_MAX_CHALLENGE_ATTEMPTS) {
                    val challengedBody = response.use { it.body?.readBoundedText().orEmpty() }
                    try {
                        ControlPlaneHttp.cookieJar.syncFromWebView()
                        challengeSolver.solveAndInstall(
                            challengedBody,
                            origin,
                            FirefoxAccountHeaders.userAgent,
                        )
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (solveFailure: Exception) {
                        AppLogger.w(TAG, "could not solve the Fastly challenge", solveFailure)
                        throw FastlyBlockedException(FRIENDLY_406_MESSAGE, solveFailure)
                    }
                    response = client.newCall(buildRequest()).awaitResponse()
                    attempts++
                }

                response.use { resp ->
                    val text = resp.body?.readBoundedText().orEmpty()
                    if (resp.code >= 400) {
                        if (resp.code == 406) {
                            throw FastlyBlockedException(FRIENDLY_406_MESSAGE)
                        }
                        val errorBody =
                            try {
                                JSONObject(text)
                            } catch (e: Exception) {
                                JSONObject()
                            }
                        val errno = if (errorBody.has("errno")) errorBody.optInt("errno") else null
                        val message =
                            when {
                                errorBody.length() > 0 ->
                                    errorBody.optString("message", "HTTP ${resp.code}")
                                text.isEmpty() -> "HTTP ${resp.code}"
                                else -> text.take(200)
                            }
                        throw FxaApiError(message, errno, resp.code)
                    }
                    if (text.isEmpty()) JSONObject() else JSONObject(text)
                }
            }
        }

    private fun deriveHawkCredentials(sessionTokenHex: String): Pair<String, ByteArray> {
        val sessionToken = sessionTokenHex.hexToBytes()
        val expanded = hkdf(sessionToken, "${PROTOCOL_VERSION}sessionToken", 64)
        return expanded.copyOfRange(0, 32).toHex() to expanded.copyOfRange(32, 64)
    }

    private fun hawkHeader(
        method: String,
        url: HttpUrl,
        tokenId: String,
        hmacKey: ByteArray,
        body: ByteArray,
    ): String {
        val ts = (System.currentTimeMillis() / 1000).toString()
        val nonceBytes = ByteArray(6).also { SecureRandom().nextBytes(it) }
        val nonce =
            Base64.encodeToString(
                nonceBytes,
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
            )
        val path = url.encodedPath + if (url.encodedQuery != null) "?${url.encodedQuery}" else ""

        var payloadHash = ""
        if (body.isNotEmpty()) {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update("hawk.1.payload\napplication/json\n".toByteArray(Charsets.UTF_8))
            digest.update(body)
            digest.update("\n".toByteArray(Charsets.UTF_8))
            payloadHash = Base64.encodeToString(digest.digest(), Base64.NO_WRAP)
        }

        val normalized =
            "hawk.1.header\n$ts\n$nonce\n${method.uppercase()}\n$path\n${url.host}\n${url.port}\n$payloadHash\n\n"

        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(hmacKey, "HmacSHA256")) }
        val macB64 =
            Base64.encodeToString(
                mac.doFinal(normalized.toByteArray(Charsets.UTF_8)),
                Base64.NO_WRAP,
            )

        var header = "Hawk id=\"$tokenId\", ts=\"$ts\", nonce=\"$nonce\", mac=\"$macB64\""
        if (payloadHash.isNotEmpty()) header += ", hash=\"$payloadHash\""
        return header
    }
}

private const val FRIENDLY_406_MESSAGE =
    "Mozilla's bot protection rejected this request (HTTP 406) and the automatic " +
        "challenge could not be completed on this network. Please switch networks " +
        "(Wi-Fi <-> mobile data) and try again."

private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key, "HmacSHA256"))
    return mac.doFinal(data)
}

private fun hkdf(
    ikm: ByteArray,
    info: String,
    length: Int,
    salt: ByteArray = ByteArray(32),
): ByteArray {
    val prk = hmacSha256(salt, ikm)
    val infoBytes = info.toByteArray(Charsets.UTF_8)
    val result = ByteArray(length)
    var previousBlock = ByteArray(0)
    var generated = 0
    var counter = 1
    while (generated < length) {
        val block = hmacSha256(prk, previousBlock + infoBytes + byteArrayOf(counter.toByte()))
        val toCopy = minOf(block.size, length - generated)
        System.arraycopy(block, 0, result, generated, toCopy)
        generated += toCopy
        previousBlock = block
        counter++
    }
    return result
}

private fun pbkdf2HmacSha256(password: ByteArray, salt: ByteArray, iterations: Int, keyLengthBytes: Int): ByteArray {
    val hLen = 32
    val numBlocks = (keyLengthBytes + hLen - 1) / hLen
    val result = ByteArray(numBlocks * hLen)
    val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(password, "HmacSHA256")) }
    for (blockIndex in 1..numBlocks) {
        val blockIndexBytes = ByteBuffer.allocate(4).putInt(blockIndex).array()
        var u = mac.doFinal(salt + blockIndexBytes)
        val t = u.copyOf()
        for (iter in 2..iterations) {
            u = mac.doFinal(u)
            for (i in t.indices) t[i] = (t[i].toInt() xor u[i].toInt()).toByte()
        }
        System.arraycopy(t, 0, result, (blockIndex - 1) * hLen, hLen)
    }
    return result.copyOf(keyLengthBytes)
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
