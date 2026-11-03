package com.vauth.foxyvpn.data

import android.content.Context
import android.content.SharedPreferences

/**
 * One-time migration of persisted APPEARANCE state into the overhaul
 * (ZedSecure-look) preference model — Phase B1 of the Zed overhaul.
 *
 * WHY: the overhaul replaces the home-screen design axis. The v1 port's
 * AppDesign values (PIXEL / PORTAL / OLD) name DIFFERENT designs from
 * ZedSecure's (fork PIXEL was a reskin of the old Mozilla-style home;
 * Zed's Pixel is Zed's own second design). Mapping by name would
 * silently land the user on a design they never picked, in a build
 * where that design does not even exist yet. The overhaul rule
 * (DESIGN-MAP §6.4) is therefore: ANY pre-overhaul design value lands
 * on BENTO, Zed's default; the user re-picks from the Interface page.
 *
 * WHAT ELSE:
 *  - The fork folded AMOLED into ThemeMode (a fourth enum value); the
 *    Zed model keeps themeMode {System, Light, Dark} plus a separate
 *    amoledBlack switch. A stored AMOLED becomes DARK + amoledBlack.
 *  - CardCornerStyle values were the fork's approximations (10/28/48);
 *    the stored enum NAME survives (Squared/Rounded/Pill exist in both
 *    models) — only the dp values change, in the theme layer, so no
 *    stored value needs rewriting.
 *  - The fork's NavBarStyle lacks Dock; stored names are a subset of
 *    the new enum (which also interprets "Dock"), so nothing rewrites.
 *  - Everything else appearance-related is an identical axis in both
 *    models (theme mode, dynamic color, contrast, accent index —
 *    including the fork-appended index 10, Signal Blue — font scale,
 *    transition names, language subset, personalize colors, behaviour
 *    switches) and is deliberately left untouched.
 *  - New overhaul prefs are NOT written: absent keys decode to Zed's
 *    defaults in SettingsStore, which is the clean-install state.
 *
 * HOW: same crash-idempotent pattern as [UpgradeSanitizer] — every
 * change AND the marker through ONE Editor and ONE synchronous
 * commit(); total decode (wrong-typed values read as absent, never
 * throw); idempotent (the marker gates re-entry, and the transformation
 * itself is a fixed point). Runs in Application.onCreate right after
 * the sanitizer, before SettingsStore exists.
 */
internal object OverhaulMigration {

    private const val TAG = "OverhaulMigration"

    const val KEY_REVISION = "ui_overhaul_rev"

    /** Bump to force another one-time pass in a future phase. */
    const val CURRENT_REVISION = 1

    private const val KEY_APP_DESIGN = "app_design"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_AMOLED_BLACK = "amoled_black"

    /** The pre-overhaul design axis (v1 port). Presence of any of these — or
     *  of a design value unknown to the new model — marks pre-overhaul state. */
    internal val LEGACY_DESIGN_VALUES: Set<String> = setOf("PIXEL", "PORTAL", "OLD")

    /** The new (Zed) design axis. OLD exists in both models with different
     *  meaning; it is only trusted when the migration marker is present. */
    internal val NEW_DESIGN_VALUES: Set<String> = setOf("BENTO", "PIXEL", "OLD")

    /** Application entry point. Never throws into the caller's startup. */
    fun runOnce(context: Context): Boolean {
        val prefs = context.applicationContext
            .getSharedPreferences(UpgradeSanitizer.PREFS_NAME, Context.MODE_PRIVATE)
        return migrate(prefs) { AppLogger.i(TAG, it) }
    }

    /**
     * Pure-prefs core (unit-testable with an in-memory SharedPreferences).
     * Returns true when a migration pass was committed on this call.
     */
    fun migrate(prefs: SharedPreferences, log: (String) -> Unit): Boolean {
        val revision = try {
            prefs.getInt(KEY_REVISION, 0)
        } catch (wrongType: ClassCastException) {
            0
        }
        if (revision >= CURRENT_REVISION) return false

        val editor = prefs.edit()
        val changes = ArrayList<String>()

        // --- Design axis: any pre-overhaul or unknown value -> BENTO. ---
        val design = readString(prefs, KEY_APP_DESIGN)
        if (design == null) {
            // Nothing stored: fresh install (or a state the sanitizer
            // already cleaned). The new getter defaults to BENTO; write
            // nothing, so "absent = default" keeps holding.
            changes += "app_design absent (fresh state)"
        } else if (design in NEW_DESIGN_VALUES && design != "OLD" && design != "PIXEL") {
            changes += "app_design=$design kept"
        } else {
            // PIXEL / PORTAL / OLD (legacy meanings) or garbage: Bento.
            editor.putString(KEY_APP_DESIGN, "BENTO")
            changes += "app_design=$design -> BENTO"
        }

        // --- AMOLED fold: ThemeMode.AMOLED -> DARK + amoledBlack=true. ---
        val themeMode = readString(prefs, KEY_THEME_MODE)
        if (themeMode == "AMOLED") {
            editor.putString(KEY_THEME_MODE, "DARK")
            editor.putBoolean(KEY_AMOLED_BLACK, true)
            changes += "theme_mode=AMOLED -> DARK + amoled_black=true"
        }

        editor.putInt(KEY_REVISION, CURRENT_REVISION)
        val committed = editor.commit()
        if (committed) {
            log("ui overhaul migration rev $CURRENT_REVISION: " + changes.joinToString("; "))
        } else {
            log("ui overhaul migration rev $CURRENT_REVISION: commit failed, will retry next start")
        }
        return committed
    }

    /** Total string read: a wrong-typed value is treated as absent. */
    private fun readString(prefs: SharedPreferences, key: String): String? =
        try {
            prefs.getString(key, null)
        } catch (wrongType: ClassCastException) {
            null
        }
}
