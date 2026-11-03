package com.vauth.foxyvpn.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
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

    // Design dispatch (B2): the tile board is the Bento design's root;
    // Pixel and Old get Zed's RootPage anatomy — a grouped list over
    // the SAME entries, so every destination exists under every design.
    if (appearance.appDesign == SettingsStore.AppDesign.BENTO) {
        BentoHub(
            entries = entries,
            layoutEncoded = bento.tiles,
            onLayoutChange = { appearance.updateBentoTiles(it) },
            onBack = onBack,
        )
    } else {
        SettingsRootList(
            entries = entries,
            accountSummary = entries.firstOrNull { it.key == BentoTiles.ACCOUNTS }?.summary,
            onBack = onBack,
        )
    }
}

/**
 * The settings root for the Pixel/Old designs (Zed's RootPage
 * anatomy): profile card, the account card, then every destination as
 * a grouped row (art icon in a colored square, title, live summary,
 * chevron), and the version footer.
 */
@Composable
private fun SettingsRootList(
    entries: List<BentoHubEntry>,
    accountSummary: String?,
    onBack: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val scroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .background(cs.surface)
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = s("Back"),
                )
            }
            Text(
                s("Settings"),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        // Profile card.
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(cs.primaryContainer)
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Text(
                "FoxyVPN",
                style = MaterialTheme.typography.titleLarge,
                color = cs.onPrimaryContainer,
            )
            Text(
                "v${BuildConfig.VERSION_NAME}" +
                    (accountSummary?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onPrimaryContainer.copy(alpha = 0.8f),
            )
        }
        Spacer(Modifier.height(14.dp))
        // Grouped destination rows in one card stack.
        val containers = listOf(
            cs.primaryContainer, cs.secondaryContainer, cs.tertiaryContainer,
            cs.surfaceContainerHigh,
        )
        val contents = listOf(
            cs.onPrimaryContainer, cs.onSecondaryContainer, cs.onTertiaryContainer,
            cs.onSurface,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(cs.surfaceContainerHigh),
        ) {
            entries.forEachIndexed { index, item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = item.onOpen)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(containers[index % containers.size]),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            item.art.icon,
                            contentDescription = null,
                            tint = contents[index % contents.size],
                            modifier = Modifier.size(21.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = cs.onSurface,
                        )
                        Text(
                            item.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = cs.onSurfaceVariant,
                    )
                }
                if (index < entries.lastIndex) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 66.dp, end = 14.dp)
                            .height(1.dp)
                            .background(cs.onSurface.copy(alpha = 0.06f)),
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
    }
}
