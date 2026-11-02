package com.vauth.foxyvpn.data

import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

import com.vauth.foxyvpn.ui.theme.AppLanguage
import com.vauth.foxyvpn.ui.theme.CardCornerStyle
import com.vauth.foxyvpn.ui.theme.ColorContrast
import com.vauth.foxyvpn.ui.theme.ColorStyle
import com.vauth.foxyvpn.ui.theme.ConnectButtonStyle
import com.vauth.foxyvpn.ui.theme.DEFAULT_ACCENT_INDEX
import com.vauth.foxyvpn.ui.theme.ListDensity
import com.vauth.foxyvpn.ui.theme.NavBarStyle
import com.vauth.foxyvpn.ui.theme.ScreenTransition
import com.vauth.foxyvpn.ui.theme.ThemeMode
import com.vauth.foxyvpn.ui.theme.UiFontScale
import com.vauth.foxyvpn.vpn.FoxyVpnService
import com.vauth.foxyvpn.widget.QuickConnectWidgetProvider

class SettingsStore(context: Context) {
    internal val proxySettingsLock = Any()
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("foxyvpn_settings", Context.MODE_PRIVATE)

    private fun <T> readCredential(block: () -> T): T =
        try {
            block()
        } catch (error: Exception) {
            throw error as? SecureStorageException ?: SecureStorageException(error)
        }

    private val credentialsPrefs by lazy {
        SecurePreferences(appContext, "foxyvpn_settings_credentials")
    }

    data class AccountSnapshot(
        val realIp: String?,
        val country: String?,
        val quotaRemaining: Long?,
        val quotaMax: Long?,
        val unlimited: Boolean,
        /** Account-list id the quota values belong to (null = unattributed). */
        val quotaAccountId: String? = null,
        /** When the quota values were fetched from the server (0 = never). */
        val quotaFetchedAtEpochSeconds: Long = 0L,
    )

    private fun accountSnapshot() =
        AccountSnapshot(
            cachedRealIp,
            cachedRealCountry,
            cachedQuotaRemaining,
            cachedQuotaMax,
            cachedIsUnlimited,
            prefs.getString(KEY_CACHED_QUOTA_ACCOUNT_ID, null),
            prefs.getLong(KEY_CACHED_QUOTA_FETCHED_AT, 0L),
        )

    private val mutableAccount = kotlinx.coroutines.flow.MutableStateFlow(accountSnapshot())
    val account: kotlinx.coroutines.flow.StateFlow<AccountSnapshot> = mutableAccount

    /**
     * Resolves the active account id for quota writes that do not know it
     * (the byte-identical VPN service calls the 3-argument saveAccountQuota).
     * Set once by FoxyVpnApp; null in unit tests / before wiring.
     */
    @Volatile
    var activeAccountIdProvider: (() -> String?)? = null

    private val accountListener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (
                key == null ||
                    key in
                        setOf(
                            KEY_CACHED_REAL_IP,
                            KEY_CACHED_REAL_COUNTRY,
                            KEY_CACHED_QUOTA_REMAINING,
                            KEY_CACHED_QUOTA_MAX,
                            KEY_CACHED_IS_UNLIMITED,
                            KEY_CACHED_QUOTA_ACCOUNT_ID,
                            KEY_CACHED_QUOTA_FETCHED_AT,
                        )
            ) {
                mutableAccount.value = accountSnapshot()
            }
        }

    init {

        if (prefs.getString(KEY_DNS_PROVIDER, null) == "ADGUARD") {
            prefs.edit().putString(KEY_DNS_PROVIDER, DnsProvider.GOOGLE.name).apply()
        }
        prefs.registerOnSharedPreferenceChangeListener(accountListener)
    }

    var bypassLocalNetwork: Boolean
        get() = prefs.getBoolean("bypass_local_network", true)
        set(value) {
            prefs.edit().putBoolean("bypass_local_network", value).apply()
        }

    var automaticRecovery: Boolean
        get() = prefs.getBoolean("automatic_recovery", false)
        set(value) {
            prefs.edit().putBoolean("automatic_recovery", value).apply()
        }

    var themeMode: ThemeMode
        get() =
            ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME_MODE, null) }
                ?: ThemeMode.SYSTEM
        set(value) {
            prefs.edit().putString(KEY_THEME_MODE, value.name).apply()
            QuickConnectWidgetProvider.updateAll(appContext, FoxyVpnService.state.value)
        }

    var appDesign: AppDesign
        get() = AppDesign.entries.firstOrNull {
            it.name == prefs.getString(KEY_APP_DESIGN, null)
        } ?: AppDesign.PIXEL
        set(value) { prefs.edit().putString(KEY_APP_DESIGN, value.name).apply() }

    // ------------------------------------------------------------------
    // Interface / appearance settings (ported from ZedSecure's Ui page).
    // Defaults match ZedSecure's defaults.
    // ------------------------------------------------------------------

    var colorStyle: ColorStyle
        get() = ColorStyle.entries.firstOrNull {
            it.name == prefs.getString(KEY_COLOR_STYLE, null)
        } ?: ColorStyle.ZedSecure
        set(value) { prefs.edit().putString(KEY_COLOR_STYLE, value.name).apply() }

    var dynamicColor: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC_COLOR, false)
        set(value) { prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, value).apply() }

    var accentColorIndex: Int
        get() = if (prefs.contains(KEY_ACCENT_COLOR_INDEX)) {
            prefs.getInt(KEY_ACCENT_COLOR_INDEX, DEFAULT_ACCENT_INDEX)
        } else {
            DEFAULT_ACCENT_INDEX // Signal blue for fresh installs only
        }
        set(value) { prefs.edit().putInt(KEY_ACCENT_COLOR_INDEX, value).apply() }

    var hapticFeedback: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true) // default ON
        set(value) { prefs.edit().putBoolean(KEY_HAPTIC_FEEDBACK, value).apply() }

    var colorContrast: ColorContrast
        get() = ColorContrast.entries.firstOrNull {
            it.name == prefs.getString(KEY_COLOR_CONTRAST, null)
        } ?: ColorContrast.Standard
        set(value) { prefs.edit().putString(KEY_COLOR_CONTRAST, value.name).apply() }

    var reduceMotion: Boolean
        get() = prefs.getBoolean(KEY_REDUCE_MOTION, false)
        set(value) { prefs.edit().putBoolean(KEY_REDUCE_MOTION, value).apply() }

    var uiFontScale: UiFontScale
        get() = UiFontScale.entries.firstOrNull {
            it.name == prefs.getString(KEY_UI_FONT_SCALE, null)
        } ?: UiFontScale.Normal
        set(value) { prefs.edit().putString(KEY_UI_FONT_SCALE, value.name).apply() }

    var appLanguage: AppLanguage
        get() = AppLanguage.entries.firstOrNull {
            it.name == prefs.getString(KEY_APP_LANGUAGE, null)
        } ?: AppLanguage.System
        set(value) { prefs.edit().putString(KEY_APP_LANGUAGE, value.name).apply() }

    var screenTransition: ScreenTransition
        get() = ScreenTransition.entries.firstOrNull {
            it.name == prefs.getString(KEY_SCREEN_TRANSITION, null)
        } ?: ScreenTransition.Push
        set(value) { prefs.edit().putString(KEY_SCREEN_TRANSITION, value.name).apply() }

    var navBarStyle: NavBarStyle
        get() = NavBarStyle.entries.firstOrNull {
            it.name == prefs.getString(KEY_NAV_BAR_STYLE, null)
        } ?: NavBarStyle.ExpressivePill
        set(value) { prefs.edit().putString(KEY_NAV_BAR_STYLE, value.name).apply() }

    var connectButtonStyle: ConnectButtonStyle
        get() = ConnectButtonStyle.entries.firstOrNull {
            it.name == prefs.getString(KEY_CONNECT_BUTTON_STYLE, null)
        } ?: ConnectButtonStyle.Hero
        set(value) { prefs.edit().putString(KEY_CONNECT_BUTTON_STYLE, value.name).apply() }

    var cardCornerStyle: CardCornerStyle
        get() = CardCornerStyle.entries.firstOrNull {
            it.name == prefs.getString(KEY_CARD_CORNER_STYLE, null)
        } ?: CardCornerStyle.Rounded
        set(value) { prefs.edit().putString(KEY_CARD_CORNER_STYLE, value.name).apply() }

    var listDensity: ListDensity
        get() = ListDensity.entries.firstOrNull {
            it.name == prefs.getString(KEY_LIST_DENSITY, null)
        } ?: ListDensity.Comfortable
        set(value) { prefs.edit().putString(KEY_LIST_DENSITY, value.name).apply() }

    var themeProfileId: String?
        get() = prefs.getString(KEY_THEME_PROFILE_ID, null)
        set(value) { prefs.edit().putString(KEY_THEME_PROFILE_ID, value).apply() }

    var customConnectColor: Long
        get() = prefs.getLong(KEY_CUSTOM_CONNECT_COLOR, UNSET_COLOR)
        set(value) { prefs.edit().putLong(KEY_CUSTOM_CONNECT_COLOR, value).apply() }

    var customConnectedColor: Long
        get() = prefs.getLong(KEY_CUSTOM_CONNECTED_COLOR, UNSET_COLOR)
        set(value) { prefs.edit().putLong(KEY_CUSTOM_CONNECTED_COLOR, value).apply() }

    var customServerCardColor: Long
        get() = prefs.getLong(KEY_CUSTOM_SERVER_CARD_COLOR, UNSET_COLOR)
        set(value) { prefs.edit().putLong(KEY_CUSTOM_SERVER_CARD_COLOR, value).apply() }

    var customServerActiveColor: Long
        get() = prefs.getLong(KEY_CUSTOM_SERVER_ACTIVE_COLOR, UNSET_COLOR)
        set(value) { prefs.edit().putLong(KEY_CUSTOM_SERVER_ACTIVE_COLOR, value).apply() }

    var customConfigCardColor: Long
        get() = prefs.getLong(KEY_CUSTOM_CONFIG_CARD_COLOR, UNSET_COLOR)
        set(value) { prefs.edit().putLong(KEY_CUSTOM_CONFIG_CARD_COLOR, value).apply() }

    var customConfigCardTextColor: Long
        get() = prefs.getLong(KEY_CUSTOM_CONFIG_CARD_TEXT_COLOR, UNSET_COLOR)
        set(value) { prefs.edit().putLong(KEY_CUSTOM_CONFIG_CARD_TEXT_COLOR, value).apply() }

    var showServerPing: Boolean
        get() = prefs.getBoolean(KEY_SHOW_SERVER_PING, true)
        set(value) { prefs.edit().putBoolean(KEY_SHOW_SERVER_PING, value).apply() }

    var showServerUsage: Boolean
        get() = prefs.getBoolean(KEY_SHOW_SERVER_USAGE, true)
        set(value) { prefs.edit().putBoolean(KEY_SHOW_SERVER_USAGE, value).apply() }

    var monospaceAddress: Boolean
        get() = prefs.getBoolean(KEY_MONOSPACE_ADDRESS, false)
        set(value) { prefs.edit().putBoolean(KEY_MONOSPACE_ADDRESS, value).apply() }

    var confirmRemove: Boolean
        get() = prefs.getBoolean(KEY_CONFIRM_REMOVE, true)
        set(value) { prefs.edit().putBoolean(KEY_CONFIRM_REMOVE, value).apply() }

    var homeAfterSelect: Boolean
        get() = prefs.getBoolean(KEY_HOME_AFTER_SELECT, false)
        set(value) { prefs.edit().putBoolean(KEY_HOME_AFTER_SELECT, value).apply() }

    var doubleColumnDisplay: Boolean
        get() = prefs.getBoolean(KEY_DOUBLE_COLUMN_DISPLAY, false)
        set(value) { prefs.edit().putBoolean(KEY_DOUBLE_COLUMN_DISPLAY, value).apply() }

    var exitCheckEnabled: Boolean
        get() = prefs.getBoolean(KEY_EXIT_CHECK, true)
        set(value) { prefs.edit().putBoolean(KEY_EXIT_CHECK, value).apply() }

    var latencyProbeUrl: String
        get() =
            prefs.getString(KEY_LATENCY_PROBE_URL, null)?.takeIf { isValidProbeUrl(it) }
                ?: DEFAULT_LATENCY_PROBE_URL
        set(value) {
            val url = value.trim()
            require(isValidProbeUrl(url)) {
                "Latency endpoint must be HTTPS without credentials or fragment"
            }
            prefs.edit().putString(KEY_LATENCY_PROBE_URL, url).apply()
        }

    var mtu: Int
        get() = prefs.getInt(KEY_CUSTOM_MTU, DEFAULT_MTU)
        set(value) {
            prefs.edit().putInt(KEY_CUSTOM_MTU, value.coerceIn(MIN_MTU, MAX_MTU)).apply()
        }

    var cachedRealIp: String?
        get() = prefs.getString(KEY_CACHED_REAL_IP, null)
        set(value) {
            prefs.edit().putString(KEY_CACHED_REAL_IP, value).apply()
        }

    var cachedRealCountry: String?
        get() = prefs.getString(KEY_CACHED_REAL_COUNTRY, null)
        set(value) {
            prefs.edit().putString(KEY_CACHED_REAL_COUNTRY, value).apply()
        }

    var cachedQuotaRemaining: Long?
        get() =
            if (prefs.contains(KEY_CACHED_QUOTA_REMAINING))
                prefs.getLong(KEY_CACHED_QUOTA_REMAINING, -1L).takeIf { it >= 0 }
            else null
        set(value) {
            if (value != null && value >= 0)
                prefs.edit().putLong(KEY_CACHED_QUOTA_REMAINING, value).apply()
            else prefs.edit().remove(KEY_CACHED_QUOTA_REMAINING).apply()
        }

    var cachedQuotaMax: Long?
        get() =
            if (prefs.contains(KEY_CACHED_QUOTA_MAX))
                prefs.getLong(KEY_CACHED_QUOTA_MAX, -1L).takeIf { it >= 0 }
            else null
        set(value) {
            if (value != null && value >= 0)
                prefs.edit().putLong(KEY_CACHED_QUOTA_MAX, value).apply()
            else prefs.edit().remove(KEY_CACHED_QUOTA_MAX).apply()
        }

    var cachedIsUnlimited: Boolean
        get() = prefs.getBoolean(KEY_CACHED_IS_UNLIMITED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_CACHED_IS_UNLIMITED, value).apply()
        }

    fun saveDirectAddress(ip: String, country: String) {
        prefs
            .edit()
            .putString(KEY_CACHED_REAL_IP, ip)
            .putString(KEY_CACHED_REAL_COUNTRY, country)
            .apply()
        mutableAccount.value = accountSnapshot()
    }

    fun saveAccountQuota(
        unlimited: Boolean,
        remaining: Long?,
        maximum: Long?,
        accountId: String? = null,
        fetchedAtEpochSeconds: Long = 0L,
    ) {
        val editor = prefs.edit().putBoolean(KEY_CACHED_IS_UNLIMITED, unlimited)
        if (remaining != null && remaining >= 0) {
            editor.putLong(KEY_CACHED_QUOTA_REMAINING, remaining)
        } else if (unlimited) {
            editor.remove(KEY_CACHED_QUOTA_REMAINING)
        }
        if (maximum != null && maximum >= 0) {
            editor.putLong(KEY_CACHED_QUOTA_MAX, maximum)
        } else if (unlimited) {
            editor.remove(KEY_CACHED_QUOTA_MAX)
        }
        // Identity + freshness of the values above. EVERY caller writes a value
        // it has just received from Guardian, so the fetch time is always "now"
        // unless the caller supplies it. The account id is the caller's if it
        // knows it (HomeViewModel, after its generation check), otherwise the
        // account that is active right now (the VPN service only ever runs with
        // the active session). The stamp is a REFINEMENT for readers — Home
        // never refuses to show the values because of it.
        val stampId = accountId ?: runCatching { activeAccountIdProvider?.invoke() }.getOrNull()
        if (stampId != null) {
            editor.putString(KEY_CACHED_QUOTA_ACCOUNT_ID, stampId)
        }
        editor.putLong(
            KEY_CACHED_QUOTA_FETCHED_AT,
            if (fetchedAtEpochSeconds > 0) fetchedAtEpochSeconds else System.currentTimeMillis() / 1000,
        )
        editor.apply()
        mutableAccount.value = accountSnapshot()
    }

    fun clearAccountSession() {
        prefs
            .edit()
            .remove(KEY_CACHED_REAL_IP)
            .remove(KEY_CACHED_REAL_COUNTRY)
            .remove(KEY_CACHED_QUOTA_REMAINING)
            .remove(KEY_CACHED_QUOTA_MAX)
            .remove(KEY_CACHED_IS_UNLIMITED)
            .remove(KEY_CACHED_QUOTA_ACCOUNT_ID)
            .remove(KEY_CACHED_QUOTA_FETCHED_AT)
            .apply()
        mutableAccount.value = accountSnapshot()
    }

    /** Drops only the cached quota (+stamp); the cached real IP stays. */
    fun clearAccountQuota() {
        prefs
            .edit()
            .remove(KEY_CACHED_QUOTA_REMAINING)
            .remove(KEY_CACHED_QUOTA_MAX)
            .remove(KEY_CACHED_IS_UNLIMITED)
            .remove(KEY_CACHED_QUOTA_ACCOUNT_ID)
            .remove(KEY_CACHED_QUOTA_FETCHED_AT)
            .apply()
        mutableAccount.value = accountSnapshot()
    }

    val socksBindAddress: String
        get() = DEFAULT_SOCKS_BIND_ADDRESS

    val socksPort: Int
        get() =
            if (localProxyAuthEnabled)
                readCredential {
                    credentialsPrefs
                        .getLong(
                            KEY_SOCKS_PORT,
                            prefs.getInt(KEY_SOCKS_PORT, DEFAULT_SOCKS_PORT).toLong(),
                        )
                        .toInt()
                }
            else prefs.getInt(KEY_SOCKS_PORT, DEFAULT_SOCKS_PORT)

    var dnsProvider: DnsProvider
        get() {
            val saved = prefs.getString(KEY_DNS_PROVIDER, null)
            if (saved == "SYSTEM_DEFAULT" || saved == null) return DnsProvider.GOOGLE
            return DnsProvider.entries.firstOrNull { it.name == saved } ?: DnsProvider.GOOGLE
        }
        set(value) {
            prefs.edit().putString(KEY_DNS_PROVIDER, value.name).apply()
        }

    var customDohUrl: String
        get() = prefs.getString(KEY_CUSTOM_DOH_URL, "") ?: ""
        set(value) {
            val trimmed = value.trim()
            if (trimmed.isNotEmpty() && !isValidDohUrl(trimmed)) return
            prefs.edit().putString(KEY_CUSTOM_DOH_URL, trimmed).apply()
        }

    fun saveDns(provider: DnsProvider, customUrl: String) {
        val url = customUrl.trim()
        require(provider != DnsProvider.CUSTOM || isValidDohUrl(url)) {
            "Enter a valid HTTPS DoH URL"
        }
        val savedUrl =
            url.takeIf { it.isEmpty() || isValidDohUrl(it) }
                ?: customDohUrl.takeIf { isValidDohUrl(it) }.orEmpty()
        prefs
            .edit()
            .putString(KEY_DNS_PROVIDER, provider.name)
            .putString(KEY_CUSTOM_DOH_URL, savedUrl)
            .apply()
    }

    val effectiveDohUrl: String
        get() =
            when (dnsProvider) {
                DnsProvider.CUSTOM -> customDohUrl
                DnsProvider.DIRECT_SYSTEM -> ""
                else -> dnsProvider.dohUrl
            }

    var tlsFragmentMode: TlsFragmentMode
        get() =
            TlsFragmentMode.entries.firstOrNull {
                it.name == prefs.getString(KEY_TLS_FRAGMENT_MODE, null)
            } ?: TlsFragmentMode.OFF
        set(value) {
            prefs.edit().putString(KEY_TLS_FRAGMENT_MODE, value.name).apply()
        }

    var fragmentFallbackDelay: Int
        get() = prefs.getInt(KEY_FRAGMENT_FALLBACK_DELAY, DEFAULT_FRAGMENT_FALLBACK_DELAY)
        set(value) {
            prefs.edit().putInt(KEY_FRAGMENT_FALLBACK_DELAY, value.coerceIn(50, 2000)).apply()
        }

    var utlsFingerprint: UtlsFingerprint
        get() =
            UtlsFingerprint.entries.firstOrNull {
                it.name == prefs.getString(KEY_UTLS_FINGERPRINT, null)
            } ?: UtlsFingerprint.FIREFOX
        set(value) {
            prefs.edit().putString(KEY_UTLS_FINGERPRINT, value.name).apply()
        }

    var tcpFastOpen: Boolean
        get() = prefs.getBoolean(KEY_TCP_FAST_OPEN, false)
        set(value) {
            prefs.edit().putBoolean(KEY_TCP_FAST_OPEN, value).apply()
        }

    var maxConcurrentStreams: Int
        get() = prefs.getInt(KEY_MAX_CONCURRENT_STREAMS, DEFAULT_MAX_CONCURRENT_STREAMS)
        set(value) {
            prefs.edit().putInt(KEY_MAX_CONCURRENT_STREAMS, value.coerceIn(2, 128)).apply()
        }

    var ipv6Enabled: Boolean
        get() = prefs.getBoolean(KEY_IPV6_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_IPV6_ENABLED, value).apply()
        }

    var proxyOnlyMode: Boolean
        get() = prefs.getBoolean(KEY_PROXY_ONLY_MODE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_PROXY_ONLY_MODE, value).apply()
        }

    var customEdgeAddress: String
        get() = prefs.getString(KEY_CUSTOM_EDGE_ADDRESS, "") ?: ""
        set(value) {
            val normalized = value.trim().lowercase()
            if (normalized.isNotEmpty() && !isValidEdgeHost(normalized)) return
            prefs.edit().putString(KEY_CUSTOM_EDGE_ADDRESS, normalized).apply()
        }

    val effectiveCustomEdgeAddress: String?
        get() = customEdgeAddress.takeIf { isValidEdgeHost(it) }

    val localProxyAuthEnabled: Boolean
        get() = prefs.getBoolean("local_proxy_auth_enabled", false)

    val localProxyUsername: String
        get() = readCredential { credentialsPrefs.getString("local_proxy_username", "") ?: "" }

    val localProxyPassword: String
        get() = readCredential { credentialsPrefs.getString("local_proxy_password", "") ?: "" }

    val upstreamProxyEnabled: Boolean
        get() = prefs.getBoolean(KEY_UPSTREAM_PROXY_ENABLED, false)

    val upstreamProxyType: UpstreamProxyType
        get() =
            UpstreamProxyType.entries.firstOrNull {
                it.name ==
                    readCredential {
                        credentialsPrefs.getString(
                            KEY_UPSTREAM_PROXY_TYPE,
                            prefs.getString(KEY_UPSTREAM_PROXY_TYPE, null),
                        )
                    }
            } ?: UpstreamProxyType.SOCKS5

    val upstreamProxyHost: String
        get() = readCredential {
            credentialsPrefs
                .getString(KEY_UPSTREAM_PROXY_HOST, prefs.getString(KEY_UPSTREAM_PROXY_HOST, ""))
                .orEmpty()
                .trim()
                .removeSurrounding("[", "]")
        }

    fun saveLocalProxy(options: LocalProxyOptions) =
        synchronized(proxySettingsLock) {
            require(isValidLocalPort(options.port)) {
                "Choose a valid listener port, excluding controller port $CONTROLLER_PORT"
            }
            require(
                !options.authentication ||
                    isValidLocalProxyCredentials(options.username, options.password)
            ) {
                "Enter valid credentials for both HTTP and SOCKS5"
            }
            require(
                !upstreamProxyEnabled ||
                    !isLocalProxyLoop(upstreamProxyHost, upstreamProxyPort, options.port)
            ) {
                "Upstream proxy must not point to this app's listeners"
            }
            readCredential {
                credentialsPrefs
                    .edit()
                    .putString("local_proxy_username", options.username)
                    .putString("local_proxy_password", options.password)
                    .putLong(KEY_SOCKS_PORT, options.port.toLong())
                    .commit()
            }
            check(
                prefs
                    .edit()
                    .putInt(KEY_SOCKS_PORT, options.port)
                    .putBoolean("local_proxy_auth_enabled", options.authentication)
                    .commit()
            ) {
                "Could not persist local proxy settings"
            }
        }

    val upstreamReviewRequired: Boolean
        get() = readCredential {
            credentialsPrefs.getLong(
                "upstream_review_required",
                if (prefs.getBoolean("upstream_proxy_tls", false)) 1 else 0,
            ) == 1L
        }

    fun setUpstreamProxyEnabled(enabled: Boolean) =
        synchronized(proxySettingsLock) {
            check(prefs.edit().putBoolean(KEY_UPSTREAM_PROXY_ENABLED, enabled).commit()) {
                "Could not persist the upstream proxy switch"
            }
        }

    fun disableUpstreamProxy() = setUpstreamProxyEnabled(false)

    fun saveUpstreamProxy(options: UpstreamProxyOptions) =
        synchronized(proxySettingsLock) {
            val host = options.host.trim().removeSurrounding("[", "]")
            require((host.isEmpty() || isValidProxyHost(host)) && options.port in 1..65_535) {
                "Enter a valid proxy host and port"
            }
            require(
                !options.enabled ||
                    isValidUpstreamProxyCredentials(
                        options.type,
                        options.username,
                        options.password,
                    )
            ) {
                "Credentials are not valid for the selected proxy type"
            }
            require(!options.enabled || !isLocalProxyLoop(host, options.port, socksPort)) {
                "Upstream proxy must not point to this app's listeners"
            }
            readCredential {
                credentialsPrefs
                    .edit()
                    .putString(KEY_UPSTREAM_PROXY_USERNAME, options.username)
                    .putString(KEY_UPSTREAM_PROXY_PASSWORD, options.password)
                    .putString(KEY_UPSTREAM_PROXY_TYPE, options.type.name)
                    .putString(KEY_UPSTREAM_PROXY_HOST, host)
                    .putLong(KEY_UPSTREAM_PROXY_PORT, options.port.toLong())
                    .also { if (options.enabled) it.putLong("upstream_review_required", 0) }
                    .commit()
            }
            setUpstreamProxyEnabled(options.enabled)
        }

    val upstreamProxyPort: Int
        get() = readCredential {
            credentialsPrefs
                .getLong(
                    KEY_UPSTREAM_PROXY_PORT,
                    prefs.getInt(KEY_UPSTREAM_PROXY_PORT, DEFAULT_UPSTREAM_PROXY_PORT).toLong(),
                )
                .toInt()
        }

    val upstreamProxyUsername: String
        get() = readCredential { credentialsPrefs.getString(KEY_UPSTREAM_PROXY_USERNAME, "") ?: "" }

    val upstreamProxyPassword: String
        get() = readCredential { credentialsPrefs.getString(KEY_UPSTREAM_PROXY_PASSWORD, "") ?: "" }

    var excludedApps: Set<String>
        get() = prefs.getStringSet(KEY_EXCLUDED_APPS, emptySet())?.toSet() ?: emptySet()
        set(value) {
            prefs.edit().putStringSet(KEY_EXCLUDED_APPS, HashSet(value)).apply()
        }

    enum class SplitTunnelMode {
        EXCLUDE,
        INCLUDE,
    }

    var splitTunnelMode: SplitTunnelMode
        get() =
            SplitTunnelMode.entries.firstOrNull {
                it.name == prefs.getString(KEY_SPLIT_TUNNEL_MODE, null)
            } ?: SplitTunnelMode.EXCLUDE
        set(value) {
            prefs.edit().putString(KEY_SPLIT_TUNNEL_MODE, value.name).apply()
        }

    var includedApps: Set<String>
        get() = prefs.getStringSet(KEY_INCLUDED_APPS, emptySet())?.toSet() ?: emptySet()
        set(value) {
            prefs.edit().putStringSet(KEY_INCLUDED_APPS, HashSet(value)).apply()
        }

    fun saveSplitTunneling(mode: SplitTunnelMode, excluded: Set<String>, included: Set<String>) {
        require(mode != SplitTunnelMode.INCLUDE || included.isNotEmpty()) {
            "Select at least one app to include"
        }
        prefs
            .edit()
            .putString(KEY_SPLIT_TUNNEL_MODE, mode.name)
            .putStringSet(KEY_EXCLUDED_APPS, HashSet(excluded))
            .putStringSet(KEY_INCLUDED_APPS, HashSet(included))
            .apply()
    }

    enum class HttpVersion(val versionNumber: Int, val label: String, val description: String) {
        HTTP_2(versionNumber = 2, label = "HTTP/2", description = "Multiplexed streams over TCP"),
        HTTP_3(versionNumber = 3, label = "HTTP/3", description = "QUIC transport over UDP"),
    }

    var httpVersion: HttpVersion
        get() =
            HttpVersion.entries.firstOrNull { it.name == prefs.getString(KEY_HTTP_VERSION, null) }
                ?: HttpVersion.HTTP_2
        set(value) {
            prefs.edit().putString(KEY_HTTP_VERSION, value.name).apply()
        }

    enum class UpstreamProxyType {
        SOCKS5,
        HTTP,
    }

    enum class TlsFragmentMode(val label: String, val description: String) {
        OFF(label = "Disabled", description = "Standard TLS"),
        RECORD(label = "Record", description = "TLS record layer"),
        PACKET(label = "Packet", description = "TCP segment layer"),
        COMBINED(label = "Combined", description = "Record and packet"),
    }

    enum class UtlsFingerprint(val label: String, val identifier: String) {
        FIREFOX("Firefox", "firefox"),
        CHROME("Chrome", "chrome"),
        SAFARI("Safari", "safari"),
        IOS("iOS", "ios"),
        EDGE("Edge", "edge"),
        RANDOMIZED("Randomized", "randomized"),
    }

    enum class DnsProvider(val label: String, val dohUrl: String) {
        DIRECT_SYSTEM(label = "System DNS", dohUrl = ""),
        GOOGLE(label = "Google", dohUrl = "https://8.8.8.8/dns-query"),
        CLOUDFLARE(label = "Cloudflare", dohUrl = "https://1.1.1.1/dns-query"),
        QUAD9(label = "Quad9", dohUrl = "https://9.9.9.9/dns-query"),
        CUSTOM(label = "Custom DoH", dohUrl = "");

        val isDirect: Boolean
            get() = this == DIRECT_SYSTEM
    }

    // ------------------------------------------------------------------
    // Fork block: control-plane access fallbacks. These affect only the
    // account/API requests (Firefox Accounts, Guardian, the server
    // list) — never the VPN tunnel itself, and never the data-plane
    // upstream proxy above (upstreamProxy*, which chains the tunnel
    // through another proxy). All default off; with everything off the
    // control plane behaves exactly as upstream's.
    // ------------------------------------------------------------------

    /**
     * Control-plane TLS split mode (the fork's third fallback): splits
     * the TLS ClientHello of account/API requests. Default OFF; the
     * tunnel itself is never affected.
     */
    var tlsSplitMode: TlsSplitMode
        get() = TlsSplitMode.parse(prefs.getString(KEY_TLS_SPLIT_MODE, null))
        set(value) { prefs.edit().putString(KEY_TLS_SPLIT_MODE, value.name).apply() }

    /** SOCKS5 proxy for control-plane requests only (the account proxy). */
    var apiProxyEnabled: Boolean
        get() = prefs.getBoolean(KEY_API_PROXY_ENABLED, false)
        set(value) { prefs.edit().putBoolean(KEY_API_PROXY_ENABLED, value).apply() }

    var apiProxyHost: String
        get() = prefs.getString(KEY_API_PROXY_HOST, "") ?: ""
        set(value) { prefs.edit().putString(KEY_API_PROXY_HOST, cleanProxyHost(value)).apply() }

    var apiProxyPort: Int
        get() = prefs.getInt(KEY_API_PROXY_PORT, DEFAULT_API_PROXY_PORT)
        set(value) { prefs.edit().putInt(KEY_API_PROXY_PORT, value.coerceIn(1, 65_535)).apply() }

    var apiProxyUsername: String
        get() = readCredential { credentialsPrefs.getString(KEY_API_PROXY_USERNAME, "") ?: "" }
        set(value) {
            credentialsPrefs.edit().putString(KEY_API_PROXY_USERNAME, value.trim()).commit()
        }

    var apiProxyPassword: String
        get() = readCredential { credentialsPrefs.getString(KEY_API_PROXY_PASSWORD, "") ?: "" }
        set(value) {
            credentialsPrefs.edit().putString(KEY_API_PROXY_PASSWORD, value.trim()).commit()
        }

    /** The account proxy to use, or null when off or not validly configured. */
    val effectiveApiProxy: ApiProxySettings?
        get() {
            if (!apiProxyEnabled) return null
            val host = cleanProxyHost(apiProxyHost)
            if (!isValidProxyHost(host) || !isValidProxyPort(apiProxyPort)) return null
            val username = apiProxyUsername
            val password = apiProxyPassword
            if (proxyCredentialsError(username, password) != null) return null
            return ApiProxySettings(host, apiProxyPort, username, password)
        }

    /** API relay for control-plane requests (direct first, relay on failure). */
    var apiRelayEnabled: Boolean
        get() = prefs.getBoolean(KEY_API_RELAY_ENABLED, false)
        set(value) { prefs.edit().putBoolean(KEY_API_RELAY_ENABLED, value).apply() }

    var apiRelayUrl: String
        get() = prefs.getString(KEY_API_RELAY_URL, "") ?: ""
        set(value) { prefs.edit().putString(KEY_API_RELAY_URL, value.trim()).apply() }

    /** The normalized relay base URL, or null when off or not a valid https URL. */
    val effectiveApiRelayUrl: String?
        get() = if (apiRelayEnabled) normalizeRelayUrl(apiRelayUrl) else null

    private val controlPlaneListeners =
        mutableListOf<android.content.SharedPreferences.OnSharedPreferenceChangeListener>()

    /**
     * Invokes [listener] whenever a control-plane network setting kept in
     * the plain prefs (account proxy switch/address, API relay) changes,
     * so pooled connections and relay state built on the old values can
     * be dropped. The proxy credentials live in the secure prefs, which
     * expose no change listener; the credentials dialog calls
     * ControlPlaneHttp.onSettingsChanged() itself after saving.
     */
    fun addControlPlaneSettingsListener(listener: () -> Unit) {
        val keys = setOf(
            KEY_TLS_SPLIT_MODE,
            KEY_API_PROXY_ENABLED, KEY_API_PROXY_HOST, KEY_API_PROXY_PORT,
            KEY_API_RELAY_ENABLED, KEY_API_RELAY_URL,
        )
        val delegate =
            android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key in keys) listener()
            }
        synchronized(controlPlaneListeners) { controlPlaneListeners += delegate }
        prefs.registerOnSharedPreferenceChangeListener(delegate)
    }

    data class ApiProxySettings(
        val host: String,
        val port: Int,
        val username: String,
        val password: String,
    )

    enum class AppDesign(val label: String) {
        PIXEL(label = "Pixel"),
        PORTAL(label = "Portal"),
        OLD(label = "Classic"),
    }

    enum class TlsSplitMode(
        val label: String,
        val description: String,
    ) {
        OFF(
            label = "Disabled",
            description = "Standard TLS handshake without fragmentation",
        ),
        FIRST_BYTE(
            label = "First byte",
            description = "Splits TLS record header at byte 0 (0x16)",
        ),
        SNI(
            label = "SNI hostname",
            description = "Fragments the SNI extension across packets (defeats DPI)",
        ),
        ;

        companion object {
            /** Stored value → mode; anything unknown (or unset) is OFF. */
            fun parse(raw: String?): TlsSplitMode =
                entries.firstOrNull { it.name == raw } ?: OFF
        }
    }

    companion object {
        const val DEFAULT_LATENCY_PROBE_URL = "https://www.gstatic.com/generate_204"
        private const val KEY_LATENCY_PROBE_URL = "latency_probe_url"

        // Fork block keys (control-plane fallbacks; see the class body).
        // Fork appearance keys (v1 fork's Interface layer, restored in v1).
        private const val KEY_APP_DESIGN = "app_design"
        private const val KEY_COLOR_STYLE = "color_style"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_ACCENT_COLOR_INDEX = "accent_color_index"
        private const val KEY_HAPTIC_FEEDBACK = "haptic_feedback"
        private const val KEY_COLOR_CONTRAST = "color_contrast"
        private const val KEY_REDUCE_MOTION = "reduce_motion"
        private const val KEY_UI_FONT_SCALE = "ui_font_scale"
        private const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_SCREEN_TRANSITION = "screen_transition"
        private const val KEY_NAV_BAR_STYLE = "nav_bar_style"
        private const val KEY_CONNECT_BUTTON_STYLE = "connect_button_style"
        private const val KEY_CARD_CORNER_STYLE = "card_corner_style"
        private const val KEY_LIST_DENSITY = "list_density"
        private const val KEY_THEME_PROFILE_ID = "theme_profile_id"
        private const val KEY_CUSTOM_CONNECT_COLOR = "custom_connect_color"
        private const val KEY_CUSTOM_CONNECTED_COLOR = "custom_connected_color"
        private const val KEY_CUSTOM_SERVER_CARD_COLOR = "custom_server_card_color"
        private const val KEY_CUSTOM_SERVER_ACTIVE_COLOR = "custom_server_active_color"
        private const val KEY_CUSTOM_CONFIG_CARD_COLOR = "custom_config_card_color"
        private const val KEY_CUSTOM_CONFIG_CARD_TEXT_COLOR = "custom_config_card_text_color"
        private const val KEY_SHOW_SERVER_PING = "show_server_ping"
        private const val KEY_SHOW_SERVER_USAGE = "show_server_usage"
        private const val KEY_MONOSPACE_ADDRESS = "monospace_address"
        private const val KEY_CONFIRM_REMOVE = "confirm_remove"
        private const val KEY_HOME_AFTER_SELECT = "home_after_select"
        private const val KEY_DOUBLE_COLUMN_DISPLAY = "double_column_display"
        private const val KEY_EXIT_CHECK = "exit_check_enabled"
        const val UNSET_COLOR: Long = Long.MIN_VALUE
        private const val KEY_TLS_SPLIT_MODE = "tls_split_mode"
        private const val KEY_API_PROXY_ENABLED = "api_proxy_enabled"
        private const val KEY_API_PROXY_HOST = "api_proxy_host"
        private const val KEY_API_PROXY_PORT = "api_proxy_port"
        private const val KEY_API_PROXY_USERNAME = "api_proxy_username"
        private const val KEY_API_PROXY_PASSWORD = "api_proxy_password"
        private const val KEY_API_RELAY_ENABLED = "api_relay_enabled"
        private const val KEY_API_RELAY_URL = "api_relay_url"
        const val DEFAULT_API_PROXY_PORT = 1080

        /**
         * A proxy address as typed or pasted, without the spaces and the
         * scheme that may come with it (mirrors the desktop client's
         * cleaning of its upstream proxy address).
         */
        fun cleanProxyHost(raw: String): String {
            var address = raw.filterNot { it.isWhitespace() }
            val schemeEnd = address.indexOf("://")
            if (schemeEnd >= 0) address = address.substring(schemeEnd + 3)
            return address.trimEnd('/')
        }

        fun isValidProxyPort(port: Int): Boolean = port in 1..65_535

        /**
         * SOCKS5 credentials are both or neither, and at most 255 bytes
         * each. Returns a short problem description, or null when valid.
         */
        fun proxyCredentialsError(username: String, password: String): String? {
            if (username.isEmpty() != password.isEmpty()) {
                return "The username and the password go together"
            }
            if (username.toByteArray(Charsets.UTF_8).size > 255 ||
                password.toByteArray(Charsets.UTF_8).size > 255
            ) {
                return "SOCKS5 takes no username or password longer than 255 bytes"
            }
            return null
        }

        /**
         * An API relay URL, normalized: empty when unset is NOT valid here
         * (callers check blank separately); otherwise it must be an
         * https:// URL with a host — plain http is refused because the
         * sign-in and the account tokens cross the relay. Returns the
         * canonical URL string, or null when invalid.
         */
        fun normalizeRelayUrl(raw: String): String? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            val url = trimmed.toHttpUrlOrNull() ?: return null
            if (url.scheme != "https" || url.host.isEmpty()) return null
            return url.toString()
        }

        fun isValidRelayUrl(raw: String): Boolean = normalizeRelayUrl(raw) != null

        fun isValidProbeUrl(value: String): Boolean =
            runCatching {
                    val uri = java.net.URI(value)
                    uri.scheme.equals("https", ignoreCase = true) &&
                        !uri.host.isNullOrBlank() &&
                        uri.rawUserInfo == null &&
                        uri.rawFragment == null &&
                        (uri.port == -1 || uri.port in 1..65_535)
                }
                .getOrDefault(false)

        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_CUSTOM_MTU = "custom_mtu"
        private const val KEY_CACHED_REAL_IP = "cached_real_ip"
        private const val KEY_CACHED_REAL_COUNTRY = "cached_real_country"
        private const val KEY_CACHED_QUOTA_REMAINING = "cached_quota_remaining"
        private const val KEY_CACHED_QUOTA_MAX = "cached_quota_max"
        private const val KEY_CACHED_IS_UNLIMITED = "cached_is_unlimited"
        private const val KEY_CACHED_QUOTA_ACCOUNT_ID = "cached_quota_account_id"
        private const val KEY_CACHED_QUOTA_FETCHED_AT = "cached_quota_fetched_at"
        const val DEFAULT_MTU = 1280
        const val MIN_MTU = 576
        const val MAX_MTU = 9000
        private const val KEY_SOCKS_PORT = "socks_port"
        private const val KEY_DNS_PROVIDER = "dns_provider"
        private const val KEY_CUSTOM_DOH_URL = "custom_doh_url"
        private const val KEY_TLS_FRAGMENT_MODE = "tls_fragment_mode"
        private const val KEY_FRAGMENT_FALLBACK_DELAY = "fragment_fallback_delay"
        private const val KEY_UTLS_FINGERPRINT = "utls_fingerprint"
        private const val KEY_TCP_FAST_OPEN = "tcp_fast_open"
        private const val KEY_MAX_CONCURRENT_STREAMS = "max_concurrent_streams"
        private const val KEY_IPV6_ENABLED = "ipv6_enabled"
        const val DEFAULT_FRAGMENT_FALLBACK_DELAY = 300
        const val DEFAULT_MAX_CONCURRENT_STREAMS = 32
        private const val KEY_PROXY_ONLY_MODE = "proxy_only_mode"
        private const val KEY_CUSTOM_EDGE_ADDRESS = "custom_edge_address"
        private const val KEY_UPSTREAM_PROXY_ENABLED = "upstream_proxy_enabled"
        private const val KEY_UPSTREAM_PROXY_TYPE = "upstream_proxy_type"
        private const val KEY_UPSTREAM_PROXY_HOST = "upstream_proxy_host"
        private const val KEY_UPSTREAM_PROXY_PORT = "upstream_proxy_port"
        private const val KEY_UPSTREAM_PROXY_USERNAME = "upstream_proxy_username"
        private const val KEY_UPSTREAM_PROXY_PASSWORD = "upstream_proxy_password"
        private const val KEY_EXCLUDED_APPS = "excluded_apps"
        private const val KEY_INCLUDED_APPS = "included_apps"
        private const val KEY_SPLIT_TUNNEL_MODE = "split_tunnel_mode"
        private const val KEY_HTTP_VERSION = "http_version"
        const val CONTROLLER_PORT = 9090
        const val DEFAULT_SOCKS_BIND_ADDRESS = "127.0.0.1"
        const val DEFAULT_SOCKS_PORT = 1080
        const val DEFAULT_UPSTREAM_PROXY_PORT = 1080

        fun isValidUpstreamProxyCredentials(
            type: UpstreamProxyType,
            username: String,
            password: String,
        ): Boolean {
            if (username.isEmpty() && password.isEmpty()) return true
            if (
                username.isBlank() ||
                    username.any { it.isISOControl() } ||
                    password.any { it.isISOControl() }
            )
                return false
            return when (type) {
                UpstreamProxyType.SOCKS5 ->
                    username.toByteArray(Charsets.UTF_8).size in 1..255 &&
                        password.toByteArray(Charsets.UTF_8).size in 1..255
                UpstreamProxyType.HTTP ->
                    ':' !in username &&
                        username.toByteArray(Charsets.UTF_8).size <= 255 &&
                        password.toByteArray(Charsets.UTF_8).size <= 255
            }
        }

        fun isLocalProxyLoop(host: String, port: Int, mixedPort: Int): Boolean {
            val normalized = host.trim().removeSurrounding("[", "]").removeSuffix(".").lowercase()
            val loopback =
                normalized == "localhost" ||
                    normalized == "::1" ||
                    normalized == "::ffff:127.0.0.1" ||
                    (isValidIpAddress(normalized) &&
                        runCatching { java.net.InetAddress.getByName(normalized).isLoopbackAddress }
                            .getOrDefault(false))
            return loopback && (port == mixedPort || port == CONTROLLER_PORT)
        }

        fun isValidLocalProxyCredentials(username: String, password: String): Boolean =
            username.isNotBlank() &&
                password.isNotBlank() &&
                ':' !in username &&
                username.none { it.isISOControl() } &&
                password.none { it.isISOControl() } &&
                username.toByteArray(Charsets.UTF_8).size in 1..255 &&
                password.toByteArray(Charsets.UTF_8).size in 1..255

        fun isValidDohUrl(value: String): Boolean {
            val trimmed = value.trim()
            if (!trimmed.startsWith("https://", ignoreCase = true)) return false
            return runCatching {
                    val uri = java.net.URI.create(trimmed)
                    !uri.host.isNullOrBlank() &&
                        uri.rawUserInfo == null &&
                        uri.rawFragment == null &&
                        uri.rawQuery == null &&
                        (uri.port == -1 || uri.port in 1..65_535)
                }
                .getOrDefault(false)
        }

        fun isValidIpAddress(value: String): Boolean {
            if (value.isEmpty()) return false
            if (':' !in value) {
                val octets = value.split('.')
                return octets.size == 4 &&
                    octets.all { octet ->
                        octet.isNotEmpty() &&
                            octet.length <= 3 &&
                            octet.all { it in '0'..'9' } &&
                            (octet.length == 1 || octet.first() != '0') &&
                            octet.toInt() in 0..255
                    }
            }
            if (value.any { it !in "0123456789abcdefABCDEF:." }) return false
            return runCatching {
                    java.net.URI("http://[$value]/").host != null &&
                        java.net.InetAddress.getByName(value) != null
                }
                .getOrDefault(false)
        }

        fun isValidHostname(value: String): Boolean {
            val host = value.trim().removeSuffix(".")
            if (host.isEmpty() || host.length > 253) return false

            if (host.contains(':')) return false
            if (host.all { it.isDigit() || it == '.' }) return false
            val labels = host.split('.')
            if (labels.size < 2) return false
            return labels.all { label ->
                label.isNotEmpty() &&
                    label.length <= 63 &&
                    !label.startsWith('-') &&
                    !label.endsWith('-') &&
                    label.all { char ->
                        char == '-' || (char.code < 128 && (char.isLetterOrDigit()))
                    }
            }
        }

        fun isValidProxyHost(value: String): Boolean {
            val host = value.trim().removeSurrounding("[", "]").removeSuffix(".")
            if (isValidIpAddress(host) || isValidHostname(host)) return true

            return host.length in 1..63 &&
                host.any { it.isLetter() } &&
                !host.startsWith('-') &&
                !host.endsWith('-') &&
                host.all { it == '-' || it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }
        }

        fun isValidLocalPort(port: Int): Boolean = port in 1..65_535 && port != CONTROLLER_PORT

        fun isValidEdgeHost(value: String): Boolean {
            val host = value.trim().removeSuffix(".")
            return isValidHostname(host) || isValidIpAddress(host)
        }
    }
}
