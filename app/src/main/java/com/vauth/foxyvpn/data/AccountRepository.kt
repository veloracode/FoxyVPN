package com.vauth.foxyvpn.data

import com.vauth.foxyvpn.data.model.RuntimeAuth
import com.vauth.foxyvpn.data.model.VpnAccount

private const val TAG = "AccountRepository"

/** Email of the placeholder entry that wraps a pre-multi-account session. */
private const val PLACEHOLDER_EMAIL = "Primary Account"

/**
 * Moves credentials between the fork's [AccountStore] (all accounts)
 * and upstream's [TokenStore] (the one active session).
 *
 * Invariants this class maintains:
 * - The ACTIVE account's live credentials are TokenStore's; whenever
 *   they are about to be replaced (switch, sign-out), the current
 *   TokenStore session is snapshotted back into the active entry, so
 *   token refreshes upstream performs are never lost.
 * - A switch installs the target's stored credentials through
 *   TokenStore.completeLoginIfCurrent — upstream's own epoch-guarded
 *   login-completion path — so every upstream reader (Guardian, the VPN
 *   service, the session watchdog) sees an ordinary session change.
 * - Reconciliation NEVER deletes an entry or overwrites it on
 *   ambiguous evidence (review-hardened 2026-11-02): a session/entry
 *   mismatch counts as a refresh of the same account only when the
 *   lineage is plausible (access-token or refresh-token equality) and
 *   is PROVEN foreign only when both refresh tokens exist and differ;
 *   a missing session merely marks the active entry needs-sign-in,
 *   and a storage READ FAILURE (TokenStore.storageError) changes
 *   nothing at all. Entries leave the list only via explicit removal
 *   or the global sign-out.
 * - Adding an account is DETACHED: the new session is written straight
 *   into the account list and the TokenStore — and with it the active
 *   account — is never touched, so there is no swap to restore and no
 *   kill-window that can mix accounts up.
 */
class AccountRepository(
    private val accountStore: AccountStore,
    private val tokenStore: TokenStore,
) {

    /** Same session lineage: the very same token, or the same refresh token. */
    private fun sameLineage(a: RuntimeAuth, b: RuntimeAuth): Boolean =
        a.accessToken == b.accessToken ||
            (a.refreshToken != null && a.refreshToken == b.refreshToken)

    /** Proven to be DIFFERENT accounts' sessions (both refresh tokens known). */
    private fun provenDifferent(a: RuntimeAuth, b: RuntimeAuth): Boolean =
        a.accessToken != b.accessToken &&
            a.refreshToken != null &&
            b.refreshToken != null &&
            a.refreshToken != b.refreshToken

    /**
     * Startup reconciliation: legacy hygiene first, then align the
     * account list with whatever session upstream's store holds.
     */
    fun migrateAndReconcile() {
        runCatching { accountStore.wipeLegacyPendingLogin() }
            .onFailure { AppLogger.w(TAG, "legacy pending-login wipe failed", it) }
        runCatching { accountStore.migrateLegacyAccountsIfNeeded() }
            .onFailure { AppLogger.w(TAG, "legacy account migration failed", it) }
        // Startup reconciliation must never take the app down: a storage
        // failure here degrades to "accounts list not aligned", which the
        // capture hook and the next start repair.
        runCatching { reconcile() }
            .onFailure { AppLogger.w(TAG, "account reconciliation failed", it) }
    }

    /** Aligns the account list with the current TokenStore session. */
    fun reconcile() {
        val session = tokenStore.loadAuth()
        val activeId = accountStore.activeAccountIdFlow.value
        val active = activeId?.let { accountStore.getAccount(it) }
        when {
            session == null -> {
                if (active == null) return
                if (tokenStore.storageError.value != null) {
                    // A transient read failure is indistinguishable
                    // from sign-out EXCEPT for this flag — never act
                    // on it.
                    AppLogger.w(
                        TAG,
                        "session unreadable at reconcile; leaving the account list untouched",
                    )
                    return
                }
                // The session is genuinely gone (a deliberate clear, or
                // upstream invalidated it after a failed restore). The
                // entry stays: it is the only copy of those credentials,
                // and browser re-auth refreshes this same entry via the
                // uid/email match in captureActiveSession.
                if (active.lastError == null) {
                    accountStore.updateAccount(
                        active.copy(
                            lastError = "Session ended — sign in again to use this account."
                        )
                    )
                }
            }
            active != null -> {
                when {
                    active.auth == session -> Unit
                    provenDifferent(active.auth, session) -> {
                        // A FOREIGN session must never overwrite the
                        // active entry. If it belongs to a stored entry,
                        // that entry becomes the active one; otherwise
                        // the sign-in capture hook registers it.
                        val match = accountStore.getAccounts()
                            .firstOrNull { it.id != active.id && sameLineage(it.auth, session) }
                        if (match != null) {
                            accountStore.updateAccount(
                                match.copy(auth = session, lastError = null)
                            )
                            accountStore.setActiveAccountId(match.id)
                        }
                    }
                    else -> {
                        // Same lineage (or not provable either way):
                        // a token refresh of the active account.
                        accountStore.updateAccount(active.copy(auth = session, lastError = null))
                    }
                }
            }
            else -> {
                val match = accountStore.getAccounts()
                    .firstOrNull { sameLineage(it.auth, session) }
                if (match != null) {
                    accountStore.updateAccount(match.copy(auth = session, lastError = null))
                    accountStore.setActiveAccountId(match.id)
                } else if (!accountStore.hasAnyAccount()) {
                    // Pre-multi-account install: wrap the lone session
                    // as the primary account (the v1 migration's shape).
                    // Its real identity is backfilled on the first
                    // successful profile call (backfillActiveIdentity).
                    val primary = VpnAccount(email = PLACEHOLDER_EMAIL, auth = session)
                    accountStore.addAccount(primary)
                    accountStore.setActiveAccountId(primary.id)
                }
                // Otherwise a fresh sign-in is in flight; the capture
                // hook registers it with its real identity.
            }
        }
    }

    /**
     * Registers the just-completed browser sign-in (its session is
     * already in the TokenStore) as the active account. Dedupe is by
     * FxA uid first (stable; captured from the profile endpoint), then
     * by email, case-insensitive; a wrapped placeholder entry for this
     * very session is resolved in place. Capturing an account that is
     * already in the list refreshes that entry instead of duplicating
     * it.
     */
    fun captureActiveSession(email: String, uid: String? = null): VpnAccount? {
        val session = tokenStore.loadAuth() ?: return null
        val accounts = accountStore.getAccounts()
        val existing =
            (if (uid != null) accounts.firstOrNull { it.uid == uid } else null)
                ?: accounts.firstOrNull { it.email.equals(email, ignoreCase = true) }
                ?: accountStore.activeAccount()?.takeIf { it.email == PLACEHOLDER_EMAIL }
        val entry = existing?.copy(
            email = email,
            auth = session,
            uid = uid ?: existing.uid,
            lastError = null,
        ) ?: VpnAccount(email = email, auth = session, uid = uid)
        accountStore.addAccount(entry)
        accountStore.setActiveAccountId(entry.id)
        return entry
    }

    /** Result of a detached add: the stored entry + whether it already existed. */
    data class DetachedAddResult(val account: VpnAccount, val wasExisting: Boolean)

    /**
     * Stores a session obtained by the add-account browser flow
     * DIRECTLY in the account list. The TokenStore is never touched:
     * the active account keeps its session, its tunnel and its active
     * marking throughout the add. Dedupe as in [captureActiveSession]
     * (uid, then email): re-adding an existing account refreshes its
     * stored session.
     */
    fun addDetachedAccount(auth: RuntimeAuth, email: String, uid: String?): DetachedAddResult {
        val accounts = accountStore.getAccounts()
        val existing =
            (if (uid != null) accounts.firstOrNull { it.uid == uid } else null)
                ?: accounts.firstOrNull { it.email.equals(email, ignoreCase = true) }
        val entry = existing?.copy(
            email = email,
            auth = auth,
            uid = uid ?: existing.uid,
            lastError = null,
        ) ?: VpnAccount(email = email, auth = auth, uid = uid)
        accountStore.addAccount(entry)
        return DetachedAddResult(entry, existing != null)
    }

    /**
     * Fills in the active entry's real identity (email + FxA uid) once
     * a profile call succeeds — the wrapped placeholder entry otherwise
     * never matches a real identity and re-adding that account would
     * duplicate it. If ANOTHER entry already owns the identity, the
     * placeholder was a duplicate of it: the live session is folded
     * into that entry, it becomes active, and the placeholder goes.
     */
    fun backfillActiveIdentity(email: String?, uid: String?) {
        if (email == null && uid == null) return
        val active = accountStore.activeAccount() ?: return
        if (active.uid != null && active.email != PLACEHOLDER_EMAIL) return
        val owner = accountStore.getAccounts().firstOrNull { other ->
            other.id != active.id &&
                ((uid != null && other.uid == uid) ||
                    (email != null && other.email.equals(email, ignoreCase = true)))
        }
        if (owner != null) {
            val session = tokenStore.loadAuth()
            accountStore.updateAccount(
                owner.copy(
                    email = email ?: owner.email,
                    auth = session ?: owner.auth,
                    uid = uid ?: owner.uid,
                    lastError = null,
                )
            )
            accountStore.setActiveAccountId(owner.id)
            accountStore.removeAccount(active.id)
        } else {
            accountStore.updateAccount(
                active.copy(
                    email = email ?: active.email,
                    uid = uid ?: active.uid,
                    lastError = if (active.email == PLACEHOLDER_EMAIL) null else active.lastError,
                )
            )
        }
    }

    /** Writes the TokenStore session back into the active entry, if any. */
    fun snapshotActiveSession() {
        val session = tokenStore.loadAuth() ?: return
        val active = accountStore.activeAccount() ?: return
        if (active.auth == session) return
        if (provenDifferent(active.auth, session)) {
            // The live session belongs to another account; writing it
            // into this entry would mix the two accounts up.
            AppLogger.w(TAG, "snapshot skipped: the live session is a different account's")
            return
        }
        accountStore.updateAccount(active.copy(auth = session, lastError = null))
    }

    /**
     * Makes [accountId] the active account: the outgoing session is
     * snapshotted first, then the target's stored credentials are
     * installed as the TokenStore session. The caller disconnects the
     * VPN first (the service reads the session at connect time).
     * Returns false when the target is missing or the install was
     * superseded by a concurrent session change.
     */
    fun switchTo(accountId: String): Boolean {
        val target = accountStore.getAccount(accountId) ?: return false
        if (accountStore.activeAccountIdFlow.value == accountId) {
            snapshotActiveSession()
            return true
        }
        snapshotActiveSession()
        return try {
            tokenStore.completeLoginIfCurrent(target.auth, tokenStore.sessionEpoch)
            ControlPlaneHttp.cookieJar.clearAccountScoped()
            accountStore.setActiveAccountId(accountId)
            true
        } catch (superseded: IllegalStateException) {
            AppLogger.w(TAG, "account switch was superseded", superseded)
            false
        }
    }

    /** The explicit global sign-out also clears the fork's account list. */
    fun onGlobalSignOut() {
        accountStore.clearAllAccounts()
    }
}
