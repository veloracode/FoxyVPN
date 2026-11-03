package com.vauth.foxyvpn.ui.bento

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vauth.foxyvpn.ui.l10n.s
import com.vauth.foxyvpn.ui.theme.Bento
import com.vauth.foxyvpn.ui.theme.BentoTone
import com.vauth.foxyvpn.ui.theme.MotionBudget
import com.vauth.foxyvpn.vpn.FoxyVpnService
import kotlinx.coroutines.delay

/**
 * The Bento home screen — Phase B1 of the Zed overhaul.
 *
 * Pure rendering over [HomeUiState] (built by HomeScreen at the seam):
 * Home observes the tunnel/server/account, it never owns them. Live
 * stats are collected ONLY inside the speed/session tiles (the v1.3
 * lag rule), and the elapsed ticker lives inside the tiles and the
 * connect-control wrapper, never at screen level.
 *
 * Deviations from the reference anatomy, all forced by fork data
 * (DESIGN-MAP §12 / DEV-2): the subscription strip carries the account
 * quota (Guardian data) in the strip's anatomy; the server card's exit
 * IP pill is absent (the fork has no through-tunnel exit lookup in B1;
 * the cached direct IP is never presented as an exit IP); the quick
 * chips row renders only when a destination exists — Map and Speed
 * test arrive in Phase B2, so the row is empty in B1.
 */

@Immutable
data class BentoActiveServer(
    val name: String,
    val detail: String?,
    val country: String?,
    val engine: String,
    val locked: Boolean,
)

@Immutable
data class BentoServerPick(
    val id: String,
    val name: String,
    val detail: String,
    val country: String?,
    val pingMs: Int?,
)

/** The quota strip's precomputed display model (DEV-2). */
@Immutable
data class HomeQuotaUi(
    val title: String,
    val usageText: String,
    val caption: String?,
    /** Used share 0..1, or null when no bar can be drawn honestly. */
    val usedFraction: Float?,
    val alert: Boolean,
)

@Immutable
data class HomeUiState(
    val connectState: BentoConnectState,
    val error: String?,
    val faultTitle: String?,
    val faultHint: String?,
    val active: BentoActiveServer?,
    val quota: HomeQuotaUi?,
    val picks: List<BentoServerPick>,
    val lockedNote: String?,
    val localProxyAddress: String?,
    /** Selected-server latency in ms; null while measuring, -2 unavailable. */
    val pingMs: Int?,
)

private const val SPARK_POINTS = 24
private const val TITLE_TAPS = 5
private const val BURST_TAPS = 3
private const val TAP_WINDOW_MS = 1_200L

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BentoHome(
    ui: HomeUiState,
    animate: Boolean,
    contentPadding: PaddingValues,
    dockAutoHide: Boolean,
    onToggleConnection: () -> Unit,
    onBrowseServers: () -> Unit,
    onPickServer: (String) -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenSpeedTest: (() -> Unit)?,
    onOpenMap: (() -> Unit)?,
    onRefreshInfo: () -> Unit,
    onSecretUnlocked: () -> Unit,
    onShowLogs: () -> Unit,
    onBurst: () -> Unit,
    dock: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Bento.colors
    val prefs = Bento.prefs
    val live = ui.connectState == BentoConnectState.Connected
    val scroll = rememberScrollState()
    val fire = LocalBentoHaptics.current

    var titleTaps by remember { mutableIntStateOf(0) }
    var titleLast by remember { mutableLongStateOf(0L) }

    var dockHidden by remember { mutableStateOf(false) }
    if (dockAutoHide) {
        LaunchedEffect(scroll) {
            var last = 0
            snapshotFlow { scroll.value }.collect { value ->
                dockHidden = when {
                    value <= 0 -> false
                    value > last + 4 -> true
                    value < last - 4 -> false
                    else -> dockHidden
                }
                last = value
            }
        }
    } else {
        dockHidden = false
    }

    Box(modifier.fillMaxSize().background(c.ground)) {
        Column(Modifier.fillMaxSize().padding(contentPadding)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(BentoMetrics.gap() + 2.dp),
            ) {
                BentoTopBar(
                    title = "FoxyVPN",
                    onTitleClick = {
                        val now = System.currentTimeMillis()
                        titleTaps = if (now - titleLast < TAP_WINDOW_MS) titleTaps + 1 else 1
                        titleLast = now
                        if (titleTaps >= TITLE_TAPS) {
                            titleTaps = 0
                            fire(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onSecretUnlocked()
                        } else if (titleTaps > 1) {
                            fire(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            if (titleTaps == BURST_TAPS) onBurst()
                        }
                    },
                ) {
                    BentoIconButton(Icons.Filled.Description, s("Logs"), onShowLogs)
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp)
                        .padding(horizontal = BentoMetrics.gutter)
                        .align(Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(BentoMetrics.gap()),
                ) {
                    AnimatedVisibility(visible = ui.faultTitle != null && live) {
                        if (ui.faultTitle != null) {
                            BentoFaultStrip(ui.faultTitle, ui.faultHint, onShowLogs)
                        }
                    }
                    if (ui.connectState == BentoConnectState.Error) {
                        BentoErrorCard(
                            ui.error,
                            onRetry = onToggleConnection,
                            onOtherServer = onBrowseServers,
                            onShowLogs = onShowLogs,
                        )
                    }

                    ServerCard(
                        active = ui.active,
                        live = live,
                        pingMs = ui.pingMs,
                        onClick = onBrowseServers,
                        onRefreshInfo = onRefreshInfo,
                    )

                    ui.quota?.let { quota ->
                        QuotaStrip(quota, onClick = onOpenAccounts)
                    }

                    if (prefs.homeSpeed) {
                        SpeedTilesRow(live = live)
                    }

                    if (prefs.homeSession) {
                        SessionTilesRow(live = live, onStatusTap = onRefreshInfo)
                    }

                    if (prefs.homeChips && (onOpenSpeedTest != null || onOpenMap != null)) {
                        FlowRow(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (onOpenSpeedTest != null && live) {
                                ActionChip(s("Speed test"), Icons.Filled.Speed, onOpenSpeedTest, c.pink.takeIf { c.colorful })
                            }
                            if (onOpenMap != null) {
                                ActionChip(s("Map"), Icons.Filled.Map, onOpenMap, c.violet.takeIf { c.colorful })
                            }
                        }
                    }

                    if (ui.localProxyAddress != null && live) {
                        LocalProxyRow(ui.localProxyAddress)
                    }

                    if (ui.lockedNote != null) {
                        Box(
                            Modifier.fillMaxWidth().clip(BentoShapes.small()).background(c.surface),
                        ) {
                            Text(
                                ui.lockedNote,
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.muted,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 140.dp)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }

                    if (prefs.homeQuick && ui.picks.isNotEmpty()) {
                        BentoCaption(s("Fastest servers"))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            ui.picks.forEachIndexed { i, pick ->
                                PickRow(pick, i, ui.picks.size) { onPickServer(pick.id) }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            ConnectControlWithElapsed(
                state = ui.connectState,
                animate = animate,
                serverName = ui.active?.name,
                onToggle = onToggleConnection,
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .align(Alignment.CenterHorizontally)
                    .padding(start = BentoMetrics.gutter, end = BentoMetrics.gutter, top = 6.dp, bottom = 14.dp),
            )
            AnimatedVisibility(visible = !dockHidden) {
                dock()
            }
        }
    }
}

/** The connect control + its private elapsed ticker (never screen-level). */
@Composable
private fun ConnectControlWithElapsed(
    state: BentoConnectState,
    animate: Boolean,
    serverName: String?,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connectedAt by FoxyVpnService.connectedAtElapsedMs.collectAsState()
    var elapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(connectedAt, state) {
        while (state == BentoConnectState.Connected && connectedAt > 0L) {
            elapsed = ((android.os.SystemClock.elapsedRealtime() - connectedAt) / 1000).toInt().coerceAtLeast(0)
            delay(1000)
        }
        if (state != BentoConnectState.Connected) elapsed = 0
    }
    BentoConnectControl(
        state = state,
        onToggle = onToggle,
        animate = animate,
        elapsedSeconds = elapsed,
        serverName = serverName,
        modifier = modifier,
    )
}

/** Live speed tiles; collects the service stats flow locally (lag rule). */
@Composable
private fun SpeedTilesRow(live: Boolean) {
    val c = Bento.colors
    val stats by FoxyVpnService.vpnLiveStats.collectAsState()
    var downHistory by remember { mutableStateOf(emptyList<Float>()) }
    var upHistory by remember { mutableStateOf(emptyList<Float>()) }
    LaunchedEffect(stats, live) {
        if (!live) {
            downHistory = emptyList()
            upHistory = emptyList()
        } else {
            downHistory = (downHistory + stats.rxRateBytesPerSec.toFloat()).takeLast(SPARK_POINTS)
            upHistory = (upHistory + stats.txRateBytesPerSec.toFloat()).takeLast(SPARK_POINTS)
        }
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BentoMetrics.gap()),
    ) {
        SpeedTile(
            label = s("Download"),
            icon = Icons.Filled.ArrowDownward,
            bps = if (live) stats.rxRateBytesPerSec else 0,
            history = downHistory,
            tone = c.blue,
            live = live,
            modifier = Modifier.weight(1f),
        )
        SpeedTile(
            label = s("Upload"),
            icon = Icons.Filled.ArrowUpward,
            bps = if (live) stats.txRateBytesPerSec else 0,
            history = upHistory,
            tone = c.violet,
            live = live,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SpeedTile(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    bps: Long,
    history: List<Float>,
    tone: BentoTone,
    live: Boolean,
    modifier: Modifier = Modifier,
) {
    val (value, unit) = formatRate(bps)
    Column(
        modifier
            .height(122.dp)
            .clip(BentoShapes.block())
            .background(tone.container)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = tone.content, modifier = Modifier.size(16.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = tone.content,
                maxLines = 1,
            )
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                value,
                style = BentoType.number(36.sp),
                color = tone.content.copy(alpha = if (live) 1f else 0.6f),
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                unit,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = tone.content.copy(alpha = 0.8f),
                modifier = Modifier.alignByBaseline(),
            )
        }
        Sparkline(history, tone.content, Modifier.fillMaxWidth().height(22.dp))
    }
}

@Composable
private fun Sparkline(values: List<Float>, color: Color, modifier: Modifier) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val path = remember { Path() }
    val fill = remember { Path() }
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val max = values.max()
        val min = values.min()
        val span = (max - min).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (values.size - 1)
        val pad = 3.dp.toPx()
        path.reset()
        fill.reset()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = if (max <= 0f) {
                size.height - pad
            } else {
                size.height - pad - (v - min) / span * (size.height - pad * 2)
            }
            if (i == 0) {
                path.moveTo(x, y)
                fill.moveTo(x, size.height)
                fill.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        fill.lineTo(size.width, size.height)
        fill.close()
        scale(if (rtl) -1f else 1f, 1f, pivot = Offset(size.width / 2f, size.height / 2f)) {
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.18f), color.copy(alpha = 0f))))
            drawPath(path, color.copy(alpha = 0.6f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Session facts; the uptime ticker and stats collection stay in here. */
@Composable
private fun SessionTilesRow(live: Boolean, onStatusTap: () -> Unit) {
    val c = Bento.colors
    val stats by FoxyVpnService.vpnLiveStats.collectAsState()
    val connectedAt by FoxyVpnService.connectedAtElapsedMs.collectAsState()
    var elapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(connectedAt, live) {
        while (live && connectedAt > 0L) {
            elapsed = ((android.os.SystemClock.elapsedRealtime() - connectedAt) / 1000).toInt().coerceAtLeast(0)
            delay(1000)
        }
        if (!live) elapsed = 0
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(BentoMetrics.gap())) {
        FactTile(
            label = s("Connected for"),
            value = if (live) formatElapsed(elapsed) else "--:--",
            onClick = onStatusTap,
            tone = c.coral.takeIf { c.colorful },
            modifier = Modifier.weight(1f),
        )
        FactTile(
            label = s("Data this session"),
            value = formatBytesBento(if (live) stats.sessionBytes else 0),
            tone = c.cyan.takeIf { c.colorful },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FactTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    tone: BentoTone? = null,
) {
    val c = Bento.colors
    Column(
        modifier
            .height(70.dp)
            .clip(BentoShapes.small())
            .background(tone?.container ?: c.surface)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = BentoType.body().copy(fontSize = 12.5.sp),
            color = tone?.content?.copy(alpha = 0.8f) ?: c.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(ltrIsolate(value), style = BentoType.number(20.sp), color = tone?.content ?: c.ink, maxLines = 1)
    }
}

@Composable
private fun ServerCard(
    active: BentoActiveServer?,
    live: Boolean,
    pingMs: Int?,
    onClick: () -> Unit,
    onRefreshInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Bento.colors
    val tone = c.stageTone
    BentoCard(
        tone = tone,
        modifier = modifier.fillMaxWidth(),
        shape = BentoShapes.card(),
        onClick = onClick,
        motif = MaterialShapes.Flower,
        motifSize = 168.dp,
        motifRotation = 16f,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text(
            s("Current server").uppercase(),
            style = BentoType.caption(),
            color = tone.content.copy(alpha = 0.8f),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (active?.country != null || active == null) {
                BentoFlag(
                    countryCode = active?.country,
                    size = 50.dp,
                    corner = 17.dp,
                    container = c.chipOnStage,
                    content = tone.content,
                )
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    active?.name ?: s("No server selected"),
                    style = BentoType.title(27.sp),
                    color = tone.content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    active?.detail ?: if (active == null) s("Pick a server to connect") else "",
                    style = BentoType.body(),
                    color = tone.content.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (active?.locked == true) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = tone.content, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = tone.content, modifier = Modifier.size(22.dp))
        }
        if (active != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (live) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(15.dp))
                            .clickable(role = Role.Button, onClick = onRefreshInfo),
                    ) {
                        BentoPill(
                            text = when {
                                pingMs == null -> "…"
                                pingMs < 0 -> "—"
                                else -> "%d ms".format(pingMs)
                            },
                            container = c.chipOnStage,
                            content = tone.content,
                            dot = when {
                                pingMs == null || pingMs < 0 -> tone.content.copy(alpha = 0.4f)
                                pingMs < 300 -> c.goodOnStage
                                pingMs < 800 -> c.mid
                                else -> c.bad
                            },
                            numeric = true,
                        )
                    }
                }
                BentoPill(active.engine, c.lime, c.onLime)
            }
        }
    }
}

/** The account quota in the subscription strip's anatomy (DEV-2). */
@Composable
private fun QuotaStrip(quota: HomeQuotaUi, onClick: () -> Unit) {
    val c = Bento.colors
    val alertInk = if (c.dark) c.danger else c.bad
    BentoCard(
        tone = c.surfaceTone,
        modifier = Modifier.fillMaxWidth(),
        shape = BentoShapes.small(),
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                quota.title,
                style = BentoType.rowTitle(),
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (quota.caption != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    quota.caption,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                    color = if (quota.alert) alertInk else c.muted,
                    maxLines = 1,
                )
            }
        }
        if (quota.usedFraction != null) {
            Box(
                Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.surface2),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(quota.usedFraction.coerceAtLeast(0.02f))
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (quota.usedFraction >= 0.9f) c.bad else c.lime),
                )
            }
        }
        Text(
            quota.usageText,
            style = BentoType.body(),
            color = c.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    tone: BentoTone? = null,
) {
    val c = Bento.colors
    val ink = tone?.content ?: c.ink
    Row(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(tone?.container ?: c.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            color = ink,
            maxLines = 1,
        )
    }
}

@Composable
private fun PickRow(pick: BentoServerPick, index: Int, count: Int, onClick: () -> Unit) {
    val c = Bento.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = BentoMetrics.rowMin())
            .clip(BentoShapes.row(index, count))
            .background(c.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pick.country != null) {
            BentoFlag(pick.country, size = 38.dp, corner = 13.dp)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(pick.name, style = BentoType.rowTitle(), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                pick.detail,
                style = BentoType.body().copy(fontSize = 12.5.sp),
                color = c.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        pick.pingMs?.let { ms ->
            Spacer(Modifier.width(8.dp))
            Text(
                if (ms > 0) ltrIsolate("%d ms".format(ms)) else "—",
                style = BentoType.number(13.5.sp),
                color = c.pingColor(ms),
            )
        }
    }
}

@Composable
private fun BentoFaultStrip(title: String, hint: String?, onShowLogs: () -> Unit) {
    val c = Bento.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(BentoShapes.small())
            .background(c.coral.container)
            .clickable(role = Role.Button, onClick = onShowLogs)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = BentoType.rowTitle(), color = c.coral.content)
            if (!hint.isNullOrBlank()) {
                Text(
                    hint,
                    style = BentoType.body(),
                    color = c.coral.content.copy(alpha = 0.85f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(Icons.Filled.Description, contentDescription = s("Open logs"), tint = c.coral.content, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun BentoErrorCard(
    error: String?,
    onRetry: () -> Unit,
    onOtherServer: () -> Unit,
    onShowLogs: () -> Unit,
) {
    val c = Bento.colors
    val shake = remember { Animatable(0f) }
    var rawOpen by remember(error) { mutableStateOf(false) }
    LaunchedEffect(error) {
        for (x in listOf(10f, -8f, 6f, -3f, 1.5f, 0f)) shake.animateTo(x, tween(55))
    }
    val density = LocalDensity.current
    BentoCard(
        tone = c.coral,
        modifier = Modifier.fillMaxWidth().graphicsLayer { translationX = shake.value * density.density },
        shape = BentoShapes.hero(),
        motif = MaterialShapes.Burst,
        motifSize = 150.dp,
        motifRotation = -12f,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(s("Connection failed"), style = BentoType.title(24.sp), color = c.coral.content)
        Text(
            s("Something went wrong while connecting. Try again, or pick another server."),
            style = BentoType.body(),
            color = c.coral.content.copy(alpha = 0.85f),
        )
        val raw = error?.takeIf { it.isNotBlank() }
        if (raw != null) {
            Text(
                raw,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    textDirection = TextDirection.Ltr,
                ),
                color = c.coral.content.copy(alpha = 0.7f),
                maxLines = if (rawOpen) 10 else 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
                modifier = Modifier.clickable { rawOpen = !rawOpen },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton(s("Retry"), c.lime, c.onLime, onRetry)
            PillButton(
                s("Other server"), Color.Transparent, c.coral.content, onOtherServer,
                outline = c.coral.content.copy(alpha = 0.4f),
            )
            Spacer(Modifier.weight(1f))
            BentoIconButton(
                Icons.Filled.Description,
                s("Open logs"),
                onShowLogs,
                container = Color.Transparent,
                content = c.coral.content,
                size = 40.dp,
            )
        }
    }
}

@Composable
private fun PillButton(
    label: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    outline: Color? = null,
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier
            .height(44.dp)
            .clip(shape)
            .background(container)
            .then(if (outline != null) Modifier.border(1.dp, outline, shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            color = content,
            maxLines = 1,
        )
    }
}

@Composable
private fun LocalProxyRow(address: String) {
    val c = Bento.colors
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(c.surface)
            .clickable(role = Role.Button) {
                clipboard.setText(AnnotatedString(address))
                copied = true
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            if (copied) s("Copied") else s("Local proxy") + "  " + address,
            style = BentoType.number(13.sp, FontWeight.Medium),
            color = c.muted,
            maxLines = 1,
        )
    }
}
