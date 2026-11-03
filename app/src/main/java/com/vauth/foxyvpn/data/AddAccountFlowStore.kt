package com.vauth.foxyvpn.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Durable record that an add-account browser sign-in is in progress
 * (Phase B2 fix for the preview defect: leaving the app mid-sign-in to
 * fetch the email code lost the whole flow).
 *
 * WHY a record is needed: every other piece of the flow's state is
 * in-memory (the screen's remember state, the WebView itself, the
 * WebChannel bridge). If Android kills the process while the user is
 * in their mail app reading the verification code, the back stack
 * restores AddAccountScreen, whose prepare step used to wipe the FxA
 * web state UNCONDITIONALLY — destroying the very session the code
 * was verifying — and a cold relaunch landed on Home with no trace
 * the flow ever existed.
 *
 * The record lives in its OWN prefs file so neither the upgrade
 * sanitizer nor the overhaul migration can touch it. It is tiny:
 * an active flag, the stage reached, and a start timestamp. A record
 * older than [MAX_AGE_MS] is stale and ignored (the FxA session it
 * points at is long dead by then anyway).
 *
 * The entry decision is a pure function ([decideEntry]) so the whole
 * truth table is unit-testable without Android:
 *  - pending BROWSER-stage record AND the FxA web session survived
 *    -> RESUME (skip the wipe, reopen the browser on the live session)
 *  - anything else -> FRESH (wipe, then open — the original behavior
 *    that prevents silently re-authenticating the same account)
 */
internal object AddAccountFlowStore {

    const val PREFS_NAME = "foxyvpn_add_account_flow"

    private const val KEY_ACTIVE = "flow_active"
    private const val KEY_STAGE = "flow_stage"
    private const val KEY_STARTED_AT = "flow_started_at"

    const val MAX_AGE_MS = 24L * 60 * 60 * 1000

    enum class Stage { BROWSER }

    enum class Entry { FRESH, RESUME }

    fun decideEntry(pendingStage: Stage?, fxASessionAlive: Boolean): Entry =
        if (pendingStage == Stage.BROWSER && fxASessionAlive) Entry.RESUME else Entry.FRESH

    fun begin(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_ACTIVE, true)
            .remove(KEY_STAGE)
            .putLong(KEY_STARTED_AT, System.currentTimeMillis())
            .apply()
    }

    fun markBrowserOpen(context: Context) {
        val p = prefs(context)
        if (!p.getBoolean(KEY_ACTIVE, false)) begin(context)
        p.edit().putString(KEY_STAGE, Stage.BROWSER.name).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    /** The pending stage, or null when no live (non-stale) flow exists. */
    fun pendingStage(context: Context, now: Long = System.currentTimeMillis()): Stage? =
        pendingStage(prefs(context), now)

    fun pendingStage(prefs: SharedPreferences, now: Long): Stage? {
        if (!prefs.getBoolean(KEY_ACTIVE, false)) return null
        val startedAt = prefs.getLong(KEY_STARTED_AT, 0L)
        if (startedAt <= 0L || now - startedAt > MAX_AGE_MS) return null
        return when (prefs.getString(KEY_STAGE, null)) {
            Stage.BROWSER.name -> Stage.BROWSER
            else -> null
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
