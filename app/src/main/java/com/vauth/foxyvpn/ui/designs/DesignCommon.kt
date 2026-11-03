package com.vauth.foxyvpn.ui.designs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vauth.foxyvpn.ui.bento.BentoConnectState
import com.vauth.foxyvpn.ui.bento.formatBytesBento
import com.vauth.foxyvpn.ui.bento.formatElapsed
import com.vauth.foxyvpn.ui.bento.formatRate
import com.vauth.foxyvpn.ui.l10n.s
import com.vauth.foxyvpn.ui.theme.ConnectButtonStyle
import com.vauth.foxyvpn.ui.theme.Personalization
import com.vauth.foxyvpn.ui.theme.TrafficCardStyle
import com.vauth.foxyvpn.ui.theme.TrafficTileSize
import com.vauth.foxyvpn.vpn.FoxyVpnService
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * Shared pieces of the Pixel and Old home designs (Phase B2) — the two
 * non-Bento Zed designs, built on the same seam as BentoHome: they
 * render [com.vauth.foxyvpn.ui.bento.HomeUiState] and never own the
 * tunnel. Live values are collected ONLY inside the leaf composables
 * here (stats, elapsed), never at the screen level (the lag rule).
 */

private const val TITLE_TAPS = 5
private const val BURST_TAPS = 3
private const val TAP_WINDOW_MS = 1_200L
private const val SPARK_POINTS = 24

/** Live session values, collected leaf-locally (rates, totals, elapsed). */
class LiveSession internal constructor(
    val downBps: Long,
    val upBps: Long,
    val sessionBytes: Long,
    val elapsedSeconds: Int,
    /** Stable per connection; keys the session gradient palette. */
    val sessionKey: Long,
    val downHistory: List<Float>,
    val upHistory: List<Float>,
)

@Composable
fun rememberLiveSession(live: Boolean): LiveSession {
    val stats by FoxyVpnService.vpnLiveStats.collectAsState()
    val connectedAt by FoxyVpnService.connectedAtElapsedMs.collectAsState()
    var elapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(connectedAt, live) {
        val started = connectedAt
        if (live && started != null) {
            while (true) {
                elapsed =
                    ((android.os.SystemClock.elapsedRealtime() - started) / 1000)
                        .toInt().coerceAtLeast(0)
                delay(1000)
            }
        }
        if (!live) elapsed = 0
    }
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
    return LiveSession(
        downBps = if (live) stats.rxRateBytesPerSec else 0,
        upBps = if (live) stats.txRateBytesPerSec else 0,
        sessionBytes = if (live) stats.sessionBytes else 0,
        elapsedSeconds = elapsed,
        sessionKey = connectedAt ?: 0L,
        downHistory = downHistory,
        upHistory = upHistory,
    )
}

/** Title tap counter (5 rapid taps unlock the egg; the 3rd fires a burst). */
@Composable
fun rememberTitleTaps(onUnlock: () -> Unit, onBurst: () -> Unit): () -> Unit {
    var taps by remember { mutableIntStateOf(0) }
    var last by remember { mutableLongStateOf(0L) }
    return {
        val now = System.currentTimeMillis()
        taps = if (now - last < TAP_WINDOW_MS) taps + 1 else 1
        last = now
        if (taps >= TITLE_TAPS) {
            taps = 0
            onUnlock()
        } else if (taps == BURST_TAPS) {
            onBurst()
        }
    }
}

/** Status-text tap counter (3 rapid taps fire the laser overlay). */
@Composable
fun rememberStatusTaps(onLaser: () -> Unit): () -> Unit {
    var taps by remember { mutableIntStateOf(0) }
    var last by remember { mutableLongStateOf(0L) }
    return {
        val now = System.currentTimeMillis()
        taps = if (now - last < TAP_WINDOW_MS) taps + 1 else 1
        last = now
        if (taps >= BURST_TAPS) {
            taps = 0
            onLaser()
        }
    }
}

fun connectStatusText(state: BentoConnectState, error: String?): String = when (state) {
    BentoConnectState.Connected -> s("Connected")
    BentoConnectState.Connecting -> s("Connecting…")
    BentoConnectState.Error -> error ?: s("Connection failed")
    BentoConnectState.Idle -> s("Disconnected")
}

/** The two quick-action chips (Speed test while connected, Map) — all designs. */
@Composable
fun DesignChips(
    connected: Boolean,
    onOpenSpeedTest: (() -> Unit)?,
    onOpenMap: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (onOpenSpeedTest == null && onOpenMap == null) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (connected && onOpenSpeedTest != null) {
            DesignChip(Icons.Filled.Speed, s("Speed test"), onOpenSpeedTest)
        }
        if (onOpenMap != null) {
            DesignChip(Icons.Filled.Map, s("Map"), onOpenMap)
        }
    }
}

@Composable
private fun DesignChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(cs.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = cs.primary, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = cs.onSurface)
    }
}

/** Tap-to-copy local-proxy address chip (proxy-only mode, connected). */
@Composable
fun LocalProxyChip(address: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    val copiedLabel = s("Local proxy") + ": " + address
    Row(
        modifier
            .clip(CircleShape)
            .background(cs.surfaceContainerHigh)
            .clickable(role = Role.Button) { clipboard.setText(AnnotatedString(address)) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            copiedLabel,
            style = MaterialTheme.typography.labelLarge,
            color = cs.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** The server ("config") card shared by Pixel (server row) and Old (active card). */
@Composable
fun ServerCardRow(
    name: String?,
    detail: String?,
    personalization: Personalization,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val container = personalization.configCardColor ?: cs.surfaceContainerHigh
    val content = personalization.configCardTextColor ?: cs.onSurface
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(personalization.cornerStyle.cardDp.dp))
            .background(container)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(
            name ?: s("Choose location"),
            style = MaterialTheme.typography.titleMedium,
            color = content,
            maxLines = 1,
        )
        if (!detail.isNullOrBlank()) {
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = content.copy(alpha = 0.7f),
                maxLines = 1,
            )
        }
    }
}

/**
 * Traffic stats for Pixel/Old in the five TrafficCardStyle shapes.
 * Stats collect in the leaf (rememberLiveSession inside), so a ticking
 * rate never recomposes the home around it.
 */
@Composable
fun TrafficStats(
    live: Boolean,
    style: TrafficCardStyle,
    size: TrafficTileSize,
    personalization: Personalization,
    modifier: Modifier = Modifier,
) {
    val session = rememberLiveSession(live)
    val cs = MaterialTheme.colorScheme
    val downColor = personalization.downloadTileColor ?: cs.primary
    val upColor = personalization.uploadTileColor ?: cs.tertiary
    val vPad = size.vPad.dp
    when (style) {
        TrafficCardStyle.Cards -> Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RateCard(
                label = s("Download"), down = true, bps = session.downBps,
                total = null, color = downColor, vPad = vPad, modifier = Modifier.weight(1f),
            )
            RateCard(
                label = s("Upload"), down = false, bps = session.upBps,
                total = null, color = upColor, vPad = vPad, modifier = Modifier.weight(1f),
            )
        }
        TrafficCardStyle.Duo -> Row(
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(personalization.cornerStyle.cardDp.dp))
                .background(cs.surfaceContainerHigh)
                .padding(horizontal = 18.dp, vertical = vPad),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RateInline(s("Download"), session.downBps, downColor)
            RateInline(s("Upload"), session.upBps, upColor)
        }
        TrafficCardStyle.Rings -> Row(
            modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            RateRing(s("Download"), session.downBps, session.downHistory, downColor)
            RateRing(s("Upload"), session.upBps, session.upHistory, upColor)
        }
        TrafficCardStyle.Minimal -> {
            val (dv, du) = formatRate(session.downBps)
            val (uv, uu) = formatRate(session.upBps)
            Text(
                "↓ $dv $du   ↑ $uv $uu   ·   " + formatBytesBento(session.sessionBytes),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                modifier = modifier.padding(vertical = vPad),
            )
        }
        TrafficCardStyle.Graph -> Column(
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(personalization.cornerStyle.cardDp.dp))
                .background(cs.surfaceContainerHigh)
                .padding(horizontal = 18.dp, vertical = vPad),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                RateInline(s("Download"), session.downBps, downColor)
                RateInline(s("Upload"), session.upBps, upColor)
            }
            Spacer(Modifier.height(8.dp))
            Sparkline(session.downHistory, downColor, Modifier.fillMaxWidth().height(44.dp))
        }
    }
}

@Composable
private fun RateCard(
    label: String,
    down: Boolean,
    bps: Long,
    total: String?,
    color: Color,
    vPad: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val (value, unit) = formatRate(bps)
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(cs.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = vPad),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (down) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                contentDescription = null, tint = color, modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = cs.onSurface, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(4.dp))
            Text(unit, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
        }
        if (total != null) {
            Text(total, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun RateInline(label: String, bps: Long, color: Color) {
    val cs = MaterialTheme.colorScheme
    val (value, unit) = formatRate(bps)
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(3.dp))
            Text(unit, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}

@Composable
private fun RateRing(label: String, bps: Long, history: List<Float>, color: Color) {
    val cs = MaterialTheme.colorScheme
    val (value, unit) = formatRate(bps)
    val peak = (history.maxOrNull() ?: 0f).coerceAtLeast(1f)
    val fraction = (bps.toFloat() / peak).coerceIn(0f, 1f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(86.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 8.dp.toPx()
                drawCircle(cs.surfaceContainerHigh, style = Stroke(stroke))
                drawArc(
                    color = color, startAngle = -90f, sweepAngle = 360f * fraction,
                    useCenter = false, style = Stroke(stroke),
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value, style = MaterialTheme.typography.titleMedium, color = cs.onSurface, fontWeight = FontWeight.SemiBold)
                Text(unit, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
    }
}

@Composable
private fun Sparkline(history: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (history.size < 2) return@Canvas
        val peak = (history.maxOrNull() ?: 0f).coerceAtLeast(1f)
        val stepX = size.width / (history.size - 1)
        val path = Path()
        history.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - (v / peak) * size.height * 0.92f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(2.dp.toPx()))
    }
}

/** The Old design's connect controls (Pill + the four Alt shapes). */
@Composable
fun OldConnectControl(
    style: ConnectButtonStyle,
    state: BentoConnectState,
    personalization: Personalization,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val live = state == BentoConnectState.Connected
    val container = when {
        live -> personalization.connectedColor ?: cs.primary
        else -> personalization.connectColor ?: cs.primary
    }
    val content = cs.onPrimary
    when (style) {
        ConnectButtonStyle.Hero -> Unit // the hero itself is the control
        ConnectButtonStyle.Pill -> Row(
            modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(CircleShape)
                .background(container)
                .clickable(role = Role.Button, onClick = onToggle)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                if (live) s("Disconnect") else s("Connect"),
                style = MaterialTheme.typography.titleMedium, color = content,
            )
        }
        ConnectButtonStyle.Ring -> Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val rotation = remember { Animatable(0f) }
            LaunchedEffect(state) {
                if (state == BentoConnectState.Connecting) {
                    rotation.snapTo(0f)
                    rotation.animateTo(360f, tween(1100, easing = LinearEasing))
                }
            }
            Box(
                Modifier.size(96.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 6.dp.toPx()
                    rotate(rotation.value) {
                        drawArc(
                            color = container, startAngle = 0f,
                            sweepAngle = if (live) 360f else 250f,
                            useCenter = false, style = Stroke(stroke),
                            topLeft = Offset(stroke / 2, stroke / 2),
                            size = Size(size.width - stroke, size.height - stroke),
                        )
                    }
                }
                Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, tint = container, modifier = Modifier.size(34.dp))
            }
        }
        ConnectButtonStyle.Bar -> Box(
            modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(CircleShape)
                .background(cs.surfaceContainerHigh)
                .clickable(role = Role.Button, onClick = onToggle),
        ) {
            val fill by animateFloatAsState(if (live) 1f else 0f, tween(450), label = "bar-fill")
            Box(
                Modifier.fillMaxSize().clip(CircleShape),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fill.coerceAtLeast(0.02f))
                        .height(14.dp)
                        .clip(CircleShape)
                        .background(container),
                )
            }
        }
        ConnectButtonStyle.Icon -> Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(container)
                    .clickable(role = Role.Button, onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, tint = content, modifier = Modifier.size(30.dp))
            }
        }
        ConnectButtonStyle.Switch -> Row(
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(cs.surfaceContainerHigh)
                .clickable(role = Role.Button, onClick = onToggle)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (live) s("Connected") else s("Disconnected"),
                    style = MaterialTheme.typography.titleMedium, color = cs.onSurface,
                )
                Text(
                    if (live) s("Tap to disconnect") else s("Tap to connect"),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
            }
            Switch(checked = live, onCheckedChange = { onToggle() })
        }
    }
}

/** Expanding particle burst overlay (title 3-tap / connect celebration). */
@Composable
fun ParticleBurst(trigger: Int, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val colors = listOf(cs.primary, cs.tertiary, cs.secondary, cs.tertiaryContainer, cs.secondaryContainer)
    val progress = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(950, easing = FastOutSlowInEasing))
    }
    Canvas(modifier) {
        val p = progress.value
        if (p >= 1f) return@Canvas
        val bit = 5.dp.toPx()
        val reach = size.minDimension * 0.9f
        for (i in 0 until 16) {
            val a = i * (2f * PI.toFloat() / 16) + 0.35f
            val d = size.minDimension * 0.42f + reach * p
            val pos = Offset(center.x + cos(a) * d, center.y + sin(a) * d)
            val color = colors[i % colors.size].copy(alpha = (1f - p).coerceIn(0f, 1f))
            val sz = bit * (1f - p * 0.35f)
            if (i % 2 == 0) {
                drawCircle(color, sz, pos)
            } else {
                drawRoundRect(color, pos - Offset(sz, sz), Size(sz * 2f, sz * 2f), CornerRadius(sz * 0.6f))
            }
        }
    }
}

/**
 * Bouncing laser overlay (status 3-tap) — a light streak ricocheting
 * inside the screen for a few seconds, then gone. Clean-room: simple
 * ballistic segment with wall reflections, drawn with a fading trail.
 */
@Composable
fun LaserBounce(trigger: Int, color: Color, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(2600, easing = LinearEasing))
    }
    Canvas(modifier) {
        val p = progress.value
        if (p >= 1f || size.width <= 0f || size.height <= 0f) return@Canvas
        // Ballistic point with reflections, integrated analytically per frame slice.
        val total = 90 // segments of travel
        val upto = (p * total).toInt().coerceAtLeast(2)
        var x = size.width * 0.12f
        var y = size.height * 0.2f
        var vx = size.width * 0.011f
        var vy = size.height * 0.0075f
        var prev = Offset(x, y)
        for (i in 0 until upto) {
            x += vx; y += vy
            if (x < 0f || x > size.width) { vx = -vx; x = x.coerceIn(0f, size.width) }
            if (y < 0f || y > size.height) { vy = -vy; y = y.coerceIn(0f, size.height) }
            val cur = Offset(x, y)
            val fade = i.toFloat() / upto
            drawLine(color.copy(alpha = 0.75f * fade * (1f - p * 0.6f)), prev, cur, strokeWidth = 3.dp.toPx())
            prev = cur
        }
        drawCircle(color, radius = 4.dp.toPx(), center = prev)
    }
}

/** Logs affordance used by the Pixel/Old top bars. */
@Composable
fun LogsButton(onShowLogs: () -> Unit) {
    IconButton(onClick = onShowLogs) {
        Icon(Icons.Filled.Description, contentDescription = s("Logs"))
    }
}

/** Elapsed text for heroes (leaf ticker via rememberLiveSession). */
@Composable
fun HeroElapsed(live: Boolean): String {
    val session = rememberLiveSession(live)
    return if (live) formatElapsed(session.elapsedSeconds) else "--:--"
}
