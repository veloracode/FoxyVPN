package com.vauth.foxyvpn.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vauth.foxyvpn.BuildConfig
import com.vauth.foxyvpn.R
import com.vauth.foxyvpn.data.AppLinks
import com.vauth.foxyvpn.data.ProxySettingsRepository
import com.vauth.foxyvpn.data.SettingsStore
import com.vauth.foxyvpn.ui.ProxyEditor
import com.vauth.foxyvpn.ui.ProxySettingsViewModel
import com.vauth.foxyvpn.ui.bento.BentoTiles
import com.vauth.foxyvpn.ui.bento.SettingsActionRow
import com.vauth.foxyvpn.ui.bento.SettingsChoiceRow
import com.vauth.foxyvpn.ui.bento.SettingsGroup
import com.vauth.foxyvpn.ui.bento.SettingsInfoRow
import com.vauth.foxyvpn.ui.bento.SettingsPageScaffold
import com.vauth.foxyvpn.ui.bento.SettingsSwitchRow
import com.vauth.foxyvpn.ui.l10n.s
import com.vauth.foxyvpn.vpn.FoxyVpnService
import com.vauth.foxyvpn.vpn.TunnelHealth

/**
 * The product settings pages — Phase B1 of the Zed overhaul.
 *
 * Every control from the v1 settings screen, redistributed to its
 * DESIGN-MAP §6.2 destination and re-rendered with the Bento
 * settings kit. Behaviour is unchanged: same store writes, same
 * dialogs, same ProxySettingsViewModel; only the chrome moved.
 */

// ---------------------------------------------------------------- VPN

@Composable
fun VpnSettingsPage(settingsStore: SettingsStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val health by FoxyVpnService.tunnelHealth.collectAsStateWithLifecycle()
    var mtu by remember { mutableIntStateOf(settingsStore.mtu) }
    var latencyProbeUrl by remember { mutableStateOf(settingsStore.latencyProbeUrl) }
    var ipv6Enabled by remember { mutableStateOf(settingsStore.ipv6Enabled) }
    var automaticRecovery by remember { mutableStateOf(settingsStore.automaticRecovery) }
    var showLatency by remember { mutableStateOf(false) }
    var showMtu by remember { mutableStateOf(false) }

    if (showLatency) {
        LatencyProbeDialog(
            current = latencyProbeUrl,
            onDismiss = { showLatency = false },
            onConfirm = {
                settingsStore.latencyProbeUrl = it
                latencyProbeUrl = it
                showLatency = false
            },
        )
    }
    if (showMtu) {
        MtuPickerDialog(
            current = mtu,
            minimum = if (ipv6Enabled) 1280 else SettingsStore.MIN_MTU,
            onDismiss = { showMtu = false },
            onConfirm = {
                mtu = it
                settingsStore.mtu = it
                showMtu = false
            },
        )
    }

    SettingsPageScaffold(
        title = s("VPN and tunnel"),
        art = hubArt(BentoTiles.VPN),
        onBack = onBack,
    ) {
        SettingsGroup(s("Connection"), listOf(
            {
                SettingsActionRow(
                    title = stringResource(R.string.settings_always_on_vpn),
                    description = stringResource(R.string.settings_always_on_help),
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(AndroidSettings.ACTION_VPN_SETTINGS))
                        }.onFailure {
                            Toast.makeText(
                                context,
                                "VPN settings are unavailable on this device",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                )
            },
            {
                SettingsActionRow(
                    title = stringResource(R.string.settings_latency_test_url),
                    description = latencyProbeUrl,
                    onClick = { showLatency = true },
                )
            },
            {
                SettingsActionRow(
                    title = stringResource(R.string.settings_tunnel_mtu),
                    description = mtu.toString(),
                    onClick = { showMtu = true },
                )
            },
            {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_ipv6_tunnel),
                    description = if (ipv6Enabled) {
                        stringResource(R.string.settings_dual_stack)
                    } else {
                        stringResource(R.string.settings_ipv4_only)
                    },
                    checked = ipv6Enabled,
                    onCheckedChange = {
                        if (it && mtu < 1280) {
                            settingsStore.mtu = 1280
                            mtu = 1280
                        }
                        settingsStore.ipv6Enabled = it
                        ipv6Enabled = it
                    },
                )
            },
        ))
        SettingsGroup(s("Health"), listOf(
            {
                SettingsInfoRow(
                    title = stringResource(R.string.settings_tunnel_health),
                    body = when (health) {
                        TunnelHealth.UNKNOWN -> stringResource(R.string.settings_health_not_connected)
                        TunnelHealth.CHECKING -> stringResource(R.string.settings_health_checking)
                        TunnelHealth.HEALTHY -> stringResource(R.string.settings_health_healthy)
                        TunnelHealth.NO_NETWORK -> stringResource(R.string.settings_health_no_network)
                        TunnelHealth.UNREACHABLE -> stringResource(R.string.settings_health_unreachable)
                    },
                )
            },
            {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_automatic_connection_recovery),
                    description = stringResource(R.string.settings_recovery_help),
                    checked = automaticRecovery,
                    onCheckedChange = {
                        settingsStore.automaticRecovery = it
                        automaticRecovery = it
                    },
                )
            },
        ))
    }
}

// ---------------------------------------------------------------- DNS

@Composable
fun DnsSettingsPage(settingsStore: SettingsStore, onBack: () -> Unit) {
    var dnsProvider by remember { mutableStateOf(settingsStore.dnsProvider) }
    var customDohUrl by remember { mutableStateOf(settingsStore.customDohUrl) }
    var showPicker by remember { mutableStateOf(false) }

    if (showPicker) {
        DnsProviderPickerDialog(
            currentProvider = dnsProvider,
            currentCustomUrl = customDohUrl,
            onDismiss = { showPicker = false },
            onConfirm = { provider, url ->
                settingsStore.saveDns(provider, url)
                dnsProvider = provider
                customDohUrl = settingsStore.customDohUrl
                showPicker = false
            },
        )
    }

    SettingsPageScaffold(
        title = s("DNS"),
        subtitle = s("Name resolution for the tunnel"),
        art = hubArt(BentoTiles.DNS),
        onBack = onBack,
    ) {
        SettingsGroup(null, listOf {
            SettingsActionRow(
                title = stringResource(R.string.settings_dns_provider),
                description = when (dnsProvider) {
                    SettingsStore.DnsProvider.CUSTOM -> customDohUrl.ifBlank { "Custom DoH" }
                    else -> dnsProvider.label
                },
                onClick = { showPicker = true },
            )
        })
    }
}

// ---------------------------------------------------------------- Routing

@Composable
fun RoutingSettingsPage(settingsStore: SettingsStore, onBack: () -> Unit) {
    var bypassLocalNetwork by remember { mutableStateOf(settingsStore.bypassLocalNetwork) }
    var blockAds by remember { mutableStateOf(settingsStore.blockAds) }
    val proxyOnlyMode = settingsStore.proxyOnlyMode
    SettingsPageScaffold(
        title = s("Routing"),
        art = hubArt(BentoTiles.ROUTING),
        onBack = onBack,
    ) {
        SettingsGroup(null, listOf {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_bypass_local_network),
                description = stringResource(R.string.settings_bypass_local_network_help),
                checked = bypassLocalNetwork,
                enabled = !proxyOnlyMode,
                onCheckedChange = {
                    settingsStore.bypassLocalNetwork = it
                    bypassLocalNetwork = it
                },
            )
            // Block ads (B2, user-ordered): same placement as ZedSecure's
            // own toggle — the Routing page's mode card, under the mode
            // rows. Applies on next connection (it changes the
            // generated config, like bypass-local above).
            SettingsSwitchRow(
                title = s("Block ads"),
                description = s("Drop requests matching the ad-domain list"),
                checked = blockAds,
                onCheckedChange = {
                    settingsStore.blockAds = it
                    blockAds = it
                },
            )
        })
    }
}

// ---------------------------------------------------------------- Per-app

@Composable
fun PerAppSettingsPage(settingsStore: SettingsStore, onBack: () -> Unit) {
    val mode = settingsStore.splitTunnelMode
    val count = if (mode == SettingsStore.SplitTunnelMode.INCLUDE) {
        settingsStore.includedApps.size
    } else {
        settingsStore.excludedApps.size
    }
    SettingsPageScaffold(
        title = s("Per-app proxy"),
        subtitle = if (count == 0) {
            s("No apps picked")
        } else {
            pluralStringResource(R.plurals.settings_apps_count, count, count)
        },
        art = hubArt(BentoTiles.PER_APP),
        onBack = onBack,
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            SplitTunnelPicker(settingsStore = settingsStore)
        }
    }
}

// ---------------------------------------------------------------- Core / local proxy

@Composable
private fun rememberProxyViewModel(settingsStore: SettingsStore): ProxySettingsViewModel =
    viewModel(
        factory = remember(settingsStore) {
            ProxySettingsViewModel.factory(ProxySettingsRepository(settingsStore))
        },
    )

@Composable
fun CoreSettingsPage(settingsStore: SettingsStore, onBack: () -> Unit) {
    val proxyViewModel = rememberProxyViewModel(settingsStore)
    val proxyState by proxyViewModel.state.collectAsStateWithLifecycle()
    var proxyOnlyMode by remember { mutableStateOf(settingsStore.proxyOnlyMode) }
    ProxySettingsDialog(
        proxyState,
        proxyViewModel::close,
        proxyViewModel::open,
        proxyViewModel::save,
        proxyViewModel::save,
    )
    SettingsPageScaffold(
        title = s("Core and local proxy"),
        art = hubArt(BentoTiles.CORE),
        onBack = onBack,
    ) {
        if (proxyState.editor == null && proxyState.error != null) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    proxyState.error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = proxyViewModel::reload) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
        SettingsGroup(null, listOf(
            {
                SettingsActionRow(
                    title = stringResource(R.string.local_proxy),
                    description = proxyState.summary?.let { summary ->
                        stringResource(
                            R.string.local_proxy_summary,
                            SettingsStore.DEFAULT_SOCKS_BIND_ADDRESS,
                            summary.localPort,
                        )
                    } ?: stringResource(R.string.loading),
                    enabled = !proxyState.busy && proxyState.summary != null,
                    onClick = { proxyViewModel.open(ProxyEditor.LOCAL) },
                )
            },
            {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_proxy_only_mode),
                    description = stringResource(R.string.settings_proxy_only_help),
                    checked = proxyOnlyMode,
                    onCheckedChange = {
                        settingsStore.proxyOnlyMode = it
                        proxyOnlyMode = it
                    },
                )
            },
        ))
    }
}

// ---------------------------------------------------------------- Mode

@Composable
fun ModeSettingsPage(settingsStore: SettingsStore, onBack: () -> Unit) {
    var proxyOnlyMode by remember { mutableStateOf(settingsStore.proxyOnlyMode) }
    val normalLabel = s("Normal")
    val proxyOnlyLabel = s("Proxy only")
    SettingsPageScaffold(
        title = s("Mode"),
        art = hubArt(BentoTiles.MODE),
        onBack = onBack,
    ) {
        SettingsGroup(null, listOf {
            SettingsChoiceRow(
                title = s("Connection mode"),
                options = listOf(normalLabel, proxyOnlyLabel),
                selected = if (proxyOnlyMode) proxyOnlyLabel else normalLabel,
                onSelect = { label ->
                    val next = label == proxyOnlyLabel
                    settingsStore.proxyOnlyMode = next
                    proxyOnlyMode = next
                },
            )
        })
        SettingsGroup(null, listOf {
            SettingsInfoRow(
                s("Proxy only"),
                stringResource(R.string.settings_proxy_only_help),
            )
        })
    }
}

// ---------------------------------------------------------------- Multiplexing

@Composable
fun MuxSettingsPage(settingsStore: SettingsStore, onBack: () -> Unit) {
    var maxConcurrentStreams by remember { mutableIntStateOf(settingsStore.maxConcurrentStreams) }
    var showPicker by remember { mutableStateOf(false) }
    if (showPicker) {
        ConcurrencyPickerDialog(
            current = maxConcurrentStreams,
            onDismiss = { showPicker = false },
            onConfirm = {
                maxConcurrentStreams = it
                settingsStore.maxConcurrentStreams = it
                showPicker = false
            },
        )
    }
    SettingsPageScaffold(
        title = s("Multiplexing"),
        art = hubArt(BentoTiles.MUX),
        onBack = onBack,
    ) {
        SettingsGroup(null, listOf {
            SettingsActionRow(
                title = stringResource(R.string.settings_multiplex_concurrency),
                description = pluralStringResource(
                    R.plurals.stream_count,
                    maxConcurrentStreams,
                    maxConcurrentStreams,
                ),
                onClick = { showPicker = true },
            )
        })
    }
}

// ---------------------------------------------------------------- Fragment

@Composable
fun FragmentSettingsPage(settingsStore: SettingsStore, onBack: () -> Unit) {
    var tlsFragmentMode by remember { mutableStateOf(settingsStore.tlsFragmentMode) }
    var fragmentFallbackDelay by remember { mutableIntStateOf(settingsStore.fragmentFallbackDelay) }
    var utlsFingerprint by remember { mutableStateOf(settingsStore.utlsFingerprint) }
    var showFragment by remember { mutableStateOf(false) }
    var showFingerprint by remember { mutableStateOf(false) }

    if (showFragment) {
        TlsFragmentPickerDialog(
            currentMode = tlsFragmentMode,
            currentDelay = fragmentFallbackDelay,
            onDismiss = { showFragment = false },
            onConfirm = { mode, delay ->
                tlsFragmentMode = mode
                settingsStore.tlsFragmentMode = mode
                fragmentFallbackDelay = delay
                settingsStore.fragmentFallbackDelay = delay
                showFragment = false
            },
        )
    }
    if (showFingerprint) {
        UtlsFingerprintPickerDialog(
            current = utlsFingerprint,
            onDismiss = { showFingerprint = false },
            onConfirm = {
                utlsFingerprint = it
                settingsStore.utlsFingerprint = it
                showFingerprint = false
            },
        )
    }
    SettingsPageScaffold(
        title = s("TLS fragmenting"),
        art = hubArt(BentoTiles.FRAGMENT),
        onBack = onBack,
    ) {
        SettingsGroup(null, listOf(
            {
                SettingsActionRow(
                    title = stringResource(R.string.settings_tls_fragmentation),
                    description = if (tlsFragmentMode == SettingsStore.TlsFragmentMode.OFF) {
                        stringResource(R.string.settings_disabled)
                    } else if (tlsFragmentMode == SettingsStore.TlsFragmentMode.RECORD) {
                        tlsFragmentMode.label
                    } else {
                        "${tlsFragmentMode.label}, ${fragmentFallbackDelay}ms"
                    },
                    onClick = { showFragment = true },
                )
            },
            {
                SettingsActionRow(
                    title = stringResource(R.string.settings_browser_tls_fingerprint),
                    description = utlsFingerprint.label,
                    onClick = { showFingerprint = true },
                )
            },
        ))
    }
}

// ---------------------------------------------------------------- Advanced

@Composable
fun AdvancedSettingsPage(settingsStore: SettingsStore, onBack: () -> Unit) {
    var customEdgeAddress by remember { mutableStateOf(settingsStore.customEdgeAddress) }
    var httpVersion by remember { mutableStateOf(settingsStore.httpVersion) }
    var tcpFastOpen by remember { mutableStateOf(settingsStore.tcpFastOpen) }
    var showEdge by remember { mutableStateOf(false) }
    var showHttp by remember { mutableStateOf(false) }

    val proxyViewModel = rememberProxyViewModel(settingsStore)
    val proxyState by proxyViewModel.state.collectAsStateWithLifecycle()
    ProxySettingsDialog(
        proxyState,
        proxyViewModel::close,
        proxyViewModel::open,
        proxyViewModel::save,
        proxyViewModel::save,
    )

    if (showEdge) {
        CustomEdgeAddressDialog(
            current = customEdgeAddress,
            onDismiss = { showEdge = false },
            onConfirm = {
                customEdgeAddress = it
                settingsStore.customEdgeAddress = it
                showEdge = false
            },
        )
    }
    if (showHttp) {
        HttpVersionPickerDialog(
            current = httpVersion,
            onDismiss = { showHttp = false },
            onConfirm = {
                httpVersion = it
                settingsStore.httpVersion = it
                showHttp = false
            },
        )
    }

    SettingsPageScaffold(
        title = s("Advanced"),
        art = hubArt(BentoTiles.ADVANCED),
        onBack = onBack,
    ) {
        SettingsGroup(s("Tunnel"), listOf(
            {
                SettingsActionRow(
                    title = stringResource(R.string.settings_custom_edge_address),
                    description = customEdgeAddress.ifBlank { stringResource(R.string.not_set) },
                    onClick = { showEdge = true },
                )
            },
            {
                SettingsActionRow(
                    title = stringResource(R.string.settings_transport_protocol),
                    description = httpVersion.label,
                    onClick = { showHttp = true },
                )
            },
            {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_tcp_fast_open),
                    description = stringResource(R.string.settings_tcp_fast_open_help),
                    checked = tcpFastOpen,
                    onCheckedChange = {
                        settingsStore.tcpFastOpen = it
                        tcpFastOpen = it
                    },
                )
            },
        ))
        if (proxyState.summary?.upstreamReviewRequired == true &&
            proxyState.summary?.upstreamEnabled == true
        ) {
            Text(
                stringResource(R.string.upstream_review_required),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
            )
        }
        SettingsGroup(s("Upstream proxy"), listOf<@Composable () -> Unit>(
            {
                SettingsSwitchRow(
                    title = stringResource(R.string.upstream_proxy),
                    description = stringResource(R.string.upstream_proxy_help),
                    checked = proxyState.summary?.upstreamEnabled ?: settingsStore.upstreamProxyEnabled,
                    enabled = !proxyState.busy &&
                        (proxyState.summary != null || settingsStore.upstreamProxyEnabled),
                    onCheckedChange = proxyViewModel::setUpstreamEnabled,
                )
            },
        ) + (if (proxyState.summary?.upstreamEnabled == true) {
            listOf<@Composable () -> Unit>(
                {
                    SettingsActionRow(
                        title = stringResource(R.string.proxy_type),
                        description = proxyState.summary?.upstreamType?.name.orEmpty(),
                        enabled = !proxyState.busy,
                        onClick = { proxyViewModel.open(ProxyEditor.UPSTREAM_TYPE) },
                    )
                },
                {
                    SettingsActionRow(
                        title = stringResource(R.string.proxy_address),
                        description = proxyState.summary?.let { summary ->
                            val host = summary.upstreamHost
                            if (host.isBlank()) {
                                stringResource(R.string.not_set)
                            } else {
                                "${if (':' in host) "[$host]" else host}:${summary.upstreamPort}"
                            }
                        } ?: stringResource(R.string.not_set),
                        enabled = !proxyState.busy,
                        onClick = { proxyViewModel.open(ProxyEditor.UPSTREAM_ADDRESS) },
                    )
                },
                {
                    SettingsActionRow(
                        title = stringResource(R.string.proxy_credentials),
                        description = stringResource(R.string.proxy_credentials_help),
                        enabled = !proxyState.busy,
                        onClick = { proxyViewModel.open(ProxyEditor.UPSTREAM_CREDENTIALS) },
                    )
                },
            )
        } else {
            emptyList()
        }))
    }
}

// ---------------------------------------------------------------- Anti-block

@Composable
fun AntiBlockSettingsPage(settingsStore: SettingsStore, onBack: () -> Unit) {
    SettingsPageScaffold(
        title = s("Anti-block"),
        subtitle = s("When Mozilla can't be reached"),
        art = hubArt(BentoTiles.ANTI_BLOCK),
        onBack = onBack,
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            AntiBlockSettingsSection(settingsStore = settingsStore, showHeader = false)
        }
    }
}

// ---------------------------------------------------------------- About

@Composable
fun AboutSettingsPage(
    onOpenLogs: () -> Unit,
    onCheckForUpdates: (() -> Unit)?,
    automaticUpdates: Boolean,
    onAutomaticUpdatesChange: ((Boolean) -> Unit)?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    SettingsPageScaffold(
        title = s("About"),
        art = hubArt(BentoTiles.ABOUT),
        onBack = onBack,
    ) {
        SettingsGroup(null, listOf(
            {
                SettingsActionRow(
                    title = stringResource(R.string.settings_view_logs),
                    description = stringResource(R.string.settings_logs_help),
                    onClick = onOpenLogs,
                )
            },
            {
                SettingsInfoRow(
                    title = stringResource(R.string.settings_version),
                    body = BuildConfig.VERSION_NAME,
                )
            },
            {
                SettingsActionRow(
                    title = stringResource(R.string.settings_check_for_updates),
                    description = stringResource(R.string.settings_update_source),
                    enabled = onCheckForUpdates != null,
                    onClick = { onCheckForUpdates?.invoke() },
                )
            },
            {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_automatic_update_checks),
                    description = stringResource(R.string.settings_automatic_updates_help),
                    checked = automaticUpdates,
                    enabled = onAutomaticUpdatesChange != null,
                    onCheckedChange = { onAutomaticUpdatesChange?.invoke(it) },
                )
            },
            {
                SettingsActionRow(
                    title = stringResource(R.string.settings_source_code),
                    description = AppLinks.SOURCE,
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(AppLinks.SOURCE)),
                            )
                        }.onFailure {
                            Toast.makeText(
                                context,
                                context.getString(R.string.browser_unavailable),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                )
            },
        ))
    }
}
