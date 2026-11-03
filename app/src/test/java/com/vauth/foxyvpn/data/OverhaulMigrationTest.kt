package com.vauth.foxyvpn.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase B1 pref migration gates. The seed reproduces the user's device
 * state after the v1 fix round: the sanitizer already ran (marker set,
 * data-plane hazards reset), the design is PORTAL (pre-overhaul), and the
 * user has made appearance choices that must survive byte-identical
 * except where the migration's contract says otherwise.
 */
class OverhaulMigrationTest {

    /** Post-sanitizer device state (see UpgradeSanitizerTest for the pre-state). */
    private fun seededDevicePrefs() = FakeSharedPreferences(
        mapOf(
            "state_sanitized_rev" to 1,
            // appearance choices the user made on the v1 build
            "app_design" to "PORTAL",
            "theme_mode" to "DARK",
            "accent_color_index" to 4,
            "color_style" to "TONAL_SPOT",
            "dynamic_color" to false,
            "color_contrast" to "MEDIUM",
            "ui_font_scale" to "LARGE",
            "app_language" to "PERSIAN",
            "screen_transition" to "SLIDE",
            "nav_bar_style" to "FloatingPill",
            "connect_button_style" to "RING",
            "card_corner_style" to "PILL",
            "list_density" to "COMPACT",
            "custom_connect_color" to 0xFF112233L,
            "custom_config_card_color" to 0xFF445566L,
            "show_server_ping" to false,
            "monospace_address" to true,
            "confirm_remove" to false,
            "haptic_feedback" to false,
            // product state that must never be touched by the UI migration
            "cached_real_ip" to "198.51.100.1",
            "cached_quota_remaining" to 123L,
            "socks_port" to 1081,
            "tls_split_mode" to "FIRST_BYTE",
        )
    )

    @Test
    fun seededUpgrade_portalDesign_landsOnBento_andEverythingElseSurvives() {
        val prefs = seededDevicePrefs()
        val before = prefs.getAll().entries
            .filter { it.key != "app_design" }
            .associate { it.key to it.value }
        val log = mutableListOf<String>()

        assertTrue(OverhaulMigration.migrate(prefs, log::add))

        assertEquals("BENTO", prefs.getString("app_design", null))
        assertEquals(OverhaulMigration.CURRENT_REVISION, prefs.getInt(OverhaulMigration.KEY_REVISION, 0))
        // Every other key is byte-identical to before.
        val after = prefs.getAll().entries
            .filter { it.key != "app_design" && it.key != OverhaulMigration.KEY_REVISION }
            .associate { it.key to it.value }
        assertEquals(before, after)
        assertTrue(log.single().contains("PORTAL"))
    }

    @Test
    fun seededUpgrade_amoledTheme_becomesDarkPlusAmoledFlag() {
        val prefs = FakeSharedPreferences(
            mapOf(
                "app_design" to "PIXEL",
                "theme_mode" to "AMOLED",
            )
        )
        assertTrue(OverhaulMigration.migrate(prefs) {})
        assertEquals("BENTO", prefs.getString("app_design", null))
        assertEquals("DARK", prefs.getString("theme_mode", null))
        assertTrue(prefs.getBoolean("amoled_black", false))
    }

    @Test
    fun pixelDesign_isNotNameMapped_itAlsoLandsOnBento() {
        val prefs = FakeSharedPreferences(mapOf("app_design" to "PIXEL"))
        assertTrue(OverhaulMigration.migrate(prefs) {})
        assertEquals("BENTO", prefs.getString("app_design", null))
    }

    @Test
    fun oldDesign_landsOnBento_too() {
        val prefs = FakeSharedPreferences(mapOf("app_design" to "OLD"))
        assertTrue(OverhaulMigration.migrate(prefs) {})
        assertEquals("BENTO", prefs.getString("app_design", null))
    }

    @Test
    fun freshInstall_writesOnlyTheMarker() {
        val prefs = FakeSharedPreferences()
        assertTrue(OverhaulMigration.migrate(prefs) {})
        assertEquals(OverhaulMigration.CURRENT_REVISION, prefs.getInt(OverhaulMigration.KEY_REVISION, 0))
        assertFalse(prefs.contains("app_design"))
        assertEquals(1, prefs.getAll().size)
    }

    @Test
    fun alreadyMigrated_isANoOp_andIdempotent() {
        val prefs = seededDevicePrefs()
        assertTrue(OverhaulMigration.migrate(prefs) {})
        val afterFirst = HashMap(prefs.getAll())
        val commits = prefs.commitCount
        // Second run: marker gates re-entry — no commit, no writes, and
        // the return value reports that no pass ran.
        assertFalse(OverhaulMigration.migrate(prefs) {})
        assertEquals(afterFirst, HashMap(prefs.getAll()))
        assertEquals(commits, prefs.commitCount) // the second run never writes
    }

    @Test
    fun commitFailure_leavesEverythingUntouched_includingMarker() {
        val prefs = seededDevicePrefs()
        prefs.failCommit = true
        assertFalse(OverhaulMigration.migrate(prefs) {})
        assertEquals("PORTAL", prefs.getString("app_design", null))
        assertFalse(prefs.contains(OverhaulMigration.KEY_REVISION))
    }

    @Test
    fun corruptValues_decodeTotally_neverThrow() {
        val prefs = FakeSharedPreferences(
            mapOf(
                "app_design" to 42, // wrong type
                "theme_mode" to "NOT_A_MODE",
            )
        )
        assertTrue(OverhaulMigration.migrate(prefs) {})
        // Wrong-typed design reads as absent: no design written, no throw.
        assertEquals(42, prefs.getInt("app_design", -1))
        // Unknown theme names are left for the store's total decode.
        assertEquals("NOT_A_MODE", prefs.getString("theme_mode", null))
    }

    @Test
    fun newEnum_hasNoPortal_andMigrationRewritesEveryLegacyValue() {
        // PORTAL exists only in the legacy model; the new enum must not
        // contain it (a stored PORTAL is rewritten, never decoded).
        for (value in OverhaulMigration.NEW_DESIGN_VALUES) {
            SettingsStore.AppDesign.valueOf(value)
        }
        try {
            SettingsStore.AppDesign.valueOf("PORTAL")
            throw AssertionError("PORTAL must not exist in the new AppDesign enum")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
        // Fork PIXEL shares its NAME with Zed's Pixel — which is exactly
        // why every legacy value, PIXEL included, is rewritten to BENTO
        // instead of being decoded by name.
        for (value in OverhaulMigration.LEGACY_DESIGN_VALUES) {
            val prefs = FakeSharedPreferences(mapOf("app_design" to value))
            assertTrue(OverhaulMigration.migrate(prefs) {})
            assertEquals("legacy $value", "BENTO", prefs.getString("app_design", null))
        }
    }
}
