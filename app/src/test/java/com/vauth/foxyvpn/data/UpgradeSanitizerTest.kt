package com.vauth.foxyvpn.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D1: the gates that would have caught it — they run against SEEDED UPGRADE
 * STATE (what the user's device actually holds), not a clean install.
 */
class UpgradeSanitizerTest {

    /** v1-era fork prefs + a few things the user did on 2026-11-01. */
    private fun seededV1EraPrefs() = FakeSharedPreferences(
        mapOf(
            // data-plane hazards
            "proxy_only_mode" to true,
            "dns_provider" to "CUSTOM",
            "custom_doh_url" to "https://dead.example/dns-query",
            "custom_edge_address" to "203.0.113.9",
            "upstream_proxy_enabled" to true,
            "custom_mtu" to 1400,
            "split_tunnel_mode" to "EXCLUDE",
            "excluded_apps" to setOf("com.android.chrome", "org.mozilla.firefox"),
            // control-plane anti-block set during the 406 investigation
            "tls_split_mode" to "SNI",
            "api_proxy_enabled" to true,
            "api_proxy_host" to "198.51.100.7",
            "api_proxy_port" to 1080,
            "api_relay_enabled" to true,
            "api_relay_url" to "https://relay.example/",
            // things that must survive
            "app_design" to "PORTAL",
            "theme_mode" to "DARK",
            "accent_color_index" to 4,
            "cached_real_ip" to "198.51.100.1",
            "cached_quota_remaining" to 123L,
            "socks_port" to 1081,
        )
    )

    @Test
    fun seededUpgradeState_isResetToUpstreamDefaults_andMarkerIsSet() {
        val prefs = seededV1EraPrefs()
        val log = mutableListOf<String>()

        assertTrue(UpgradeSanitizer.sanitize(prefs, log::add))

        // The five ranked vectors + MTU: keys are gone, so every getter returns
        // its upstream default (proxy_only=false, DNS=GOOGLE, no edge, no
        // upstream proxy, MTU=1280).
        for (key in listOf(
            "proxy_only_mode", "dns_provider", "custom_doh_url", "custom_edge_address",
            "upstream_proxy_enabled", "custom_mtu", "excluded_apps", "split_tunnel_mode",
        )) {
            assertFalse("$key should be removed", prefs.contains(key))
        }
        // Control-plane trio: activation flags removed ...
        for (key in listOf("tls_split_mode", "api_proxy_enabled", "api_relay_enabled")) {
            assertFalse("$key should be removed", prefs.contains(key))
        }
        // ... but the configured values stay (re-enable is one tap).
        assertEquals("198.51.100.7", prefs.getString("api_proxy_host", null))
        assertEquals("https://relay.example/", prefs.getString("api_relay_url", null))
        // Appearance / cache / listener port untouched.
        assertEquals("PORTAL", prefs.getString("app_design", null))
        assertEquals("DARK", prefs.getString("theme_mode", null))
        assertEquals(4, prefs.getInt("accent_color_index", -1))
        assertEquals("198.51.100.1", prefs.getString("cached_real_ip", null))
        assertEquals(123L, prefs.getLong("cached_quota_remaining", -1L))
        assertEquals(1081, prefs.getInt("socks_port", -1))
        // Marker.
        assertEquals(UpgradeSanitizer.CURRENT_REVISION, prefs.getInt(UpgradeSanitizer.KEY_REVISION, 0))
        // The log names what was reset (so the user can re-apply deliberately).
        assertTrue(log.single().contains("proxy_only_mode=true"))
        assertTrue(log.single().contains("excluded_apps=2 entries"))
    }

    @Test
    fun runsExactlyOnce_laterUserChangesSurvive() {
        val prefs = seededV1EraPrefs()
        assertTrue(UpgradeSanitizer.sanitize(prefs) {})
        // The user deliberately turns proxy-only on AFTER the sanitization.
        prefs.edit().putBoolean("proxy_only_mode", true).apply()

        assertFalse(UpgradeSanitizer.sanitize(prefs) {})
        assertTrue(prefs.getBoolean("proxy_only_mode", false))
    }

    @Test
    fun failedCommit_leavesStateUntouched_andRetriesNextStart() {
        val prefs = seededV1EraPrefs()
        prefs.failCommit = true
        assertFalse(UpgradeSanitizer.sanitize(prefs) {})
        // Atomic: neither the removals nor the marker landed (crash-equivalent).
        assertTrue(prefs.getBoolean("proxy_only_mode", false))
        assertEquals(0, prefs.getInt(UpgradeSanitizer.KEY_REVISION, 0))

        prefs.failCommit = false // next start
        assertTrue(UpgradeSanitizer.sanitize(prefs) {})
        assertFalse(prefs.contains("proxy_only_mode"))
        assertEquals(UpgradeSanitizer.CURRENT_REVISION, prefs.getInt(UpgradeSanitizer.KEY_REVISION, 0))
    }

    @Test
    fun usesASingleCommit_forRemovalsAndMarker() {
        val prefs = seededV1EraPrefs()
        UpgradeSanitizer.sanitize(prefs) {}
        assertEquals(1, prefs.commitCount)
    }

    @Test
    fun freshInstall_onlySetsTheMarker() {
        val prefs = FakeSharedPreferences()
        val log = mutableListOf<String>()
        assertTrue(UpgradeSanitizer.sanitize(prefs, log::add))
        assertEquals(setOf(UpgradeSanitizer.KEY_REVISION), prefs.all.keys)
        assertTrue(log.single().contains("nothing to reset"))
    }

    @Test
    fun markerOfWrongType_isTreatedAsUnset_notACrash() {
        val prefs = FakeSharedPreferences(mapOf(UpgradeSanitizer.KEY_REVISION to "oops", "proxy_only_mode" to true))
        assertTrue(UpgradeSanitizer.sanitize(prefs) {})
        assertFalse(prefs.contains("proxy_only_mode"))
        assertEquals(UpgradeSanitizer.CURRENT_REVISION, prefs.getInt(UpgradeSanitizer.KEY_REVISION, 0))
    }

    /**
     * B2 rev 2: a device that already ran the vc54 sanitizer (marker 1)
     * re-runs the reset once, and the newly listed legacy key
     * upstream_proxy_tls — which feeds the builder's review-flag
     * derivation — is removed too.
     */
    @Test
    fun revision1Device_rerunsOnce_andUpstreamProxyTlsIsReset() {
        val prefs = FakeSharedPreferences(
            mapOf(
                UpgradeSanitizer.KEY_REVISION to 1,
                "upstream_proxy_tls" to true,
                "upstream_proxy_enabled" to true,
                "proxy_only_mode" to true,
            ),
        )
        assertTrue(UpgradeSanitizer.sanitize(prefs) {})
        assertFalse(prefs.contains("upstream_proxy_tls"))
        assertFalse(prefs.contains("upstream_proxy_enabled"))
        assertFalse(prefs.contains("proxy_only_mode"))
        assertEquals(2, prefs.getInt(UpgradeSanitizer.KEY_REVISION, 0))
        // And it is once-only again at the new revision.
        assertFalse(UpgradeSanitizer.sanitize(prefs) {})
    }
}
