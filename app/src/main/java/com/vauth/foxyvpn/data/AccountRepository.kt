package com.vauth.foxyvpn.data

import com.vauth.foxyvpn.data.model.VpnAccount

private const val TAG = "AccountRepository"

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
 * - If the TokenStore session disappears without our involvement
 *   (upstream cleared it after a failed restore), the active entry,
 *   whose only live copy that was, is dropped on reconcile.
 */
class AccountRepository(
    private val accountStore: AccountStore,
    private val tokenStore: TokenStore,
) {

    /**
     * Startup reconciliation: v1 migration first, then align the
     * account list with whatever session upstream's store holds.
     */
    fun migrateAndReconcile() {
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
            session == null && active != null -> {
                // Signed out (or the session was invalidated) outside
                // this layer: the active account's live credentials are
                // gone with the session.
                accountStore.removeAccount(active.id)
            }
            session != null && active != null -> {
                if (active.auth != session) {
                    accountStore.updateAccount(active.copy(auth = session, lastError = null))
                }
            }
            session != null && active == null -> {
                val match = accountStore.getAccounts()
                    .firstOrNull { it.auth.accessToken == session.accessToken }
                if (match != null) {
                    accountStore.updateAccount(match.copy(auth = session, lastError = null))
                    accountStore.setActiveAccountId(match.id)
                } else if (!accountStore.hasAnyAccount()) {
                    // Pre-multi-account install: wrap the lone session
                    // as the primary account (the v1 migration's shape).
                    val primary = VpnAccount(email = "Primary Account", auth = session)
                    accountStore.addAccount(primary)
                    accountStore.setActiveAccountId(primary.id)
                }
                // Otherwise a fresh sign-in is in flight; the capture
                // hook registers it with its real email.
            }
        }
    }

    /**
     * Registers the just-completed browser sign-in (its session is
     * already in the TokenStore) as the active account. Dedupe is by
     * email, case-insensitive: capturing an account that is already in
     * the list refreshes that entry in place instead of duplicating it.
     */
    fun captureActiveSession(email: String, uid: String? = null): VpnAccount? {
        val session = tokenStore.loadAuth() ?: return null
        val existing = accountStore.getAccounts()
            .firstOrNull { it.email.equals(email, ignoreCase = true) }
        val entry = existing?.copy(
            auth = session,
            uid = uid ?: existing.uid,
            lastError = null,
        ) ?: VpnAccount(email = email, auth = session, uid = uid)
        accountStore.addAccount(entry)
        accountStore.setActiveAccountId(entry.id)
        return entry
    }

    /** Writes the TokenStore session back into the active entry, if any. */
    fun snapshotActiveSession() {
        val session = tokenStore.loadAuth() ?: return
        val active = accountStore.activeAccount() ?: return
        if (active.auth != session) {
            accountStore.updateAccount(active.copy(auth = session, lastError = null))
        }
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
            ControlPlaneHttp.cookieJar.clear()
            accountStore.setActiveAccountId(accountId)
            true
        } catch (superseded: IllegalStateException) {
            AppLogger.w(TAG, "account switch was superseded", superseded)
            false
        }
    }

    /**
     * Installs [accountId]'s STORED credentials as the TokenStore
     * session WITHOUT snapshotting the current session anywhere.
     *
     * This is the add-account flow's restore path: if capturing the
     * just-finished sign-in fails after the new session already landed
     * in the TokenStore, the previously active account is put back
     * exactly as it was. [switchTo] must NOT be used there — it first
     * snapshots the live session into the still-active entry, which
     * would overwrite the previous account's credentials with the new
     * account's session.
     */
    fun installStoredSession(accountId: String): Boolean {
        val target = accountStore.getAccount(accountId) ?: return false
        return try {
            tokenStore.completeLoginIfCurrent(target.auth, tokenStore.sessionEpoch)
            ControlPlaneHttp.cookieJar.clear()
            accountStore.setActiveAccountId(accountId)
            true
        } catch (superseded: IllegalStateException) {
            AppLogger.w(TAG, "session restore was superseded", superseded)
            false
        }
    }

    /** The explicit global sign-out also clears the fork's account list. */
    fun onGlobalSignOut() {
        accountStore.clearAllAccounts()
    }
}
