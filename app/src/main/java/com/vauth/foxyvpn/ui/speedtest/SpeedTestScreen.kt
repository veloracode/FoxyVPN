package com.vauth.foxyvpn.ui.speedtest

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vauth.foxyvpn.AppContainer
import com.vauth.foxyvpn.data.SpeedResultsStore
import com.vauth.foxyvpn.data.model.ConnectionState
import com.vauth.foxyvpn.data.model.displayName
import com.vauth.foxyvpn.ui.bento.SettingsPageScaffold
import com.vauth.foxyvpn.ui.bento.formatBytesBento
import com.vauth.foxyvpn.ui.l10n.s
import com.vauth.foxyvpn.ui.resolveHomeQuota
import com.vauth.foxyvpn.vpn.FoxyVpnService
import com.vauth.foxyvpn.vpn.localProxyClient
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * The Speed test screen (Phase B2, DESIGN-SYSTEM §10.3 anatomy on the
 * shared settings scaffold): route pills, phase chips, the live card,
 * result stats, the endpoint's meta card, and the lime action pill.
 *
 * Quota safeguard (the overhaul review's rule, §10.2 adaptation):
 * through the tunnel, a run is gated on the sealed quota model —
 * ample/unlimited → full test; capped with little remaining → a
 * confirmation that names the maximum cost first; quota unknown →
 * ping-only, no transfers. A direct run (VPN off) spends no Mozilla
 * quota and is labeled Direct, never presented as VPN speed.
 */
sealed interface SpeedGate {
    object Full : SpeedGate
    data class Confirm(val remainingBytes: Long, val maxCostBytes: Long) : SpeedGate
    object PingOnly : SpeedGate
}

fun speedGateFor(unlimited: Boolean, remainingBytes: Long?): SpeedGate {
    val maxCost = SpeedTestMath.MAX_DOWN_TOTAL + SpeedTestMath.MAX_UP_TOTAL
    return when {
        unlimited -> SpeedGate.Full
        remainingBytes == null -> SpeedGate.PingOnly
        remainingBytes >= 250_000_000L -> SpeedGate.Full
        else -> SpeedGate.Confirm(remainingBytes, maxCost)
    }
}

@Composable
fun SpeedTestScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = container.settingsStore
    val vpnState by FoxyVpnService.state.collectAsState()
    val selected by container.proxyStateStore.selectedProxyFlow.collectAsState()
    val connected = vpnState == ConnectionState.CONNECTED

    val accounts by container.accountStore.accountsFlow.collectAsState()
    val activeAccountId by container.accountStore.activeAccountIdFlow.collectAsState()
    val accountSnapshot by settings.account.collectAsState()
    val activeAccount = accounts.firstOrNull { it.id == activeAccountId }
    val quota = resolveHomeQuota(
        snapshot = accountSnapshot,
        activeAccount = activeAccount,
        knownAccountIds = accounts.map { it.id }.toSet(),
        usedBytesOfActive = activeAccount?.let { container.accountStore.getUsedBytes(it.id) } ?: 0L,
    )
    val gate = if (connected) {
        speedGateFor(unlimited = quota.unlimited, remainingBytes = quota.remaining)
    } else {
        SpeedGate.Full
    }

    val scope = rememberCoroutineScope()
    val engine = remember(connected, settings.socksPort) {
        val client = if (connected) {
            localProxyClient(
                port = settings.socksPort,
                username = if (settings.localProxyAuthEnabled) settings.localProxyUsername else "",
                password = if (settings.localProxyAuthEnabled) settings.localProxyPassword else "",
                timeoutSeconds = 30,
            )
        } else {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        }
        SpeedTestEngine(client, scope)
    }
    DisposableEffect(engine) { onDispose { engine.cancel() } }
    val testState by engine.state.collectAsState()

    val serverKey = if (connected) selected?.authority ?: "tunnel" else "direct"
    var lastResult by remember(serverKey) {
        mutableStateOf(SpeedResultsStore.load(context, serverKey))
    }
    LaunchedEffect(testState.phase) {
        if (testState.phase == SpeedPhase.Done) {
            SpeedResultsStore.save(
                context, serverKey,
                SpeedResultsStore.Entry(
                    downMbps = testState.downMbps,
                    upMbps = testState.upMbps,
                    pingMs = testState.pingMs,
                    atMs = System.currentTimeMillis(),
                ),
            )
            lastResult = SpeedResultsStore.load(context, serverKey)
        }
    }

    var confirmOpen by remember { mutableStateOf(false) }
    val running = testState.phase == SpeedPhase.Ping ||
        testState.phase == SpeedPhase.Download ||
        testState.phase == SpeedPhase.Upload
    fun start() {
        when (gate) {
            is SpeedGate.Confirm -> confirmOpen = true
            SpeedGate.PingOnly -> engine.start(allowTransfers = false)
            SpeedGate.Full -> engine.start(allowTransfers = true)
        }
    }

    if (confirmOpen && gate is SpeedGate.Confirm) {
        AlertDialog(
            onDismissRequest = { confirmOpen = false },
            title = { Text(s("Use quota for a full test?")) },
            text = {
                Text(
                    s("Remaining quota") + ": " + formatBytesBento(gate.remainingBytes) +
                        "\n" + s("A full test can use up to") + " " +
                        formatBytesBento(gate.maxCostBytes) + ".",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmOpen = false
                    engine.start(allowTransfers = true)
                }) { Text(s("Run full test")) }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmOpen = false
                    engine.start(allowTransfers = false)
                }) { Text(s("Ping only")) }
            },
        )
    }

    SettingsPageScaffold(title = s("Speed test"), art = null, onBack = onBack) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            // Route row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoutePill(if (connected) s("Via tunnel") else s("Direct"), active = true)
                selected?.let { RoutePill(it.displayName, active = false) }
            }
            Spacer(Modifier.height(12.dp))
            // Phase chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PhaseChip(s("Ping"), testState.phase, SpeedPhase.Ping, done = testState.pingMs != null)
                PhaseChip(s("Download"), testState.phase, SpeedPhase.Download, done = testState.downMbps != null)
                PhaseChip(s("Upload"), testState.phase, SpeedPhase.Upload, done = testState.upMbps != null)
            }
            Spacer(Modifier.height(12.dp))
            // Live card
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Text(
                    when (testState.phase) {
                        SpeedPhase.Download, SpeedPhase.Upload ->
                            SpeedTestMath.formatMbps(testState.liveMbps) + " Mbps"
                        SpeedPhase.Done ->
                            SpeedTestMath.formatMbps(testState.downMbps) + " Mbps"
                        else -> "— Mbps"
                    },
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    when (testState.phase) {
                        SpeedPhase.Ping -> s("Measuring ping…")
                        SpeedPhase.Download -> s("Downloading") + " " + testState.liveStepLabel
                        SpeedPhase.Upload -> s("Uploading") + " " + testState.liveStepLabel
                        SpeedPhase.Done -> s("Done")
                        SpeedPhase.Stopped -> s("Stopped")
                        SpeedPhase.Error -> testState.error ?: s("Failed")
                        SpeedPhase.Idle -> s("Ready")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            // Results
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigStat(s("Download"), SpeedTestMath.formatMbps(testState.downMbps), "Mbps", Modifier.weight(1f))
                BigStat(s("Upload"), SpeedTestMath.formatMbps(testState.upMbps), "Mbps", Modifier.weight(1f))
                BigStat(
                    s("Ping"),
                    testState.pingMs?.let { "%.0f".format(it) } ?: "—",
                    "ms",
                    Modifier.weight(1f),
                )
            }
            testState.meta?.let { meta ->
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    listOfNotNull(
                        meta.clientIp?.let { s("IP") + ": " + it },
                        listOfNotNull(meta.city, meta.country).joinToString(", ").ifBlank { null },
                        meta.colo?.let { "Colo: " + it },
                        meta.asOrganization,
                    ).forEach {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            if (gate is SpeedGate.PingOnly) {
                Spacer(Modifier.height(12.dp))
                Text(
                    s("Quota is unknown, so only ping is measured — no data is spent."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            lastResult?.let { last ->
                Spacer(Modifier.height(12.dp))
                Text(
                    s("Last test") + ": ↓ " + SpeedTestMath.formatMbps(last.downMbps) +
                        " · ↑ " + SpeedTestMath.formatMbps(last.upMbps) +
                        " · " + (last.pingMs?.let { "%.0f ms".format(it) } ?: "—"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            // Action pill
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(27.dp))
                    .background(
                        if (running) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.primary,
                    )
                    .clickable(role = Role.Button) {
                        if (running) engine.cancel() else start()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when {
                        running -> s("Stop")
                        testState.phase == SpeedPhase.Done || testState.phase == SpeedPhase.Stopped ||
                            testState.phase == SpeedPhase.Error -> s("Restart")
                        else -> s("Start test")
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (running) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                formatBytesBento(testState.bytesUsed) + " " + s("used") +
                    " · " + testState.elapsedSeconds + "s",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RoutePill(label: String, active: Boolean) {
    Box(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (active) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (active) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PhaseChip(label: String, current: SpeedPhase, mine: SpeedPhase, done: Boolean) {
    val active = current == mine
    Box(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    active -> MaterialTheme.colorScheme.primary
                    done -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                },
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                active -> MaterialTheme.colorScheme.onPrimary
                done -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun BigStat(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.size(3.dp))
            Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}
