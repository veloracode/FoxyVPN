package com.vauth.foxyvpn.data

import android.content.Context
import android.content.SharedPreferences

/**
 * One-time sanitization of settings state carried across the upgrade
 * chain (v1-era fork -> v1.1.6 -> v1, same applicationId, same
 * `foxyvpn_settings` prefs file).
 *
 * WHY: the tunnel code is byte-identical to upstream v2.0.0, so the
 * only way a v1 install can behave differently from stock v2.0.0 is
 * persisted state that stock never had (stock v2.0.0 is a different
 * applicationId, so it always starts clean). This resets every
 * persisted key that can change the DATA PLANE (what the generated
 * sing-box config contains) or can resurrect a dead control-plane
 * fallback, so "identical code" also starts from "identical state".
 *
 * HOW (crash-idempotent by construction): every removal AND the marker
 * go through ONE SharedPreferences.Editor and ONE synchronous
 * commit(). SharedPreferences writes the whole file atomically
 * (backup + rename), so after a crash either nothing happened (rerun)
 * or everything happened (marker present, no rerun). A failed commit()
 * leaves the marker unset on disk, so the next start retries.
 *
 * Keys are REMOVED, not rewritten: an absent key makes every getter
 * return upstream's default, which is exactly the state of a clean
 * stock install, and it keeps following upstream's defaults if they
 * ever change. Credentials in the secure file are deliberately NOT
 * touched (no Keystore access on the startup path; with the enabling
 * flags gone the stored host/credentials are inert and re-enabling in
 * Settings reuses them).
 *
 * Must run BEFORE SettingsStore is constructed and before anything
 * reads the settings (first statements of Application.onCreate).
 */
internal object UpgradeSanitizer {

    private const val TAG = "UpgradeSanitizer"

    const val PREFS_NAME = "foxyvpn_settings"

    /** Int revision marker, same prefs file as the keys it guards. */
    const val KEY_REVISION = "state_sanitized_rev"

    /** Bump to force another one-time pass in a future release. */
    const val CURRENT_REVISION = 1

    /**
     * Everything TunnelSettings reads from plain prefs that can alter the
     * tunnel, minus listener-only settings (socks port, local-proxy auth),
     * which cannot black-hole TUN traffic.
     */
    internal val DATA_PLANE_KEYS: List<String> = listOf(
        // Rank 1: omits the TUN inbound entirely while reporting CONNECTED.
        "proxy_only_mode",
        // Rank 2: a stale custom DoH becomes the tunnel's only remote DNS.
        "dns_provider",
        "custom_doh_url",
        // Rank 4: replaces the MASQUE server address.
        "custom_edge_address",
        // Rank 5: chains proxy-out through a (possibly dead) upstream proxy.
        "upstream_proxy_enabled",
        // Extra vector (missed by the diagnosis): v1-era default MTU was
        // 1400, upstream's is 1280; a persisted 1400 black-holes large
        // packets inside the QUIC/MASQUE tunnel while small ones pass.
        "custom_mtu",
        // Upstream tunables the v1.1.6 stock UI could have set.
        "http_version",
        "ipv6_enabled",
        "tls_fragment_mode",
        "utls_fingerprint",
        "tcp_fast_open",
        "max_concurrent_streams",
        // Split tunnelling: an excluded browser would make any on-device
        // verification of the fix ambiguous (traffic would bypass the VPN).
        "split_tunnel_mode",
        "excluded_apps",
        "included_apps",
    )

    /**
     * Rank 3: ONLY the switches that activate the control-plane
     * fallbacks. Host/port/URL/credentials are left in place.
     */
    internal val CONTROL_PLANE_FLAG_KEYS: List<String> = listOf(
        "tls_split_mode",
        "api_proxy_enabled",
        "api_relay_enabled",
    )

    internal val RESET_KEYS: List<String> = DATA_PLANE_KEYS + CONTROL_PLANE_FLAG_KEYS

    /** Application entry point. Never throws into the caller's startup. */
    fun runOnce(context: Context): Boolean {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sanitize(prefs) { AppLogger.i(TAG, it) }
    }

    /**
     * Pure-prefs core (unit-testable with an in-memory SharedPreferences).
     * Returns true when a sanitization pass was committed on this call.
     */
    fun sanitize(prefs: SharedPreferences, log: (String) -> Unit): Boolean {
        val revision = try {
            prefs.getInt(KEY_REVISION, 0)
        } catch (wrongType: ClassCastException) {
            0
        }
        if (revision >= CURRENT_REVISION) return false

        val snapshot = prefs.all
        val editor = prefs.edit()
        val cleared = ArrayList<String>()
        for (key in RESET_KEYS) {
            if (snapshot.containsKey(key)) {
                cleared += "$key=${describe(snapshot[key])}"
                editor.remove(key)
            }
        }
        editor.putInt(KEY_REVISION, CURRENT_REVISION)
        val committed = editor.commit()
        if (committed) {
            log(
                if (cleared.isEmpty()) {
                    "state sanitization rev $CURRENT_REVISION: nothing to reset"
                } else {
                    "state sanitization rev $CURRENT_REVISION: reset to defaults -> " +
                        cleared.joinToString(", ")
                }
            )
        } else {
            log("state sanitization rev $CURRENT_REVISION: commit failed, will retry next start")
        }
        return committed
    }

    private fun describe(value: Any?): String =
        when (value) {
            null -> "null"
            is Set<*> -> "${value.size} entries"
            is String -> value.take(80)
            else -> value.toString()
        }
}
