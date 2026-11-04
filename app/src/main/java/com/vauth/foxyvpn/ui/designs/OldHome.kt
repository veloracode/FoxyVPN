package com.vauth.foxyvpn.ui.designs

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vauth.foxyvpn.ui.bento.BentoConnectState
import com.vauth.foxyvpn.ui.bento.HomeUiState
import com.vauth.foxyvpn.ui.bento.formatElapsed
import com.vauth.foxyvpn.ui.l10n.s
import com.vauth.foxyvpn.ui.theme.AppearanceController
import com.vauth.foxyvpn.ui.theme.ConnectButtonStyle
import com.vauth.foxyvpn.ui.theme.Personalization
import com.vauth.foxyvpn.ui.theme.SessionGradients

/**
 * The Old home design (Phase B2) — ZedSecure v3.1.8's classic home
 * (the non-Bento, non-Pixel body of its HomeScreen): centered brand
 * header with the egg tap counter, a big circular hero, the active
 * server ("config") card, and the connect control in the style chosen
 * in Interface (Pill / Hero / Ring / Bar / Icon / Switch — with Hero,
 * the hero circle itself is the control). Traffic tiles honor the
 * Old-only Interface rows (show / above hero / style / size). Same
 * [HomeUiState] seam and callbacks as the other designs.
 */
@Composable
fun OldHome(
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
    val onStatusTap = rememberStatusTaps(onLaser)
    val scroll = rememberScrollState()
    val heroIsControl = appearance.connectButtonStyle == ConnectButtonStyle.Hero

    val backdropSession = rememberLiveSession(live)
    Box(modifier.fillMaxSize().background(cs.surface)) {
        ConnectingBackdrop(
            state = ui.connectState,
            sessionKey = backdropSession.sessionKey,
            animate = animate,
        )
        Column(Modifier.fillMaxSize().padding(contentPadding)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scroll),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // BrandHeader
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(Modifier.weight(1f))
                        Text(
                            "FoxyVPN",
                            style = MaterialTheme.typography.headlineSmall,
                            color = cs.onSurface,
                            fontWeight = FontWeight.SemiBold,
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
                        Spacer(Modifier.height(10.dp))
                    }

                    if (appearance.showTrafficTiles && appearance.trafficTilesAboveHero) {
                        TrafficStats(
                            live = live,
                            style = appearance.trafficCardStyle,
                            size = appearance.trafficTileSize,
                            personalization = personalization,
                        )
                        Spacer(Modifier.height(14.dp))
                    }

                    OldHero(
                        state = ui.connectState,
                        animate = animate,
                        isControl = heroIsControl,
                        onToggle = onToggleConnection,
                        onLongPress = onBurst,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        connectStatusText(ui.connectState, ui.error),
                        style = MaterialTheme.typography.titleLarge,
                        color = cs.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.clickable(role = Role.Button, onClick = onStatusTap),
                    )
                    if (searching) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            s("Tap to cancel"),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    }

                    ui.quota?.let { quota ->
                        Spacer(Modifier.height(14.dp))
                        QuotaCard(quota.title, quota.usageText, quota.caption, onOpenAccounts)
                    }

                    Spacer(Modifier.height(12.dp))
                    DesignChips(
                        connected = live,
                        onOpenSpeedTest = onOpenSpeedTest,
                        onOpenMap = onOpenMap,
                    )
                    if (live && ui.localProxyAddress != null) {
                        Spacer(Modifier.height(10.dp))
                        LocalProxyChip(ui.localProxyAddress)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ServerCardRow(
                    name = ui.active?.name,
                    detail = ui.active?.detail,
                    personalization = personalization,
                    onClick = onBrowseServers,
                )
                if (!heroIsControl) {
                    OldConnectControl(
                        style = appearance.connectButtonStyle,
                        state = ui.connectState,
                        personalization = personalization,
                        onToggle = onToggleConnection,
                    )
                }
                if (appearance.showTrafficTiles && !appearance.trafficTilesAboveHero) {
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

/**
 * Full-screen backdrop of the Old design (rescue B): a vertical
 * session-gradient wash whose intensity follows the connection
 * state, with four rings expanding from the hero's center while
 * motion is allowed. Idle draws nothing. Structure follows the
 * reference's ConnectingBackdrop; colors are our SessionGradients.
 */
@Composable
private fun ConnectingBackdrop(
    state: BentoConnectState,
    sessionKey: Long,
    animate: Boolean,
) {
    val intensity by androidx.compose.animation.core.animateFloatAsState(
        targetValue = when (state) {
            BentoConnectState.Connecting -> 0.72f
            BentoConnectState.Connected -> 1f
            BentoConnectState.Disconnecting -> 0.35f
            else -> 0f
        },
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 900,
            easing = androidx.compose.animation.core.FastOutSlowInEasing,
        ),
        label = "old-backdrop-intensity",
    )
    val pulse = if (animate && state != BentoConnectState.Idle && state != BentoConnectState.Error) {
        val transition = rememberInfiniteTransition(label = "old-backdrop-life")
        val value by transition.animateFloat(
            0f, 1f,
            infiniteRepeatable(
                androidx.compose.animation.core.tween(2600),
                androidx.compose.animation.core.RepeatMode.Restart,
            ),
            label = "old-backdrop-pulse",
        )
        value
    } else {
        0f
    }
    val stops = if (state == BentoConnectState.Connected) {
        SessionGradients.connectedStops(sessionKey)
    } else {
        SessionGradients.connectingStops
    }
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        if (intensity <= 0.002f) return@Canvas
        drawRect(
            brush = Brush.verticalGradient(colors = stops, startY = 0f, endY = size.height),
            alpha = 0.9f * intensity,
        )
        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.42f)
        val ringCount = 4
        for (i in 0 until ringCount) {
            val p = (pulse + i.toFloat() / ringCount) % 1f
            drawCircle(
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.14f * (1f - p) * intensity),
                radius = size.height * 0.06f + p * size.height * 0.42f,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OldHero(
    state: BentoConnectState,
    animate: Boolean,
    isControl: Boolean,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val live = state == BentoConnectState.Connected
    val searching = state == BentoConnectState.Connecting
    val session = rememberLiveSession(live)
    val brush: Brush = when {
        searching -> SessionGradients.brushFor(SessionGradients.connectingStops)
        live -> SessionGradients.brushFor(SessionGradients.connectedStops(session.sessionKey))
        else -> SessionGradients.idle
    }
    // The hero is a morphing blob (Cookie9Sided -> SoftBurst as the
    // tunnel comes up) that breathes while connected — the reference
    // Old hero's actual structure, replacing the flat circle + fake
    // rotating disc shipped in B2 (rescue B).
    val morphProgress by androidx.compose.animation.core.animateFloatAsState(
        if (live) 1f else 0f,
        androidx.compose.animation.core.spring(dampingRatio = 0.75f, stiffness = 280f),
        label = "old-hero-morph",
    )
    val morph = androidx.compose.runtime.remember {
        androidx.graphics.shapes.Morph(
            androidx.compose.material3.MaterialShapes.Cookie9Sided,
            androidx.compose.material3.MaterialShapes.SoftBurst,
        )
    }
    val breathe = if (animate && live) {
        val transition = rememberInfiniteTransition(label = "old-hero-breathe")
        val value by transition.animateFloat(
            0.97f, 1.04f,
            infiniteRepeatable(
                androidx.compose.animation.core.tween(2800),
                androidx.compose.animation.core.RepeatMode.Reverse,
            ),
            label = "old-hero-breathe-v",
        )
        value
    } else {
        1f
    }
    val click = if (isControl) {
        Modifier.combinedClickable(
            interactionSource = androidx.compose.runtime.remember {
                androidx.compose.foundation.interaction.MutableInteractionSource()
            },
            indication = null,
            role = Role.Button,
            onClick = onToggle,
            onLongClick = onLongPress,
        )
    } else {
        Modifier
    }
    val work = androidx.compose.runtime.remember { androidx.compose.ui.graphics.Path() }
    Box(
        Modifier
            .size(184.dp)
            .graphicsLayer {
                scaleX = breathe
                scaleY = breathe
            }
            .drawBehind {
                work.rewind()
                val path = morph.toPath(morphProgress.coerceIn(0f, 1f), work)
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
                drawPath(path, brush)
            }
            .then(click),
        contentAlignment = Alignment.Center,
    ) {
        when {
            searching -> androidx.compose.material3.ContainedLoadingIndicator(
                modifier = Modifier.size(72.dp),
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                indicatorColor = androidx.compose.ui.graphics.Color.White,
            )
            live -> Text(
                formatElapsed(session.elapsedSeconds),
                style = MaterialTheme.typography.headlineLarge,
                fontSize = 36.sp,
                color = androidx.compose.ui.graphics.Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
            )
            else -> Text(
                "F",
                style = MaterialTheme.typography.displayLarge,
                fontSize = 58.sp,
                color = androidx.compose.ui.graphics.Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
