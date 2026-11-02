package com.vauth.foxyvpn.data

import com.vauth.foxyvpn.data.model.RuntimeAuth
import com.vauth.foxyvpn.data.model.VpnAccount

private const val TAG = "AccountRepository"

/**
 * Moves credentials between the fork's [AccountStore] (all accounts)
 * and upstream's [TokenStore] (the one active session).
 *
 * Invariants this class maintains:
 * - The ACTIVE account's live credentials are TokenStore's; whenever
 *   they are about to be replaced (switch, sign-out), the current
 *   TokenStore session is snapshotted back into the active entry.
 * - The LIVE TokenStore session is ALWAYS owned by exactly one entry,
 *   and that entry is the active one ([registerLiveSession]). This does
 *   NOT depend on any network call: an unidentified session gets a
 *   placeholder identity that is backfilled later. Stale entries (e.g.
 *   migrated from older generations) can never block it.
 * - A session is attached to an existing entry only on positive
 *   evidence (identity or lineage); an entry is never overwritten with
 *   a foreign session, and nothing is deleted except placeholder
 *   duplicates of the very same live session. A storage READ FAILURE
 *   (TokenStore.storageError) changes nothing at all.
 * - A switch installs the target's credentials through
 *   TokenStore.completeLoginIfCurrent (upstream's epoch-guarded path).
 * - Adding an account is DETACHED: the TokenStore is never touched.
 */
class AccountRepository(
    private val accountStore: AccountStore,
    private val tokenStore: TokenStore,
) {

    /** Startup: legacy hygiene first, then align the list with the session. */
    fun migrateAndReconcile() {
        runCatching { accountStore.wipeLegacyPendingLogin() }
            .onFailure { AppLogger.w(TAG, "legacy pending-login wipe failed", it) }
        runCatching { accountStore.migrateLegacyAccountsIfNeeded() }
            .onFailure { AppLogger.w(TAG, "legacy account migration failed", it) }
        runCatching { reconcile() }
            .onFailure { AppLogger.w(TAG, "account reconciliation failed", it) }
    }

    /**
     * Aligns the account list with the current TokenStore session. Runs at
     * process start AND after every completed sign-in (NavGraph).
     */
    fun reconcile() {
        val session = tokenStore.loadAuth()
        if (session == null) {
            val activeId = accountStore.activeAccountIdFlow.value
            val active = activeId?.let { accountStore.getAccount(it) } ?: return
            if (tokenStore.storageError.value != null) {
                // A transient read failure is indistinguishable from
                // sign-out EXCEPT for this flag — never act on it.
                AppLogger.w(
                    TAG,
                    "session unreadable at reconcile; leaving the account list untouched",
                )
                return
            }
            // Genuinely gone: the entry stays (the only copy of those
            // credentials); browser re-auth refreshes it via uid/email.
            if (active.lastError == null) {
                accountStore.updateAccount(
                    active.copy(lastError = "Session ended — sign in again to use this account.")
                )
            }
            return
        }
        registerLiveSession()
    }

    /**
     * Create-or-match the entry for the CURRENT session and make it the
     * active account. Offline and idempotent; safe to call repeatedly.
     *
     * [email]/[uid] (from a successful profile lookup) refine the match:
     * a placeholder is folded into the real entry that owns the identity.
     * [expectedEpoch], when given, is TokenStore.sessionEpoch captured
     * before the (slow) identity lookup; if the session changed since,
     * nothing is written — the identity belongs to a session that is gone.
     */
    fun registerLiveSession(
        email: String? = null,
        uid: String? = null,
        expectedEpoch: Long? = null,
    ): VpnAccount? {
        if (expectedEpoch == null) return registerLocked(email, uid)
        return try {
            tokenStore.withCurrentSession(expectedEpoch) { registerLocked(email, uid) }
        } catch (changed: IllegalStateException) {
            AppLogger.w(TAG, "live-session registration skipped: the session changed")
            null
        }
    }

    // Lock order: (optional) TokenStore.sessionLock -> AccountStore monitor,
    // never the reverse (AccountStore never calls into TokenStore).
    private fun registerLocked(email: String?, uid: String?): VpnAccount? {
        val session = tokenStore.loadAuth() ?: return null
        return synchronized(accountStore) {
            val plan = planLiveSession(
                accounts = accountStore.getAccounts(),
                activeId = accountStore.activeAccountIdFlow.value,
                session = session,
                email = email,
                uid = uid,
            )
            if (plan == null) {
                accountStore.activeAccount()
            } else {
                accountStore.commitRegistration(plan.entry, plan.removeIds)
                accountStore.getAccount(plan.entry.id)
            }
        }
    }

    /** Kept for source compatibility; now delegates to [registerLiveSession]. */
    fun captureActiveSession(email: String, uid: String? = null): VpnAccount? =
        registerLiveSession(email, uid)

    /** Kept for source compatibility; now delegates to [registerLiveSession]. */
    fun backfillActiveIdentity(email: String?, uid: String?) {
        if (email == null && uid == null) return
        registerLiveSession(email, uid)
    }

    /** Result of a detached add: the stored entry + whether it already existed. */
    data class DetachedAddResult(val account: VpnAccount, val wasExisting: Boolean)

    /**
     * Stores a session obtained by the add-account browser flow DIRECTLY in
     * the account list. The TokenStore is never touched. Dedupe by uid, then
     * email: re-adding an existing account refreshes its stored session.
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

    /** Writes the TokenStore session back into the active entry, if any. */
    fun snapshotActiveSession() {
        val session = tokenStore.loadAuth() ?: return
        val active = accountStore.activeAccount() ?: return
        if (active.auth == session) return
        if (provenDifferent(active.auth, session)) {
            AppLogger.w(TAG, "snapshot skipped: the live session is a different account's")
            return
        }
        accountStore.updateAccount(active.copy(auth = session, lastError = null))
    }

    /**
     * Makes [accountId] the active account: the outgoing session is
     * snapshotted first, then the target's stored credentials are installed
     * as the TokenStore session. The caller disconnects the VPN first.
     * Returns false when the target is missing or the install was superseded.
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
