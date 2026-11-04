package com.vauth.foxyvpn.ui.designs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vauth.foxyvpn.ui.bento.BentoConnectState
import com.vauth.foxyvpn.ui.bento.HomeUiState
import com.vauth.foxyvpn.ui.bento.formatBytesBento
import com.vauth.foxyvpn.ui.bento.formatElapsed
import com.vauth.foxyvpn.ui.l10n.s
import com.vauth.foxyvpn.ui.theme.AppearanceController
import com.vauth.foxyvpn.ui.theme.Personalization
import com.vauth.foxyvpn.ui.theme.SessionGradients

/**
 * The Pixel home design (Phase B2) — ZedSecure v3.1.8's PixelHome
 * anatomy on the fork's seam: top bar with the egg tap counter, the
 * big rounded hero card whose morphing "door" IS the connect control
 * (tap toggles, long-press bursts), fact chips with an expandable
 * details panel while connected, then the pinned server row and
 * traffic stats. Same [HomeUiState], same callbacks as BentoHome;
 * live values collect only in leaf composables.
 */
@Composable
fun PixelHome(
    ui: HomeUiState,
    animate: Boolean,
    appearance: AppearanceController,
    personalization: Personalization,
    contentPadding: PaddingValues,
    onToggleConnection: () -> Unit,
    onBrowseServers: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenSpeedTest: (() -> Unit)?,
    onOpenMap: (() -> Unit)?,
    onRefreshInfo: () -> Unit,
    onSecretUnlocked: () -> Unit,
    onShowLogs: () -> Unit,
    onBurst: () -> Unit,
    onLaser: () -> Unit,
    dock: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val live = ui.connectState == BentoConnectState.Connected
    val searching = ui.connectState == BentoConnectState.Connecting
    val onTitleTap = rememberTitleTaps(onSecretUnlocked, onBurst)
    val scroll = rememberScrollState()

    var celebrate by remember { mutableIntStateOf(0) }
    var previous by remember { mutableStateOf(ui.connectState) }
    LaunchedEffect(ui.connectState) {
        if (ui.connectState == BentoConnectState.Connected &&
            previous == BentoConnectState.Connecting
        ) {
            celebrate++
        }
        previous = ui.connectState
    }

    Box(modifier.fillMaxSize().background(cs.surface)) {
        Column(Modifier.fillMaxSize().padding(contentPadding)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scroll),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 16.dp)) {
                    // PortalTopBar
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(cs.primaryContainer)
                                .clickable(role = Role.Button, onClick = onTitleTap),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "F",
                                style = MaterialTheme.typography.titleMedium,
                                color = cs.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "FoxyVPN",
                            style = MaterialTheme.typography.titleLarge,
                            color = cs.onSurface,
                            modifier = Modifier.clickable(role = Role.Button, onClick = onTitleTap),
                        )
                        Spacer(Modifier.weight(1f))
                        LogsButton(onShowLogs)
                    }

                    if (ui.faultTitle != null && live) {
                        FaultCard(ui.faultTitle, ui.faultHint, onShowLogs)
                        Spacer(Modifier.height(10.dp))
                    }

                    if (ui.connectState == BentoConnectState.Error) {
                        ErrorCard(
                            error = ui.error,
                            onRetry = onToggleConnection,
                            onOtherServer = onBrowseServers,
                            onShowLogs = onShowLogs,
                        )
                    } else {
                        PixelHeroCard(
                            ui = ui,
                            animate = animate,
                            celebrate = celebrate,
                            onToggle = onToggleConnection,
                            onLongPress = onBurst,
                            onLaser = onLaser,
                            onRefreshInfo = onRefreshInfo,
                        )
                    }

                    if (live && ui.localProxyAddress != null) {
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            LocalProxyChip(ui.localProxyAddress)
                        }
                    }

                    ui.quota?.let { quota ->
                        Spacer(Modifier.height(10.dp))
                        QuotaCard(quota.title, quota.usageText, quota.caption, onOpenAccounts)
                    }

                    Spacer(Modifier.height(10.dp))
                    DesignChips(
                        connected = live,
                        onOpenSpeedTest = onOpenSpeedTest,
                        onOpenMap = onOpenMap,
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ServerCardRow(
                    name = ui.active?.name,
                    detail = ui.active?.detail,
                    personalization = personalization,
                    onClick = onBrowseServers,
                )
                if (appearance.showTrafficTiles) {
                    TrafficStats(
                        live = live,
                        style = appearance.trafficCardStyle,
                        size = appearance.trafficTileSize,
                        personalization = personalization,
                    )
                }
                Spacer(Modifier.height(2.dp))
            }
            dock()
        }
    }
}

@Composable
private fun PixelHeroCard(
    ui: HomeUiState,
    animate: Boolean,
    celebrate: Int,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
    onLaser: () -> Unit,
    onRefreshInfo: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val live = ui.connectState == BentoConnectState.Connected
    val searching = ui.connectState == BentoConnectState.Connecting
    val container by animateColorAsState(
        if (live) cs.primaryContainer else cs.surfaceContainer,
        tween(450), label = "pixel-hero-container",
    )
    val ink by animateColorAsState(
        if (live) cs.onPrimaryContainer else cs.onSurface,
        tween(450), label = "pixel-hero-ink",
    )
    val sub by animateColorAsState(
        if (live) cs.onPrimaryContainer.copy(alpha = 0.78f) else cs.onSurfaceVariant,
        tween(450), label = "pixel-hero-sub",
    )
    var details by remember { mutableStateOf(false) }
    val onStatusTap = rememberStatusTaps(onLaser)

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(44.dp))
            .background(container),
    ) {
        // Door sizing follows the reference card: the door is a
        // fraction of the card and shrinks when the details panel
        // opens, so the facts take the stage (rescue B).
        val open = live && details
        val full = minOf(maxWidth * 0.52f, 208.dp).coerceIn(116.dp, 208.dp)
        val small = minOf(maxWidth * 0.26f, 100.dp).coerceIn(64.dp, 100.dp)
        val door by animateDpAsState(
            if (open) small else full,
            spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
            label = "pixel-door-size",
        )
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 340.dp)
                .padding(horizontal = 18.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
        PixelDoor(
            state = ui.connectState,
            animate = animate,
            celebrate = celebrate,
            doorSize = door,
            onTap = onToggle,
            onLongPress = onLongPress,
        )
        Spacer(Modifier.height(16.dp))
        AnimatedContent(
            targetState = ui.connectState,
            transitionSpec = {
                (fadeIn(tween(280)) + slideInVertically { it / 3 }) togetherWith
                    fadeOut(tween(160))
            },
            label = "pixel-state",
        ) { state ->
            Text(
                connectStatusText(state, ui.error),
                style = MaterialTheme.typography.headlineMediumEmphasized,
                color = ink,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.clickable(role = Role.Button, onClick = onStatusTap),
            )
        }
        Spacer(Modifier.height(8.dp))
        when {
            ui.connectState == BentoConnectState.Idle -> Text(
                s("Tap the button to connect"),
                style = MaterialTheme.typography.bodyMedium,
                color = sub,
                textAlign = TextAlign.Center,
            )
            searching -> {
                LinearProgressIndicator(
                    modifier = Modifier.width(156.dp),
                    color = cs.primary,
                    trackColor = cs.primary.copy(alpha = 0.18f),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    s("Tap to cancel"),
                    style = MaterialTheme.typography.bodySmall,
                    color = sub,
                )
            }
            live -> {
                PixelFacts(ui = ui, ink = ink, sub = sub, onToggleDetails = { details = !details })
                if (details) {
                    Spacer(Modifier.height(10.dp))
                    PixelDetails(ui = ui, sub = sub, live = live, onRefreshInfo = onRefreshInfo)
                }
            }
        }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PixelDoor(
    state: BentoConnectState,
    animate: Boolean,
    celebrate: Int,
    doorSize: androidx.compose.ui.unit.Dp,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val live = state == BentoConnectState.Connected
    val searching = state == BentoConnectState.Connecting
    val session = rememberLiveSession(live)

    // While searching, the door cycles the reference's search shapes
    // (one step every 0.72 s); connected settles on SoftBurst, idle
    // on Cookie12Sided. Target changes morph from the current polygon
    // with the reference spring; press feedback is a 0.9 squash with
    // no ripple (rescue B).
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(searching, animate) {
        if (!searching || !animate) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(720)
            step++
        }
    }
    val target: androidx.graphics.shapes.RoundedPolygon = when {
        live -> MaterialShapes.SoftBurst
        searching -> SearchShapes[step % SearchShapes.size]
        else -> MaterialShapes.Cookie12Sided
    }
    var from by remember {
        mutableStateOf<androidx.graphics.shapes.RoundedPolygon>(MaterialShapes.Cookie12Sided)
    }
    var to by remember {
        mutableStateOf<androidx.graphics.shapes.RoundedPolygon>(MaterialShapes.Cookie12Sided)
    }
    var progress by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(target) {
        if (target === to) return@LaunchedEffect
        from = if (progress >= 0.5f) to else from
        to = target
        progress = 0f
        androidx.compose.animation.core.animate(
            0f, 1f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 260f),
        ) { v, _ -> progress = v }
    }
    val morph = remember(from, to) { androidx.graphics.shapes.Morph(from, to) }

    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val squash by animateFloatAsState(
        if (pressed) 0.9f else 1f,
        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "pixel-door-squash",
    )
    // Rotation rates follow the reference clock: fast sweep while
    // searching (150 deg/s), a slow 9 deg/s drift while connected.
    val turn = if (animate) {
        val transition = rememberInfiniteTransition(label = "pixel-door-turn")
        val value by transition.animateFloat(
            0f, 360f,
            infiniteRepeatable(tween(if (searching) 2_400 else 40_000, easing = LinearEasing)),
            label = "pixel-door-angle",
        )
        value
    } else {
        0f
    }
    val work = remember { androidx.compose.ui.graphics.Path() }
    val density = androidx.compose.ui.platform.LocalDensity.current
    Box(
        Modifier
            .size(doorSize)
            .graphicsLayer {
                scaleX = squash
                scaleY = squash
            }
            .combinedClickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClick = onTap,
                onLongClick = onLongPress,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val brush: Brush = when {
                searching -> Brush.sweepGradient(
                    listOf(
                        SessionGradients.Violet,
                        SessionGradients.HotPink,
                        SessionGradients.Violet,
                    ),
                    center = center,
                )
                live -> SessionGradients.brushFor(SessionGradients.connectedStops(session.sessionKey))
                else -> SessionGradients.idle
            }
            work.rewind()
            val path = morph.toPath(progress.coerceIn(0f, 1f), work)
            path.transform(
                androidx.compose.ui.graphics.Matrix().apply { scale(size.width, size.height) },
            )
            val bounds = path.getBounds()
            path.translate(
                androidx.compose.ui.geometry.Offset(
                    size.width / 2f - bounds.center.x,
                    size.height / 2f - bounds.center.y,
                ),
            )
            rotate(turn) { drawPath(path, brush) }
        }
        if (live) {
            Text(
                formatElapsed(session.elapsedSeconds),
                style = MaterialTheme.typography.headlineSmallEmphasized.copy(
                    fontSize = with(density) { (doorSize * 0.14f).toSp() },
                ),
                color = cs.onPrimary,
                maxLines = 1,
                softWrap = false,
            )
        } else {
            Icon(
                Icons.Filled.PowerSettingsNew,
                contentDescription = if (searching) s("Cancel") else s("Connect"),
                tint = cs.onPrimary,
                modifier = Modifier.size(doorSize * 0.32f),
            )
        }
        ParticleBurst(trigger = celebrate, modifier = Modifier.fillMaxSize())
    }
}

/** The reference's searching cycle for the door (MaterialShapes set). */
private val SearchShapes: List<androidx.graphics.shapes.RoundedPolygon> = listOf(
    MaterialShapes.SoftBurst,
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Pentagon,
    MaterialShapes.Pill,
    MaterialShapes.Sunny,
    MaterialShapes.Cookie4Sided,
    MaterialShapes.Oval,
)

@Composable
private fun PixelFacts(
    ui: HomeUiState,
    ink: androidx.compose.ui.graphics.Color,
    sub: androidx.compose.ui.graphics.Color,
    onToggleDetails: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onToggleDetails)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FactText(s("Server"), ui.active?.name ?: "—", ink, sub)
        FactText(s("Ping"), ui.pingMs?.let { "$it ms" } ?: "—", ink, sub)
        ui.quota?.let { FactText(s("Left"), it.usageText, ink, sub) }
    }
}

@Composable
private fun FactText(
    label: String,
    value: String,
    ink: androidx.compose.ui.graphics.Color,
    sub: androidx.compose.ui.graphics.Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall, color = ink, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = sub, maxLines = 1)
    }
}

@Composable
private fun PixelDetails(
    ui: HomeUiState,
    sub: androidx.compose.ui.graphics.Color,
    live: Boolean,
    onRefreshInfo: () -> Unit,
) {
    val session = rememberLiveSession(live)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ui.active?.detail?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = sub)
        }
        Text(
            s("Data this session") + ": " + formatBytesBento(session.sessionBytes),
            style = MaterialTheme.typography.bodySmall,
            color = sub,
        )
        TextButton(onClick = onRefreshInfo) { Text(s("Refresh")) }
    }
}

@Composable
internal fun FaultCard(title: String, hint: String?, onShowLogs: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(cs.errorContainer)
            .clickable(role = Role.Button, onClick = onShowLogs)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = cs.onErrorContainer)
        if (!hint.isNullOrBlank()) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = cs.onErrorContainer.copy(alpha = 0.8f))
        }
    }
}

@Composable
internal fun ErrorCard(
    error: String?,
    onRetry: () -> Unit,
    onOtherServer: () -> Unit,
    onShowLogs: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(cs.errorContainer)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text(s("Connection failed"), style = MaterialTheme.typography.titleMedium, color = cs.onErrorContainer)
        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(error, style = MaterialTheme.typography.bodySmall, color = cs.onErrorContainer.copy(alpha = 0.85f))
        }
        Spacer(Modifier.height(8.dp))
        Row {
            TextButton(onClick = onRetry) { Text(s("Retry")) }
            TextButton(onClick = onOtherServer) { Text(s("Servers")) }
            TextButton(onClick = onShowLogs) { Text(s("Logs")) }
        }
    }
}

@Composable
internal fun QuotaCard(title: String, usageText: String, caption: String?, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(cs.secondaryContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = cs.onSecondaryContainer, maxLines = 1)
        Text(usageText, style = MaterialTheme.typography.titleMedium, color = cs.onSecondaryContainer)
        if (!caption.isNullOrBlank()) {
            Text(caption, style = MaterialTheme.typography.bodySmall, color = cs.onSecondaryContainer.copy(alpha = 0.75f))
        }
    }
}
