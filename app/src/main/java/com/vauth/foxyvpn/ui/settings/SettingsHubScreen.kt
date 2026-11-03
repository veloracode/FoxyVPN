package com.vauth.foxyvpn.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vauth.foxyvpn.BuildConfig
import com.vauth.foxyvpn.R
import com.vauth.foxyvpn.data.AccountStore
import com.vauth.foxyvpn.data.SettingsStore
import com.vauth.foxyvpn.ui.bento.BentoHub
import com.vauth.foxyvpn.ui.bento.BentoHubEntry
import com.vauth.foxyvpn.ui.bento.BentoTiles
import com.vauth.foxyvpn.ui.l10n.s
import com.vauth.foxyvpn.ui.theme.AppearanceController

/**
 * The settings hub screen — Phase B1 of the Zed overhaul.
 *
 * Replaces the v1 single scrolling settings list with Zed's
 * numbered bento board. Entries carry LIVE summaries read from the
 * stores at composition time; the board layout persists through
 * the appearance controller's bento tiles pref. Navigation to each
 * destination is the caller's (NavGraph) job via [onOpenPage].
 */
@Composable
fun SettingsHubScreen(
    settingsStore: SettingsStore,
    appearance: AppearanceController,
    accountStore: AccountStore,
    onOpenPage: (String) -> Unit,
    onBack: () -> Unit,
) {
    val accounts by accountStore.accountsFlow.collectAsStateWithLifecycle()
    val activeAccountId by accountStore.activeAccountIdFlow.collectAsStateWithLifecycle()
    val activeAccount = accounts.firstOrNull { it.id == activeAccountId }

    val bento = appearance.bento
    val splitCount = if (settingsStore.splitTunnelMode == SettingsStore.SplitTunnelMode.INCLUDE) {
        settingsStore.includedApps.size
    } else {
        settingsStore.excludedApps.size
    }

    fun entry(
        key: String,
        title: String,
        summary: String,
        keywords: String,
    ) = BentoHubEntry(
        key = key,
        title = title,
        summary = summary,
        art = hubArt(key),
        keywords = keywords,
        onOpen = { onOpenPage(key) },
    )

    val entries = listOf(
        entry(
            BentoTiles.APPEARANCE,
            s("Interface"),
            "${bento.palette.label} · Bento",
            "design theme color palette font interface appearance",
        ),
        entry(
            BentoTiles.VPN,
            s("VPN and tunnel"),
            "MTU ${settingsStore.mtu}",
            "vpn tunnel mtu ipv6 always on latency health recovery",
        ),
        entry(
            BentoTiles.DNS,
            s("DNS"),
            when (settingsStore.dnsProvider) {
                SettingsStore.DnsProvider.CUSTOM ->
                    settingsStore.customDohUrl.ifBlank { "Custom DoH" }
                else -> settingsStore.dnsProvider.label
            },
            "dns doh resolver",
        ),
        entry(
            BentoTiles.ROUTING,
            s("Routing"),
            if (settingsStore.bypassLocalNetwork) s("Local network bypassed") else s("Local network in tunnel"),
            "routing bypass local network lan",
        ),
        entry(
            BentoTiles.PER_APP,
            s("Per-app proxy"),
            if (splitCount == 0) {
                s("No apps picked")
            } else {
                pluralStringResource(R.plurals.settings_apps_count, splitCount, splitCount)
            },
            "split tunnel per app include exclude",
        ),
        entry(
            BentoTiles.CORE,
            s("Core and local proxy"),
            "${settingsStore.socksBindAddress}:${settingsStore.socksPort}",
            "core local proxy socks port sing-box",
        ),
        entry(
            BentoTiles.MODE,
            s("Mode"),
            if (settingsStore.proxyOnlyMode) s("Proxy only") else s("Normal"),
            "mode proxy only",
        ),
        entry(
            BentoTiles.ACCOUNTS,
            s("Accounts"),
            when {
                accounts.isEmpty() -> s("No accounts")
                activeAccount != null -> activeAccount.email
                else -> pluralStringResource(R.plurals.settings_apps_count, accounts.size, accounts.size)
            },
            "accounts sign in sign out multi account",
        ),
        entry(
            BentoTiles.MUX,
            s("Multiplexing"),
            pluralStringResource(
                R.plurals.stream_count,
                settingsStore.maxConcurrentStreams,
                settingsStore.maxConcurrentStreams,
            ),
            "multiplex concurrency streams mux",
        ),
        entry(
            BentoTiles.FRAGMENT,
            s("TLS fragmenting"),
            if (settingsStore.tlsFragmentMode == SettingsStore.TlsFragmentMode.OFF) {
                stringResource(R.string.settings_disabled)
            } else {
                settingsStore.tlsFragmentMode.label
            },
            "tls fragmentation fingerprint utls browser",
        ),
        entry(
            BentoTiles.ADVANCED,
            s("Advanced"),
            if (settingsStore.customEdgeAddress.isNotBlank()) {
                settingsStore.customEdgeAddress
            } else if (settingsStore.upstreamProxyEnabled) {
                s("Upstream proxy on")
            } else {
                s("Edge, transport, upstream proxy")
            },
            "advanced edge transport http upstream proxy tcp fast open",
        ),
        entry(
            BentoTiles.SUPPORT,
            s("Support"),
            s("Logs and help"),
            "support logs help",
        ),
        entry(
            BentoTiles.UPDATE,
            s("Updates"),
            "v${BuildConfig.VERSION_NAME}",
            "update check version",
        ),
        entry(
            BentoTiles.ABOUT,
            s("About"),
            "v${BuildConfig.VERSION_NAME}",
            "about version source code logs",
        ),
        entry(
            BentoTiles.ANTI_BLOCK,
            s("Anti-block"),
            settingsStore.tlsSplitMode.label,
            "anti block mozilla tls split account proxy relay",
        ),
    )

    BentoHub(
        entries = entries,
        layoutEncoded = bento.tiles,
        onLayoutChange = { appearance.updateBentoTiles(it) },
        onBack = onBack,
    )
}
