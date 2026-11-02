package com.vauth.foxyvpn.data

import android.content.Context
import com.vauth.foxyvpn.data.model.DEFAULT_ACCOUNT_QUOTA_BYTES
import com.vauth.foxyvpn.data.model.RuntimeAuth
import com.vauth.foxyvpn.data.model.VpnAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "AccountStore"

/**
 * The fork's account list, stored apart from upstream's session store.
 *
 * Upstream v2's TokenStore holds exactly one session — the ACTIVE
 * account's — and is not modified by this layer. This store holds every
 * account the user has added (credentials included), which one is
 * active, and per-account usage counters. AccountRepository moves
 * credentials between here and the TokenStore when the active account
 * changes.
 *
 * Serialization is hand-rolled org.json (the v2 tree carries no
 * kotlinx-serialization), and the one-time migration below reads the
 * v1 fork's `accounts_json_v1` payload in the shared `foxyvpn_tokens`
 * secure file, whose entries were kotlinx-serialized — the reader is
 * field-tolerant (defaults filled exactly as the v1 model declared).
 */
class AccountStore(context: Context) {

    private val appContext = context.applicationContext

    private val prefs by lazy {
        SecurePreferences(appContext, "foxyvpn_accounts") { error ->
            AppLogger.w(TAG, "account storage failure", error)
            error as? SecureStorageException ?: SecureStorageException(error)
        }
    }

    // Cumulative per-account byte usage. Plain prefs (not sensitive),
    // keyed by account id; same file the v1 fork used, so counters
    // survive the upgrade.
    private val usagePrefs =
        appContext.getSharedPreferences("foxyvpn_usage", Context.MODE_PRIVATE)

    private val _accountsFlow = MutableStateFlow(loadAccounts())
    val accountsFlow: StateFlow<List<VpnAccount>> = _accountsFlow

    private val _activeAccountIdFlow = MutableStateFlow(loadActiveAccountId())
    val activeAccountIdFlow: StateFlow<String?> = _activeAccountIdFlow

    private val _usageFlow = MutableStateFlow(loadUsage())
    val usageFlow: StateFlow<Map<String, Long>> = _usageFlow

    // ------------------------------------------------------------------
    // Account list. The mutators are @Synchronized: each does a
    // read-modify-write of _accountsFlow and they are called from
    // several coroutines at once (account screen, import, capture).
    // JVM monitors are reentrant, so the nested saveAccounts calls are
    // fine. (Same fix as fix round 1 applied to the v1 TokenStore.)
    // ------------------------------------------------------------------

    fun getAccounts(): List<VpnAccount> = _accountsFlow.value

    fun getAccount(id: String): VpnAccount? = _accountsFlow.value.firstOrNull { it.id == id }

    fun activeAccount(): VpnAccount? = _activeAccountIdFlow.value?.let { getAccount(it) }

    fun hasAnyAccount(): Boolean = _accountsFlow.value.isNotEmpty()

    @Synchronized
    fun saveAccounts(accounts: List<VpnAccount>) {
        val encoded = runCatching { encodeAccounts(accounts) }.getOrElse {
            AppLogger.w(TAG, "could not encode the account list", it)
            return
        }
        prefs.edit().putString(KEY_ACCOUNTS_JSON, encoded).commit()
        _accountsFlow.value = accounts
        if (_activeAccountIdFlow.value != null &&
            accounts.none { it.id == _activeAccountIdFlow.value }
        ) {
            setActiveAccountId(null)
        }
    }

    @Synchronized
    fun addAccount(account: VpnAccount) {
        val current = _accountsFlow.value.toMutableList()
        current.removeAll { it.id == account.id || it.email.equals(account.email, ignoreCase = true) }
        current.add(account)
        saveAccounts(current)
    }

    @Synchronized
    fun removeAccount(id: String) {
        saveAccounts(_accountsFlow.value.filterNot { it.id == id })
        resetUsedBytes(id)
    }

    @Synchronized
    fun toggleAccount(id: String, enabled: Boolean) {
        saveAccounts(_accountsFlow.value.map { if (it.id == id) it.copy(isEnabled = enabled) else it })
    }

    @Synchronized
    fun updateAccount(account: VpnAccount) {
        saveAccounts(_accountsFlow.value.map { if (it.id == account.id) account else it })
    }

    /**
     * The explicit global sign-out: every stored account, the active
     * marker and all usage counters go. Mirrors the v1 fork's clearAll
     * semantics — per-account removal is removeAccount.
     */
    @Synchronized
    fun clearAllAccounts() {
        val ids = _accountsFlow.value.map { it.id }
        prefs.edit().putString(KEY_ACCOUNTS_JSON, "[]").commit()
        _accountsFlow.value = emptyList()
        setActiveAccountId(null)
        val editor = usagePrefs.edit()
        ids.forEach { editor.remove(usageKey(it)) }
        editor.apply()
        _usageFlow.value = emptyMap()
    }

    @Synchronized
    fun setActiveAccountId(id: String?) {        if (id == null) {
            prefs.edit().remove(KEY_ACTIVE_ACCOUNT_ID).commit()
        } else {
            prefs.edit().putString(KEY_ACTIVE_ACCOUNT_ID, id).commit()
        }
        _activeAccountIdFlow.value = id
    }

    // ------------------------------------------------------------------
    // Per-account usage counters (v1 semantics kept).
    // ------------------------------------------------------------------

    fun getUsedBytes(accountId: String): Long = _usageFlow.value[accountId] ?: 0L

    @Synchronized
    fun addUsedBytes(accountId: String, deltaBytes: Long) {
        if (deltaBytes <= 0L) return
        val next = (_usageFlow.value[accountId] ?: 0L) + deltaBytes
        usagePrefs.edit().putLong(usageKey(accountId), next).apply()
        _usageFlow.value = _usageFlow.value + (accountId to next)
    }

    @Synchronized
    fun resetUsedBytes(accountId: String) {
        usagePrefs.edit().remove(usageKey(accountId)).apply()
        _usageFlow.value = _usageFlow.value - accountId
    }

    private fun usageKey(accountId: String) = "used_$accountId"

    private fun loadUsage(): Map<String, Long> =
        usagePrefs.all
            .filterKeys { it.startsWith("used_") }
            .mapNotNull { (key, value) -> (value as? Long)?.let { key.removePrefix("used_") to it } }
            .toMap()

    // ------------------------------------------------------------------
    // Persistence: org.json encoding of the account list.
    // ------------------------------------------------------------------

    private fun loadAccounts(): List<VpnAccount> {
        val raw = prefs.getString(KEY_ACCOUNTS_JSON, null) ?: return emptyList()
        return runCatching { decodeAccounts(raw) }.onFailure {
            AppLogger.w(TAG, "could not decode the stored account list", it)
        }.getOrDefault(emptyList())
    }

    private fun loadActiveAccountId(): String? =
        prefs.getString(KEY_ACTIVE_ACCOUNT_ID, null)?.takeIf { it.isNotBlank() }

    private fun encodeAccounts(accounts: List<VpnAccount>): String {
        val array = JSONArray()
        for (account in accounts) {
            array.put(
                JSONObject()
                    .put("id", account.id)
                    .put("email", account.email)
                    .put(
                        "auth",
                        JSONObject()
                            .put("accessToken", account.auth.accessToken)
                            .put("refreshToken", account.auth.refreshToken ?: JSONObject.NULL)
                            .put("expiresAtEpochSeconds", account.auth.expiresAtEpochSeconds),
                    )
                    .put("isEnabled", account.isEnabled)
                    .put("addedAtEpochSeconds", account.addedAtEpochSeconds)
                    .put("quotaMax", account.quotaMax)
                    .put("quotaRemaining", account.quotaRemaining ?: JSONObject.NULL)
                    .put("uid", account.uid ?: JSONObject.NULL)
                    .put("lastError", account.lastError ?: JSONObject.NULL),
            )
        }
        return array.toString()
    }

    private fun decodeAccounts(raw: String): List<VpnAccount> {
        val array = JSONArray(raw)
        val result = mutableListOf<VpnAccount>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val account = decodeAccount(item) ?: continue
            result.add(account)
        }
        return result
    }

    private fun decodeAccount(item: JSONObject): VpnAccount? {
        val authJson = item.optJSONObject("auth") ?: return null
        val accessToken = authJson.optString("accessToken").takeIf { it.isNotBlank() } ?: return null
        val email = item.optString("email").takeIf { it.isNotBlank() } ?: return null
        return VpnAccount(
            id = item.optString("id").takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString(),
            email = email,
            auth = RuntimeAuth(
                accessToken = accessToken,
                refreshToken = authJson.optString("refreshToken").takeIf {
                    it.isNotEmpty() && !authJson.isNull("refreshToken")
                },
                expiresAtEpochSeconds = authJson.optLong("expiresAtEpochSeconds", 0L),
            ),
            isEnabled = item.optBoolean("isEnabled", true),
            addedAtEpochSeconds = item.optLong(
                "addedAtEpochSeconds",
                System.currentTimeMillis() / 1000,
            ),
            quotaMax = item.optLong("quotaMax", DEFAULT_ACCOUNT_QUOTA_BYTES),
            quotaRemaining = if (item.has("quotaRemaining") && !item.isNull("quotaRemaining")) {
                item.optLong("quotaRemaining")
            } else {
                null
            },
            uid = item.optString("uid").takeIf { it.isNotEmpty() && !item.isNull("uid") },
            lastError = item.optString("lastError").takeIf {
                it.isNotEmpty() && !item.isNull("lastError")
            },
        )
    }

    // ------------------------------------------------------------------
    // One-time migration from the v1 fork's storage.
    // ------------------------------------------------------------------

    /**
     * Imports the v1 fork's account list (`accounts_json_v1`, written by
     * the v1 TokenStore into the shared `foxyvpn_tokens` secure file)
     * the first time this store is found empty. Returns the imported
     * accounts (empty when there was nothing to import). The legacy key
     * is left in place; the migrated flag here makes this run once.
     */
    @Synchronized
    fun migrateLegacyAccountsIfNeeded(): List<VpnAccount> {
        if (hasAnyAccount()) return emptyList()
        if (prefs.getString(KEY_MIGRATED_V1, null) != null) return emptyList()
        val legacyPrefs = SecurePreferences(appContext, "foxyvpn_tokens") { error ->
            error as? SecureStorageException ?: SecureStorageException(error)
        }
        val raw = runCatching { legacyPrefs.getString(LEGACY_KEY_ACCOUNTS_JSON, null) }
            .getOrNull()
        prefs.edit().putString(KEY_MIGRATED_V1, "1").commit()
        if (raw.isNullOrBlank()) return emptyList()
        val imported = runCatching { decodeAccounts(raw) }.onFailure {
            AppLogger.w(TAG, "could not decode the v1 account list for migration", it)
        }.getOrDefault(emptyList())
        if (imported.isNotEmpty()) saveAccounts(imported)
        return imported
    }

    companion object {
        private const val KEY_ACCOUNTS_JSON = "accounts_json_v2"
        private const val KEY_ACTIVE_ACCOUNT_ID = "active_account_id"
        private const val KEY_MIGRATED_V1 = "migrated_from_v1"
        private const val LEGACY_KEY_ACCOUNTS_JSON = "accounts_json_v1"
    }
}
