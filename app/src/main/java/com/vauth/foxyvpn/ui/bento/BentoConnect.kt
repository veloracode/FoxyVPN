package com.vauth.foxyvpn.ui.bento

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vauth.foxyvpn.ui.l10n.s
import com.vauth.foxyvpn.ui.theme.Bento
import com.vauth.foxyvpn.ui.theme.BentoConnect
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The Bento connect controls — Phase B1 of the Zed overhaul.
 *
 * Six morphologies (Slide, Button, Morph, Rail, Hold, Tile) over the
 * fork's connection state, mapped to [BentoConnectState] at the Home
 * seam: the service's three states plus Error (from the error flow) —
 * the fork has no disconnecting/reconnecting states, so those labels
 * never appear (the variants still handle Disconnecting for parity).
 * Presentation only: every variant calls the same onToggle.
 */
enum class BentoConnectState { Idle, Connecting, Connected, Disconnecting, Error }

val BentoConnectState.busy: Boolean
    get() = this == BentoConnectState.Connecting

/** Haptic sink honoring the fork's haptic-feedback switch (DEV-12). */
val LocalBentoHaptics = compositionLocalOf<(HapticFeedbackType) -> Unit> { {} }

@Composable
fun stateLabel(state: BentoConnectState): String = s(
    when (state) {
        BentoConnectState.Connected -> "Connected"
        BentoConnectState.Connecting -> "Connecting…"
        BentoConnectState.Disconnecting -> "Disconnecting…"
        BentoConnectState.Error -> "Connection failed"
        BentoConnectState.Idle -> "Not connected"
    },
)

@Composable
fun actionLabel(state: BentoConnectState): String = s(
    when (state) {
        BentoConnectState.Connected -> "Disconnect"
        BentoConnectState.Connecting -> "Cancel"
        else -> "Connect"
    },
)

@Composable
private fun tapHint(state: BentoConnectState): String = s(
    when {
        state.busy -> "Tap to cancel"
        state == BentoConnectState.Connected -> "Tap to disconnect"
        else -> "Tap to connect"
    },
)

@Composable
private fun PowerGlyph(tint: Color, size: androidx.compose.ui.unit.Dp) {
    Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, tint = tint, modifier = Modifier.size(size))
}

@Composable
private fun spinning(active: Boolean, millis: Int): Float {
    if (!active) return 0f
    val transition = rememberInfiniteTransition(label = "connect-spin")
    val turn by transition.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(millis, easing = LinearEasing), RepeatMode.Restart),
        label = "connect-spin-turn",
    )
    return turn
}

@Composable
fun BentoConnectControl(
    state: BentoConnectState,
    onToggle: () -> Unit,
    animate: Boolean,
    modifier: Modifier = Modifier,
    elapsedSeconds: Int = 0,
    serverName: String? = null,
) {
    when (Bento.prefs.connect) {
        BentoConnect.Slide -> BentoConnectBar(state, onToggle, animate, modifier)
        BentoConnect.Button -> BentoConnectButton(state, onToggle, modifier)
        BentoConnect.Morph -> BentoConnectMorph(state, onToggle, animate, elapsedSeconds, serverName, modifier)
        BentoConnect.Rail -> BentoConnectRail(state, onToggle, animate, elapsedSeconds, modifier)
        BentoConnect.Hold -> BentoConnectHold(state, onToggle, animate, elapsedSeconds, serverName, modifier)
        BentoConnect.Tile -> BentoConnectTile(state, onToggle, animate, elapsedSeconds, serverName, modifier)
    }
}

// ---------------------------------------------------------------------
// Slide (the default)
// ---------------------------------------------------------------------

private const val THRESHOLD = 0.72f
private val WAVE_AMP = 4.5.dp
private val WAVE_LENGTH = 32.dp

internal fun waveLength(range: Float, preferred: Float): Float {
    val halves = (range / (preferred / 2f)).roundToInt().coerceAtLeast(1)
    return 2f * range / halves
}

internal fun waveOffset(distance: Float, amp: Float, wavelength: Float): Float =
    amp * sin(2f * PI.toFloat() * distance / wavelength)

@Composable
private fun wavePhase(): State<Float> {
    val transition = rememberInfiniteTransition(label = "connect-wave")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "connect-wave-phase",
    )
}

@Composable
private fun WaveTrack(
    progress: () -> Float,
    color: Color,
    knob: Float,
    amp: Float,
    wavelength: Float,
    rtl: Boolean,
    phase: State<Float>?,
    connected: Boolean,
) {
    val path = remember { Path() }
    Canvas(Modifier.fillMaxSize()) {
        val p = progress()
        val width = size.width
        val mid = size.height / 2f
        val stroke = 4.5.dp.toPx()
        val start = knob / 2f
        val end = width - knob / 2f
        val knobCenter = start + p * (width - knob)
        fun build(from: Float, to: Float) {
            path.reset()
            var x = from
            path.moveTo(x, mid + waveOffset(x - start, amp, wavelength))
            while (x < to) {
                x = (x + 2f).coerceAtMost(to)
                path.lineTo(x, mid + waveOffset(x - start, amp, wavelength))
            }
        }
        scale(if (rtl) -1f else 1f, 1f, pivot = Offset(width / 2f, mid)) {
            if (knobCenter < end - 1f) {
                build(knobCenter, end)
                val dash = phase?.value
                drawPath(
                    path,
                    color.copy(alpha = if (dash != null) 0.9f else 0.3f),
                    style = Stroke(
                        width = stroke,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = dash?.let {
                            PathEffect.dashPathEffect(
                                floatArrayOf(10.dp.toPx(), 9.dp.toPx()),
                                -it * 19.dp.toPx(),
                            )
                        },
                    ),
                )
            }
            if (knobCenter > start + 1f) {
                build(start, knobCenter)
                drawPath(
                    path,
                    color.copy(alpha = if (connected) 1f else 0.85f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}

@Composable
fun BentoConnectBar(
    state: BentoConnectState,
    onToggle: () -> Unit,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = Bento.colors
    val fire = LocalBentoHaptics.current
    val scope = rememberCoroutineScope()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val busy = state.busy
    val engaged = busy || state == BentoConnectState.Connected
    val locked = state == BentoConnectState.Disconnecting
    val rest = if (engaged) 1f else 0f

    var position by remember { mutableFloatStateOf(rest) }
    var dragging by remember { mutableStateOf(false) }
    var settle by remember { mutableStateOf<Job?>(null) }

    fun settleTo(target: Float) {
        settle?.cancel()
        settle = scope.launch {
            animate(position, target, animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) { v, _ ->
                position = v
            }
        }
    }

    LaunchedEffect(rest) { if (!dragging) settleTo(rest) }

    val hint = s(
        when {
            busy -> "Tap to cancel"
            state == BentoConnectState.Connected -> "Slide to disconnect"
            state == BentoConnectState.Error -> "Slide to try again"
            else -> "Slide to connect"
        },
    )
    val label = stateLabel(state)
    val action = actionLabel(state)
    val dot by animateColorAsState(
        when (state) {
            BentoConnectState.Connected -> c.stage
            BentoConnectState.Error -> c.bad
            BentoConnectState.Connecting -> c.stage.copy(alpha = 0.65f)
            else -> c.onLime.copy(alpha = 0.4f)
        },
        label = "connect-dot",
    )

    Column(
        modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(BentoShapes.block())
            .background(c.lime)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                stateDescription = label
                onClick(action) {
                    if (!locked) onToggle()
                    true
                }
            }
            .padding(start = 14.dp, end = 14.dp, top = 9.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                color = c.onLime,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                hint,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = c.onLime.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(44.dp)) {
            val density = LocalDensity.current
            val knob = 40.dp
            val range = with(density) { (maxWidth - knob).toPx().coerceAtLeast(1f) }
            val wave = Bento.prefs.wave
            val amp = with(density) { WAVE_AMP.toPx() }
            val wavelength = with(density) { waveLength(range, WAVE_LENGTH.toPx()) }
            val draggableState = rememberDraggableState { delta ->
                if (!busy && !locked) position = (position + delta / range).coerceIn(0f, 1f)
            }
            val phase = if (busy && animate) wavePhase() else null
            val tap = rememberUpdatedState {
                when {
                    locked -> Unit
                    busy -> {
                        fire(HapticFeedbackType.LongPress)
                        onToggle()
                    }
                    else -> {
                        fire(HapticFeedbackType.TextHandleMove)
                        val nudge = if (engaged) rest - 0.14f else rest + 0.14f
                        settle?.cancel()
                        settle = scope.launch {
                            animate(position, nudge, animationSpec = tween(140)) { v, _ -> position = v }
                            animate(position, rest, animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)) { v, _ -> position = v }
                        }
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures(onTap = { tap.value() }) }
                    .draggable(
                        state = draggableState,
                        orientation = Orientation.Horizontal,
                        enabled = !busy && !locked,
                        reverseDirection = rtl,
                        onDragStarted = {
                            settle?.cancel()
                            dragging = true
                        },
                        onDragStopped = {
                            dragging = false
                            val crossed = if (engaged) position < 1f - THRESHOLD else position > THRESHOLD
                            if (crossed) {
                                fire(HapticFeedbackType.LongPress)
                                settleTo(1f - rest)
                                onToggle()
                            } else {
                                settleTo(rest)
                            }
                        },
                    ),
            ) {
                if (wave) {
                    WaveTrack(
                        progress = { position },
                        color = c.onLime,
                        knob = with(density) { knob.toPx() },
                        amp = amp,
                        wavelength = wavelength,
                        rtl = rtl,
                        phase = phase,
                        connected = state == BentoConnectState.Connected,
                    )
                }
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset { IntOffset((position * range).roundToInt(), 0) }
                        .graphicsLayer {
                            translationY = if (wave) waveOffset(position * range, amp, wavelength) else 0f
                        }
                        .size(knob)
                        .clip(CircleShape)
                        .background(c.onLime),
                    contentAlignment = Alignment.Center,
                ) {
                    PowerGlyph(c.lime, 20.dp)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
// Button
// ---------------------------------------------------------------------

@Composable
fun BentoConnectButton(
    state: BentoConnectState,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Bento.colors
    val fire = LocalBentoHaptics.current
    val connected = state == BentoConnectState.Connected
    val busy = state.busy
    val bg by animateColorAsState(if (connected) c.stage else c.lime, label = "connect-btn-bg")
    val fg by animateColorAsState(if (connected) c.onStage else c.onLime, label = "connect-btn-fg")
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val squash by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.6f), label = "connect-btn-squash")
    val label = stateLabel(state)
    val action = actionLabel(state)
    Row(
        modifier
            .fillMaxWidth()
            .height(72.dp)
            .graphicsLayer {
                scaleX = squash
                scaleY = squash
            }
            .clip(BentoShapes.block())
            .background(bg)
            .semantics { stateDescription = label }
            .clickable(
                source, ripple(color = fg),
                enabled = state != BentoConnectState.Disconnecting,
                role = Role.Button, onClickLabel = action,
            ) {
                fire(HapticFeedbackType.LongPress)
                onToggle()
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(fg),
            contentAlignment = Alignment.Center,
        ) {
            PowerGlyph(bg, 24.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                action,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = fg.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (busy) {
            CircularProgressIndicator(Modifier.size(22.dp), color = fg, strokeWidth = 2.5.dp)
        }
    }
}

// ---------------------------------------------------------------------
// Morph
// ---------------------------------------------------------------------

@Composable
fun BentoConnectMorph(
    state: BentoConnectState,
    onToggle: () -> Unit,
    animate: Boolean,
    elapsedSeconds: Int,
    serverName: String?,
    modifier: Modifier = Modifier,
) {
    val c = Bento.colors
    val fire = LocalBentoHaptics.current
    val connected = state == BentoConnectState.Connected
    val container by animateColorAsState(if (connected) c.stage else c.surface, label = "morph-bg")
    val ink by animateColorAsState(if (connected) c.onStage else c.ink, label = "morph-ink")
    val morph by animateFloatAsState(
        if (connected || state.busy) 1f else 0f,
        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow),
        label = "morph-progress",
    )
    val turn = spinning(animate && state.busy, 2400)
    val drift = spinning(animate && connected && Bento.prefs.playfulTouch, 24000)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val squash by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.5f), label = "morph-press")
    val label = stateLabel(state)
    Row(
        modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(BentoShapes.block())
            .background(container)
            .semantics { stateDescription = label }
            .clickable(
                source, ripple(color = ink),
                enabled = state != BentoConnectState.Disconnecting,
                role = Role.Button, onClickLabel = actionLabel(state),
            ) {
                fire(HapticFeedbackType.LongPress)
                onToggle()
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MorphingBlob(
            progress = morph,
            color = c.lime,
            startShape = MaterialShapes.Cookie9Sided,
            endShape = MaterialShapes.SoftBurst,
            modifier = Modifier
                .size(90.dp)
                .graphicsLayer {
                    scaleX = squash
                    scaleY = squash
                    rotationZ = turn + drift
                },
        ) {
            Box(Modifier.graphicsLayer { rotationZ = -(turn + drift) }) { PowerGlyph(c.onLime, 34.dp) }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp, fontWeight = FontWeight.Bold),
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (connected) ltrIsolate(formatElapsed(elapsedSeconds)) else serverName ?: tapHint(state),
                style = BentoType.number(15.sp),
                color = ink.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (connected || state.busy) {
                Text(tapHint(state), style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.65f), maxLines = 1)
            }
        }
    }
}

// ---------------------------------------------------------------------
// Rail
// ---------------------------------------------------------------------

@Composable
fun BentoConnectRail(
    state: BentoConnectState,
    onToggle: () -> Unit,
    animate: Boolean,
    elapsedSeconds: Int,
    modifier: Modifier = Modifier,
) {
    val c = Bento.colors
    val fire = LocalBentoHaptics.current
    val on = state == BentoConnectState.Connected || state.busy
    val track by animateColorAsState(if (on) c.lime else c.surface2, label = "rail-track")
    val thumb by animateColorAsState(if (on) c.onLime else c.lime, label = "rail-thumb")
    val glyph by animateColorAsState(if (on) c.lime else c.onLime, label = "rail-glyph")
    val travel by animateFloatAsState(
        if (on) 1f else 0f,
        spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "rail-travel",
    )
    val phase = spinning(animate && state.busy, 900) / 360f
    val label = stateLabel(state)
    val action = actionLabel(state)
    val text = when {
        state == BentoConnectState.Connected -> formatElapsed(elapsedSeconds)
        state.busy -> label
        else -> tapHint(state)
    }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(40.dp))
            .background(track)
            .semantics(mergeDescendants = true) {
                role = Role.Switch
                stateDescription = label
                onClick(action) {
                    if (state != BentoConnectState.Disconnecting) onToggle()
                    true
                }
            }
            .clickable(
                enabled = state != BentoConnectState.Disconnecting,
                role = Role.Switch, onClickLabel = action,
            ) {
                fire(HapticFeedbackType.LongPress)
                onToggle()
            }
            .padding(8.dp),
    ) {
        val knob = 64.dp
        val distance = (maxWidth - knob).coerceAtLeast(0.dp)
        if (on && Bento.prefs.wave) {
            Canvas(Modifier.fillMaxSize().padding(horizontal = 74.dp)) {
                val mid = size.height / 2f
                val amp = 6.dp.toPx()
                val length = 30.dp.toPx()
                val shift = phase * length
                val wave = Path()
                var x = 0f
                wave.moveTo(0f, mid + amp * sin(2f * PI.toFloat() * shift / length))
                while (x < size.width) {
                    x = (x + 2f).coerceAtMost(size.width)
                    wave.lineTo(x, mid + amp * sin(2f * PI.toFloat() * (x + shift) / length))
                }
                drawPath(
                    wave, c.onLime.copy(alpha = if (state.busy) 0.55f else 0.9f),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        } else {
            Text(
                text,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (on) c.onLime else c.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 72.dp),
            )
        }
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = distance * travel)
                .size(knob)
                .clip(CircleShape)
                .background(thumb),
            contentAlignment = Alignment.Center,
        ) {
            PowerGlyph(glyph, 28.dp)
        }
    }
}

// ---------------------------------------------------------------------
// Hold
// ---------------------------------------------------------------------

private const val HOLD_MS = 650

@Composable
fun BentoConnectHold(
    state: BentoConnectState,
    onToggle: () -> Unit,
    animate: Boolean,
    elapsedSeconds: Int,
    serverName: String?,
    modifier: Modifier = Modifier,
) {
    val c = Bento.colors
    val fire = LocalBentoHaptics.current
    val scope = rememberCoroutineScope()
    val connected = state == BentoConnectState.Connected
    val container by animateColorAsState(if (connected) c.stage else c.lime, label = "hold-bg")
    val ink by animateColorAsState(if (connected) c.onStage else c.onLime, label = "hold-ink")
    val knobFill by animateColorAsState(if (connected) c.lime else c.onLime, label = "hold-knob")
    val glyph by animateColorAsState(if (connected) c.onLime else c.lime, label = "hold-glyph")
    val fill = remember { Animatable(0f) }
    val turn = spinning(animate && state.busy, 1100)
    val toggle by rememberUpdatedState(onToggle)
    val current by rememberUpdatedState(state)
    val label = stateLabel(state)
    val action = actionLabel(state)
    val title = when {
        connected -> s("Hold to disconnect")
        state.busy -> label
        else -> s("Hold to connect")
    }
    Row(
        modifier
            .fillMaxWidth()
            .height(104.dp)
            .clip(BentoShapes.block())
            .background(container)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                stateDescription = label
                onClick(action) {
                    if (state != BentoConnectState.Disconnecting) onToggle()
                    true
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    if (current == BentoConnectState.Disconnecting) return@awaitEachGesture
                    if (current.busy) {
                        val up = waitForUpOrCancellation()
                        if (up != null) {
                            fire(HapticFeedbackType.LongPress)
                            toggle()
                        }
                        return@awaitEachGesture
                    }
                    val run = scope.launch {
                        fill.snapTo(0f)
                        fill.animateTo(1f, tween(HOLD_MS, easing = LinearEasing))
                        fire(HapticFeedbackType.LongPress)
                        toggle()
                    }
                    waitForUpOrCancellation()
                    if (run.isActive) {
                        run.cancel()
                        scope.launch { fill.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                    } else {
                        scope.launch { fill.snapTo(0f) }
                    }
                }
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = turn }) {
                val stroke = 6.dp.toPx()
                val inset = stroke / 2f
                val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                drawCircle(knobFill, radius = size.minDimension / 2f - stroke)
                drawArc(ink.copy(alpha = 0.22f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                val sweep = when {
                    connected -> 360f
                    state.busy -> 90f
                    else -> 360f * fill.value
                }
                if (sweep > 0f) {
                    drawArc(
                        if (connected) c.lime else ink, -90f, sweep, false,
                        Offset(inset, inset), arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
            PowerGlyph(glyph, 30.dp)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (connected) {
                Text(ltrIsolate(formatElapsed(elapsedSeconds)), style = BentoType.number(26.sp), color = ink, maxLines = 1)
            } else {
                Text(
                    if (state.busy) s("Tap to cancel") else serverName ?: s("Let go early to cancel"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ink.copy(alpha = 0.78f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------
// Tile
// ---------------------------------------------------------------------

@Composable
fun BentoConnectTile(
    state: BentoConnectState,
    onToggle: () -> Unit,
    animate: Boolean,
    elapsedSeconds: Int,
    serverName: String?,
    modifier: Modifier = Modifier,
) {
    val c = Bento.colors
    val fire = LocalBentoHaptics.current
    val connected = state == BentoConnectState.Connected
    val container by animateColorAsState(if (connected) c.lime else c.stage, label = "tile-bg")
    val ink by animateColorAsState(if (connected) c.onLime else c.onStage, label = "tile-ink")
    val badge by animateColorAsState(if (connected) c.onLime else c.lime, label = "tile-badge")
    val badgeInk by animateColorAsState(if (connected) c.lime else c.onLime, label = "tile-badge-ink")
    val morph by animateFloatAsState(
        if (connected) 1f else 0f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
        label = "tile-morph",
    )
    val turn = spinning(animate && state.busy, 1800)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val squash by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.55f), label = "tile-press")
    val label = stateLabel(state)
    Box(
        modifier
            .fillMaxWidth()
            .height(132.dp)
            .graphicsLayer {
                scaleX = squash
                scaleY = squash
            }
            .clip(BentoShapes.tile(true))
            .background(container)
            .bentoMotif(MaterialShapes.Flower, ink, 190.dp, 18f, enabled = Bento.prefs.motifs, alpha = 0.09f)
            .semantics { stateDescription = label }
            .clickable(
                source, ripple(color = ink),
                enabled = state != BentoConnectState.Disconnecting,
                role = Role.Button, onClickLabel = actionLabel(state),
            ) {
                fire(HapticFeedbackType.LongPress)
                onToggle()
            }
            .padding(18.dp),
    ) {
        Column(
            Modifier.align(Alignment.TopStart).padding(end = 72.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(label, style = BentoType.caption(), color = ink.copy(alpha = 0.8f), maxLines = 1)
            if (connected) {
                Text(ltrIsolate(formatElapsed(elapsedSeconds)), style = BentoType.number(40.sp), color = ink, maxLines = 1)
            } else {
                Text(
                    actionLabel(state),
                    style = BentoType.title(34.sp),
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            if (connected || state.busy) tapHint(state) else serverName ?: tapHint(state),
            style = MaterialTheme.typography.bodyMedium,
            color = ink.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(end = 72.dp),
        )
        MorphingBlob(
            progress = morph,
            color = badge,
            startShape = MaterialShapes.Clover4Leaf,
            endShape = MaterialShapes.Cookie6Sided,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(64.dp)
                .graphicsLayer { rotationZ = turn },
        ) {
            Box(Modifier.graphicsLayer { rotationZ = -turn }) { PowerGlyph(badgeInk, 26.dp) }
        }
    }
}
