package com.vauth.foxyvpn.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vauth.foxyvpn.AppContainer
import com.vauth.foxyvpn.data.ServerListClient
import com.vauth.foxyvpn.data.model.ConnectionState
import com.vauth.foxyvpn.data.model.displayName
import com.vauth.foxyvpn.ui.HomeViewModel
import com.vauth.foxyvpn.ui.QuotaCaption
import com.vauth.foxyvpn.ui.ServerListViewModel
import com.vauth.foxyvpn.ui.bento.BentoActiveServer
import com.vauth.foxyvpn.ui.bento.BentoConnectState
import com.vauth.foxyvpn.ui.bento.BentoHome
import com.vauth.foxyvpn.ui.bento.BentoServerPick
import com.vauth.foxyvpn.ui.bento.FoxyDestination
import com.vauth.foxyvpn.ui.bento.FoxyNavBar
import com.vauth.foxyvpn.ui.bento.HomeQuotaUi
import com.vauth.foxyvpn.ui.bento.LocalBentoHaptics
import com.vauth.foxyvpn.ui.bento.decodeNavDestinations
import com.vauth.foxyvpn.ui.formatQuotaAge
import com.vauth.foxyvpn.ui.l10n.s
import com.vauth.foxyvpn.ui.quotaCaption
import com.vauth.foxyvpn.ui.resolveHomeQuota
import com.vauth.foxyvpn.ui.theme.AppearanceController
import com.vauth.foxyvpn.ui.theme.MotionBudget
import com.vauth.foxyvpn.ui.theme.LocalMotionBudget
import com.vauth.foxyvpn.ui.theme.toPersonalization
import com.vauth.foxyvpn.vpn.FoxyVpnService
import com.vauth.foxyvpn.vpn.PingUtil
import com.vauth.foxyvpn.vpn.TunnelHealth
import java.text.SimpleDateFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.Date
import java.util.Locale

/**
 * Home — Phase B1 of the Zed overhaul.
 *
 * The seam: this composable collects the service/account/server flows
 * and builds ONE immutable [com.vauth.foxyvpn.ui.bento.HomeUiState];
 * [BentoHome] renders it. Home observes the tunnel, the selected
 * server and the account — it never owns any of them, and the connect
 * path (MainActivity's requestConnect / service start-stop) is
 * untouched. Phase B2: all three Zed designs render the same state —
 * Bento (B1), Pixel and Old (ui.designs) — dispatched on
 * [AppearanceController.appDesign] below.
 */
@Composable
fun HomeScreen(
    container: AppContainer,
    appearance: AppearanceController,
    homeModel: HomeViewModel,
    onRequestConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenServers: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenSpeedTest: () -> Unit,
    onOpenMap: () -> Unit,
    onSecretUnlocked: () -> Unit,
) {
    val state by FoxyVpnService.state.collectAsState()
    val lastError by FoxyVpnService.lastError.collectAsState()
    val tunnelHealth by FoxyVpnService.tunnelHealth.collectAsState()
    val accounts by container.accountStore.accountsFlow.collectAsState()
    val activeAccountId by container.accountStore.activeAccountIdFlow.collectAsState()
    val selectedProxy by container.proxyStateStore.selectedProxyFlow.collectAsState()
    val accountSnapshot by container.settingsStore.account.collectAsState()

    // Quota freshness: refresh the active account's entitlement when
    // Home appears and whenever a connection lands (v1-fix behavior).
    LaunchedEffect(Unit) { homeModel.refreshAccount() }
    LaunchedEffect(state) {
        if (state == ConnectionState.CONNECTED) homeModel.refreshAccount()
    }
    val activeAccount = accounts.firstOrNull { it.id == activeAccountId }
    val resolvedQuota = resolveHomeQuota(
        snapshot = accountSnapshot,
        activeAccount = activeAccount,
        knownAccountIds = accounts.map { it.id }.toSet(),
        usedBytesOfActive = activeAccount?.let { container.accountStore.getUsedBytes(it.id) } ?: 0L,
    )

    // Fastest-servers picks from the shared server directory.
    val serverListModel: ServerListViewModel = viewModel(
        factory = ServerListViewModel.factory(
            serverListClient = container.serverListClient,
            proxyStateStore = container.proxyStateStore,
        ),
    )
    val serverState by serverListModel.state.collectAsState()
    LaunchedEffect(serverState.countries, serverState.directoryRevision) {
        for (country in serverState.countries) {
            val candidates = ServerListClient.candidatesForCountry(serverState.countries, country.code)
            val first = candidates.firstOrNull() ?: continue
            serverListModel.probeCityLatency(
                country.code,
                first.cityCode,
                ServerListClient.candidatesForCity(serverState.countries, country.code, first.cityCode),
            )
        }
    }
    // B2 smoothness: probe results stream into the view-model state one
    // country at a time (~35 updates in the first seconds on Home).
    // Feeding them straight into `picks` rebuilt the whole Home state —
    // and recomposed the entire Home surface — per result. Publish them
    // throttled instead: the collector conflates bursts and republishes
    // at most every 600 ms while results stream; the final value always
    // lands. The picks list (and Home) now settles a handful of times,
    // not once per country.
    var publishedPings by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    LaunchedEffect(serverListModel) {
        serverListModel.state
            .map { it.pingResults }
            .distinctUntilChanged()
            .collect { results ->
                publishedPings = results
                delay(600)
            }
    }
    val picks = remember(serverState.countries, publishedPings) {
        serverState.countries.mapNotNull { country ->
            val candidates = ServerListClient.candidatesForCountry(serverState.countries, country.code)
            val first = candidates.firstOrNull() ?: return@mapNotNull null
            val ping = publishedPings["${country.code}:${first.cityCode}"]
            BentoServerPick(
                id = "${country.code}:${first.cityCode}",
                name = country.displayName,
                detail = first.displayName,
                country = country.code,
                pingMs = ping,
            ) to candidates
        }.sortedWith(
            compareByDescending<Pair<BentoServerPick, *>> { it.first.pingMs != null }
                .thenBy { it.first.pingMs ?: Int.MAX_VALUE },
        ).take(5)
    }
    val pickCandidates = remember(picks) { picks.associate { it.first.id to it.second } }
    val pickList = picks.map { it.first }

    // Selected-server latency for the server card (measured on connect
    // and on demand; the old home measured it inside its details panel).
    var pingMs by remember { mutableIntStateOf(-1) }
    var pingNonce by remember { mutableIntStateOf(0) }
    LaunchedEffect(state, selectedProxy, pingNonce) {
        if (state == ConnectionState.CONNECTED) {
            val proxy = selectedProxy
            pingMs = -1
            pingMs = if (proxy != null) {
                PingUtil.measureTcpLatencyMs(proxy.host, proxy.port) ?: -2
            } else {
                -2
            }
        }
    }

    val connectState = when (state) {
        ConnectionState.CONNECTED -> BentoConnectState.Connected
        ConnectionState.CONNECTING -> BentoConnectState.Connecting
        ConnectionState.DISCONNECTED ->
            if (lastError != null) BentoConnectState.Error else BentoConnectState.Idle
    }

    val faultTitle: String?
    val faultHint: String?
    when (tunnelHealth) {
        TunnelHealth.UNREACHABLE -> {
            faultTitle = s("No internet through the tunnel")
            faultHint = s("The tunnel is up but traffic is not passing. Try another server.")
        }
        TunnelHealth.NO_NETWORK -> {
            faultTitle = s("No network")
            faultHint = s("This device has no internet connection right now.")
        }
        else -> {
            faultTitle = null
            faultHint = null
        }
    }

    val quotaUi = buildQuotaUi(
        title = activeAccount?.email ?: s("Monthly quota"),
        remaining = resolvedQuota.remaining,
        max = resolvedQuota.max,
        unlimited = resolvedQuota.unlimited,
        fetchedAtEpochSeconds = resolvedQuota.fetchedAtEpochSeconds,
        isEstimate = resolvedQuota.isEstimate,
    )

    val settings = container.settingsStore
    val ui = com.vauth.foxyvpn.ui.bento.HomeUiState(
        connectState = connectState,
        error = lastError,
        faultTitle = faultTitle,
        faultHint = faultHint,
        active = selectedProxy?.let { proxy ->
            BentoActiveServer(
                name = proxy.displayName,
                detail = proxy.authority,
                country = proxy.countryCode,
                engine = "MASQUE",
                locked = false,
            )
        },
        quota = quotaUi,
        picks = pickList,
        lockedNote = null,
        localProxyAddress = if (settings.proxyOnlyMode) {
            "${settings.socksBindAddress}:${settings.socksPort}"
        } else {
            null
        },
        pingMs = if (pingMs == -1) null else pingMs,
    )

    val budget = LocalMotionBudget.current
    val animate = budget == MotionBudget.Full ||
        (budget == MotionBudget.Throttled && state == ConnectionState.CONNECTING)

    val haptics = LocalHapticFeedback.current
    // Egg overlays (DESIGN-SYSTEM §11.1): the title 3-tap burst and the
    // status 3-tap laser live at this level, above whichever design is
    // showing — exactly where Zed's HomeScreen hosts them.
    var burst by remember { mutableIntStateOf(0) }
    var laser by remember { mutableIntStateOf(0) }
    val personalization = container.settingsStore.toPersonalization()
    val dockContent: @Composable () -> Unit = {
        FoxyNavBar(
            style = appearance.bento.navStyle,
            destinations = decodeNavDestinations(appearance.navDestinations),
            current = FoxyDestination.Home,
            onNavigate = { dest ->
                when (dest) {
                    FoxyDestination.Home -> Unit
                    FoxyDestination.Servers -> onOpenServers()
                    FoxyDestination.Accounts -> onOpenAccounts()
                    FoxyDestination.Settings -> onOpenSettings()
                }
            },
        )
    }
    val toggleConnection = {
        if (state == ConnectionState.DISCONNECTED) onRequestConnect() else onDisconnect()
    }
    val refreshInfo = {
        pingNonce++
        homeModel.refreshAccount()
    }
    CompositionLocalProvider(
        LocalBentoHaptics provides { type: HapticFeedbackType ->
            if (appearance.hapticFeedback) haptics.performHapticFeedback(type)
        },
    ) {
        Box {
            when (appearance.appDesign) {
                com.vauth.foxyvpn.data.SettingsStore.AppDesign.BENTO -> BentoHome(
                    ui = ui,
                    animate = animate,
                    contentPadding = PaddingValues(),
                    dockAutoHide = appearance.bento.dockAutoHide,
                    onToggleConnection = toggleConnection,
                    onBrowseServers = onOpenServers,
                    onPickServer = { id ->
                        pickCandidates[id]?.firstOrNull()?.let { candidate ->
                            serverListModel.selectCandidate(candidate)
                        }
                    },
                    onOpenAccounts = onOpenAccounts,
                    onOpenSpeedTest = onOpenSpeedTest,
                    onOpenMap = onOpenMap,
                    onRefreshInfo = refreshInfo,
                    onSecretUnlocked = onSecretUnlocked,
                    onShowLogs = onOpenLogs,
                    onBurst = { burst++ },
                    dock = dockContent,
                )
                com.vauth.foxyvpn.data.SettingsStore.AppDesign.PIXEL ->
                    com.vauth.foxyvpn.ui.designs.PixelHome(
                        ui = ui,
                        animate = animate,
                        appearance = appearance,
                        personalization = personalization,
                        contentPadding = PaddingValues(),
                        onToggleConnection = toggleConnection,
                        onBrowseServers = onOpenServers,
                        onOpenAccounts = onOpenAccounts,
                        onOpenSpeedTest = onOpenSpeedTest,
                        onOpenMap = onOpenMap,
                        onRefreshInfo = refreshInfo,
                        onSecretUnlocked = onSecretUnlocked,
                        onShowLogs = onOpenLogs,
                        onBurst = { burst++ },
                        onLaser = { laser++ },
                        dock = dockContent,
                    )
                com.vauth.foxyvpn.data.SettingsStore.AppDesign.OLD ->
                    com.vauth.foxyvpn.ui.designs.OldHome(
                        ui = ui,
                        animate = animate,
                        appearance = appearance,
                        personalization = personalization,
                        contentPadding = PaddingValues(),
                        onToggleConnection = toggleConnection,
                        onBrowseServers = onOpenServers,
                        onOpenAccounts = onOpenAccounts,
                        onOpenSpeedTest = onOpenSpeedTest,
                        onOpenMap = onOpenMap,
                        onRefreshInfo = refreshInfo,
                        onSecretUnlocked = onSecretUnlocked,
                        onShowLogs = onOpenLogs,
                        onBurst = { burst++ },
                        onLaser = { laser++ },
                        dock = dockContent,
                    )
            }
            com.vauth.foxyvpn.ui.designs.ParticleBurst(
                trigger = burst,
                modifier = Modifier.fillMaxSize(),
            )
            com.vauth.foxyvpn.ui.designs.LaserBounce(
                trigger = laser,
                color = personalization.connectColor
                    ?: androidx.compose.material3.MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The v1-fix quota presentation (same wording, same honest freshness
 * captions) in the subscription strip's model (DEV-2).
 */
@Composable
private fun buildQuotaUi(
    title: String,
    remaining: Long?,
    max: Long?,
    unlimited: Boolean,
    fetchedAtEpochSeconds: Long,
    isEstimate: Boolean,
): HomeQuotaUi? {
    val hasValue = remaining != null || max != null || unlimited
    if (!hasValue) return null
    val usageText = when {
        unlimited -> s("Unlimited")
        remaining != null && max != null ->
            "${com.vauth.foxyvpn.data.formatBytes(remaining)} " + s("left of") +
                " ${com.vauth.foxyvpn.data.formatBytes(max)}"
        remaining != null -> "${com.vauth.foxyvpn.data.formatBytes(remaining)} " + s("left")
        max != null -> s("Limited to") + " ${com.vauth.foxyvpn.data.formatBytes(max)}"
        else -> "—"
    }
    val caption = when (
        val cap = quotaCaption(
            hasValue = true,
            isEstimate = isEstimate,
            fetchedAtEpochSeconds = fetchedAtEpochSeconds,
            nowEpochSeconds = System.currentTimeMillis() / 1000,
        )
    ) {
        QuotaCaption.None -> null
        QuotaCaption.Estimate -> s("On this device")
        QuotaCaption.LastKnown -> s("Last known value")
        is QuotaCaption.UpdatedAt ->
            s("Updated ") + SimpleDateFormat("HH:mm", Locale.US).format(Date(cap.epochSeconds * 1000))
        is QuotaCaption.Stale -> s("Updated ") + formatQuotaAge(cap.ageSeconds) + s(" ago")
    }
    val usedFraction = when {
        unlimited -> null
        remaining != null && max != null && max > 0L ->
            (1.0 - remaining.toDouble() / max.toDouble()).toFloat().coerceIn(0f, 1f)
        else -> null
    }
    val alert = usedFraction != null && usedFraction >= 0.9f
    return HomeQuotaUi(
        title = title,
        usageText = usageText,
        caption = caption,
        usedFraction = usedFraction,
        alert = alert,
    )
}
