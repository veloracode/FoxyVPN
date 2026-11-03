package com.vauth.foxyvpn.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase B2, Phase 0 gate: the FULL seeded upgrade chain the user's
 * device actually ran — v1 (vc53) state -> vc54 sanitizer -> preview/
 * final overhaul migration — executed in order on ONE prefs object.
 * After both migrations, every key the sing-box config builder reads
 * must be at its default (absent), and the design must be Bento.
 * This is the state-interplay reproduction for the preview's connect
 * defect: if a poisoned key survived the chain, it would fail here.
 */
class UpgradeChainTest {

    @Test
    fun vc53State_throughSanitizerAndOverhaulMigration_isClean() {
        val prefs = FakeSharedPreferences(
            mapOf(
                // vc53-era hazards (the v1 defect vectors).
                "proxy_only_mode" to true,
                "dns_provider" to "CUSTOM",
                "custom_doh_url" to "https://dead.example/dns-query",
                "custom_edge_address" to "203.0.113.9",
                "upstream_proxy_enabled" to true,
                "upstream_proxy_tls" to true,
                "custom_mtu" to 1400,
                "http_version" to "HTTP_3",
                "ipv6_enabled" to true,
                "tls_fragment_mode" to "PACKET",
                "utls_fingerprint" to "CHROME",
                "tcp_fast_open" to true,
                "max_concurrent_streams" to 256,
                "split_tunnel_mode" to "INCLUDE",
                "excluded_apps" to setOf("com.android.chrome"),
                "included_apps" to setOf("org.mozilla.firefox"),
                "tls_split_mode" to "SNI",
                "api_proxy_enabled" to true,
                "api_relay_enabled" to true,
                // Pre-overhaul appearance: fork Pixel design + AMOLED.
                "app_design" to "PIXEL",
                "theme_mode" to "AMOLED",
                // Things neither migration may touch.
                "socks_port" to 1080,
                "block_ads" to true,
            ),
        )

        // Step 1: the vc54 sanitizer (now rev 2).
        assertTrue(UpgradeSanitizer.sanitize(prefs) {})
        // Step 2: the overhaul migration.
        assertTrue(OverhaulMigration.migrate(prefs) {})

        // Every builder-read data/control key is absent (= default).
        for (key in listOf(
            "proxy_only_mode", "dns_provider", "custom_doh_url", "custom_edge_address",
            "upstream_proxy_enabled", "upstream_proxy_tls", "custom_mtu", "http_version",
            "ipv6_enabled", "tls_fragment_mode", "utls_fingerprint", "tcp_fast_open",
            "max_concurrent_streams", "split_tunnel_mode", "excluded_apps", "included_apps",
            "tls_split_mode", "api_proxy_enabled", "api_relay_enabled",
        )) {
            assertFalse("$key must be reset by the chain", prefs.contains(key))
        }
        // The one throwing require in tunnelSnapshot() can no longer arm:
        // it needs upstream_proxy_enabled (gone) — asserted above.

        // Design landed on Bento; AMOLED decomposed to DARK + flag.
        assertEquals("BENTO", prefs.getString("app_design", null))
        assertEquals("DARK", prefs.getString("theme_mode", null))
        assertTrue(prefs.getBoolean("amoled_black", false))

        // Untouched keys survive both migrations.
        assertEquals(1080, prefs.getInt("socks_port", 0))
        assertTrue(prefs.getBoolean("block_ads", false))

        // Both markers at their current revisions; chain is idempotent.
        assertEquals(
            UpgradeSanitizer.CURRENT_REVISION,
            prefs.getInt(UpgradeSanitizer.KEY_REVISION, 0),
        )
        assertEquals(
            OverhaulMigration.CURRENT_REVISION,
            prefs.getInt(OverhaulMigration.KEY_REVISION, 0),
        )
        assertFalse(UpgradeSanitizer.sanitize(prefs) {})
        assertFalse(OverhaulMigration.migrate(prefs) {})
    }
}
