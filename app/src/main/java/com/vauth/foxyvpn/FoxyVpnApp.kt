package com.vauth.foxyvpn

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.vauth.foxyvpn.data.AccountRepository
import com.vauth.foxyvpn.data.AccountStore
import com.vauth.foxyvpn.data.AppLogger
import com.vauth.foxyvpn.data.ControlPlaneHttp
import com.vauth.foxyvpn.data.CrashReporter
import com.vauth.foxyvpn.data.FxaAuthRepository
import com.vauth.foxyvpn.data.GuardianClient
import com.vauth.foxyvpn.data.ProxyStateStore
import com.vauth.foxyvpn.data.ServerListClient
import com.vauth.foxyvpn.data.SettingsStore
import com.vauth.foxyvpn.data.TokenStore
import com.vauth.foxyvpn.data.UpgradeSanitizer
import com.vauth.foxyvpn.update.AppUpdater
import com.vauth.foxyvpn.vpn.FoxyVpnService
import com.vauth.foxyvpn.widget.QuickConnectWidgetProvider

interface AppContainer {
    val tokenStore: TokenStore
    val proxyStateStore: ProxyStateStore
    val settingsStore: SettingsStore
    val guardianClient: GuardianClient
    val serverListClient: ServerListClient
    val authRepository: FxaAuthRepository
    // Fork additions: the multi-account layer beside the session store.
    val accountStore: AccountStore
    val accountRepository: AccountRepository
}

class FoxyVpnApp : Application(), AppContainer {

    internal lateinit var appUpdater: AppUpdater
    override lateinit var tokenStore: TokenStore
        internal set
    override lateinit var proxyStateStore: ProxyStateStore
        internal set
    override lateinit var settingsStore: SettingsStore
        internal set
    override lateinit var guardianClient: GuardianClient
        internal set
    override lateinit var serverListClient: ServerListClient
        internal set
    override lateinit var authRepository: FxaAuthRepository
        internal set
    override lateinit var accountStore: AccountStore
        internal set
    override lateinit var accountRepository: AccountRepository
        internal set

    override fun onCreate() {
        super.onCreate()

        CrashReporter.install(this)
        CrashReporter.replayLastCrashIfAny(this)

        // D1: one-time upgrade sanitization of persisted settings. MUST stay
        // the first thing that touches settings: before SettingsStore exists,
        // before the control-plane hooks can read the anti-block trio, and
        // before any service/connect can run (Application.onCreate precedes
        // every component in this process). A failure here must never block
        // startup — it is logged and retried on the next start.
        runCatching { UpgradeSanitizer.runOnce(this) }
            .onFailure { AppLogger.w("FoxyVpnApp", "upgrade sanitization failed", it) }

        appUpdater = AppUpdater(this)
        tokenStore = TokenStore(this)
        proxyStateStore = ProxyStateStore(this)
        settingsStore = SettingsStore(this)
        guardianClient = GuardianClient()
        serverListClient = ServerListClient(this)
        accountStore = AccountStore(this)
        // D2: unattributed quota writes (the byte-identical VPN service) are
        // stamped with the account that is active when they land.
        settingsStore.activeAccountIdProvider = { accountStore.activeAccountIdFlow.value }
        authRepository = FxaAuthRepository(tokenStore)
        accountRepository = AccountRepository(accountStore, tokenStore)
        accountRepository.migrateAndReconcile()
        installControlPlaneHooks()
        createNotificationChannel()
    }

    /**
     * Fork wiring for the control-plane fallbacks: the proxy/relay
     * providers read the live settings, plain-prefs changes invalidate
     * the cached state, and a change of the underlying (non-VPN)
     * network drops the relay stickiness — the v1.1.4 design, carried
     * unchanged.
     */
    private fun installControlPlaneHooks() {
        ControlPlaneHttp.apiProxyProvider = { settingsStore.effectiveApiProxy }
        ControlPlaneHttp.apiRelayProvider = { settingsStore.effectiveApiRelayUrl }
        ControlPlaneHttp.tlsSplitModeProvider = { settingsStore.tlsSplitMode }
        settingsStore.addControlPlaneSettingsListener {
            ControlPlaneHttp.onSettingsChanged()
        }
        val connectivity = getSystemService(ConnectivityManager::class.java) ?: return
        runCatching {
            connectivity.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .build(),
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) =
                        ControlPlaneHttp.onNetworkChanged()

                    override fun onLost(network: Network) =
                        ControlPlaneHttp.onNetworkChanged()
                },
            )
        }
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                VPN_NOTIFICATION_CHANNEL_ID,
                "VPN status",
                NotificationManager.IMPORTANCE_LOW,
            )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        QuickConnectWidgetProvider.updateAll(this, FoxyVpnService.state.value)
    }

    companion object {
        const val VPN_NOTIFICATION_CHANNEL_ID = "foxyvpn_status"
    }
}
