package com.vauth.foxyvpn.ui.easteregg

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vauth.foxyvpn.ui.theme.LocalMotionBudget
import com.vauth.foxyvpn.ui.theme.MotionBudget
import java.time.LocalDate
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * The space-game easter egg screen (Phase B2, DESIGN-SYSTEM §11) —
 * clean-room: renders [EggUniverse] on a canvas, flown with a virtual
 * stick (dead zone 48 dp, full throttle at 104 dp). The frame loop
 * lives in a LaunchedEffect tied to this composition, so leaving the
 * screen KILLS the loop (not pauses it); there is no network anything;
 * the whole feature is one route that can be deleted without touching
 * anything else. Layout direction is forced LTR, as in the reference.
 */
object EggColors {
    val Space = Color(0xFF16161D)
    val Grid = Color(0xFF292936)
    val PlanetRim = Color(0xFFA7A7CA)
    val Console = Color(0xFFB7B7FF)
    val Track = Color(0xFF34A853)
    val Autopilot = Color(0xFF4285F4)
    val Flag = Color(0xFFC6FF00)
    val Thrust = Color(0xFFFF8800)
    val Orbit = Color(0x8000FFFF)
}

@Composable
fun SpaceEggScreen(onExit: () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        SpaceEggContent(onExit)
    }
}

@Composable
private fun SpaceEggContent(onExit: () -> Unit) {
    val budget = LocalMotionBudget.current
    var rerolls by remember { mutableIntStateOf(0) }
    val universe = remember(rerolls) {
        EggUniverse(EggUniverse.dailySeed(LocalDate.now().toEpochDay()) + rerolls * 7919L)
    }
    var frame by remember { mutableIntStateOf(0) }
    var stickX by remember { mutableStateOf(0f) }
    var stickY by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val deadPx = with(density) { 48.dp.toPx() }
    val fullPx = with(density) { 104.dp.toPx() }

    // Frame loop: runs only while this screen is composed (leaving
    // cancels the effect — the loop is killed, not paused). When the
    // motion budget is Paused the scene renders statically.
    LaunchedEffect(universe, budget) {
        if (budget == MotionBudget.Paused) return@LaunchedEffect
        var last = -1L
        while (true) {
            withFrameNanos { nanos ->
                val dt = if (last < 0) 0.0 else ((nanos - last) / 1_000_000_000.0).coerceIn(0.0, 1.0 / 30.0)
                last = nanos
                // Stick -> thrust (dead zone / full throttle per spec).
                val mag = hypot(stickX, stickY)
                if (mag > deadPx) {
                    val strength = ((mag - deadPx) / (fullPx - deadPx)).coerceIn(0f, 1f)
                    universe.thrustX = (stickX / mag * strength).toDouble()
                    universe.thrustY = (stickY / mag * strength).toDouble()
                } else {
                    universe.thrustX = 0.0
                    universe.thrustY = 0.0
                }
                universe.step(dt)
                frame++
            }
        }
    }

    // Starfield: fixed per universe (seeded separately from the sim).
    val stars = remember(universe.seed) {
        val random = Random(universe.seed * 31 + 7)
        List(140) {
            Triple(random.nextFloat(), random.nextFloat(), random.nextFloat())
        }
    }

    Box(Modifier.fillMaxSize().background(EggColors.Space)) {
        Canvas(Modifier.fillMaxSize()) {
            // Read the frame counter so every sim step redraws.
            @Suppress("UNUSED_EXPRESSION") frame
            val scale = size.minDimension / 90_000f // px per world unit
            fun px(x: Double) = size.width / 2f + ((x - universe.shipX) * scale).toFloat()
            fun py(y: Double) = size.height / 2f + ((y - universe.shipY) * scale).toFloat()

            for ((sx, sy, sb) in stars) {
                drawCircle(
                    Color.White.copy(alpha = 0.25f + 0.55f * sb),
                    radius = 1f + sb * 1.6f,
                    center = Offset(sx * size.width, sy * size.height),
                )
            }
            // Orbit rings.
            for (planet in universe.planets) {
                drawCircle(
                    EggColors.Orbit.copy(alpha = 0.18f),
                    radius = (planet.orbitRadius * scale).toFloat(),
                    center = Offset(px(0.0), py(0.0)),
                    style = Stroke(1f),
                )
            }
            // The star.
            val starPos = Offset(px(0.0), py(0.0))
            val starColor = Color(universe.starClass.argb)
            drawCircle(starColor.copy(alpha = 0.25f), radius = 26f, center = starPos)
            drawCircle(starColor, radius = 13f, center = starPos)
            // Planets.
            for (planet in universe.planets) {
                val pos = Offset(px(planet.x), py(planet.y))
                val r = (planet.radius * scale).toFloat().coerceIn(3f, 30f)
                val fill = Color.hsv(planet.hue.toFloat(), 0.45f, 0.75f)
                drawCircle(fill, radius = r, center = pos)
                drawCircle(EggColors.PlanetRim, radius = r, center = pos, style = Stroke(1.2f))
                if (planet.surveyProgress > 0.0 && !planet.surveyed) {
                    drawArc(
                        EggColors.Track, startAngle = -90f,
                        sweepAngle = (360.0 * planet.surveyProgress).toFloat(),
                        useCenter = false,
                        topLeft = pos - Offset(r + 5f, r + 5f),
                        size = androidx.compose.ui.geometry.Size((r + 5f) * 2, (r + 5f) * 2),
                        style = Stroke(3f),
                    )
                }
                if (planet.surveyed) {
                    drawCircle(EggColors.Flag, radius = 3.5f, center = pos + Offset(r + 4f, -r - 4f))
                }
            }
            // Ship: a triangle along its velocity (or thrust) heading,
            // with a thrust flame while the engine burns.
            val heading = when {
                universe.thrustX != 0.0 || universe.thrustY != 0.0 ->
                    atan2(universe.thrustY, universe.thrustX)
                universe.shipSpeed > 1.0 -> atan2(universe.shipVy, universe.shipVx)
                else -> -Math.PI / 2
            }
            val center = Offset(size.width / 2f, size.height / 2f)
            val nose = center + Offset(cos(heading).toFloat(), sin(heading).toFloat()) * 14f
            val left = center + Offset(
                cos(heading + 2.5).toFloat(), sin(heading + 2.5).toFloat(),
            ) * 10f
            val right = center + Offset(
                cos(heading - 2.5).toFloat(), sin(heading - 2.5).toFloat(),
            ) * 10f
            if (universe.thrustX != 0.0 || universe.thrustY != 0.0 || universe.autopilot) {
                val tail = center - Offset(cos(heading).toFloat(), sin(heading).toFloat()) * 20f
                drawLine(EggColors.Thrust, center, tail, strokeWidth = 5f)
            }
            val shipPath = Path().apply {
                moveTo(nose.x, nose.y)
                lineTo(left.x, left.y)
                lineTo(right.x, right.y)
                close()
            }
            drawPath(shipPath, Color.White)
        }

        // HUD telemetry (refreshed with the frame counter).
        Column(Modifier.padding(16.dp)) {
            @Suppress("UNUSED_EXPRESSION") frame
            val nearest = universe.nearestPlanet()
            HudLine("SYS ${universe.starName} · CLASS ${universe.starClass.label}")
            HudLine("VEL ${"%.0f".format(universe.shipSpeed)} U/S")
            if (nearest != null) {
                val d = hypot(nearest.x - universe.shipX, nearest.y - universe.shipY)
                HudLine("NEAREST ${nearest.name.uppercase()} · ${"%.0f".format(d)} U")
                if (nearest.surveyed) {
                    HudLine("${nearest.name.uppercase()}: ${nearest.description}")
                    HudLine("ATM ${nearest.atmosphere} · FLORA ${nearest.flora} · FAUNA ${nearest.fauna}")
                }
            }
            HudLine("SURVEYED ${universe.planets.count { it.surveyed }}/${universe.planets.size}")
        }

        if (universe.discoveryBannerVisible()) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(EggColors.Flag)
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            ) {
                Text(
                    "◈ ${universe.lastDiscovery?.uppercase()} SURVEYED",
                    color = Color(0xFF16161D),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        // Controls: back, autopilot, new system.
        Row(
            Modifier.align(Alignment.TopEnd).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EggButton(onClick = {
                universe.autopilot = !universe.autopilot
            }) {
                Text(
                    "AP",
                    color = if (universe.autopilot) EggColors.Autopilot else EggColors.Console,
                    fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                )
            }
            EggButton(onClick = { rerolls++ }) {
                Text("NEW", color = EggColors.Console, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            }
            EggButton(onClick = onExit) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back",
                    tint = EggColors.Console, modifier = Modifier.size(18.dp),
                )
            }
        }

        // Virtual flight stick (bottom-start): drag to thrust.
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(28.dp)
                .size(120.dp)
                .clip(CircleShape)
                .border(1.dp, EggColors.Grid, CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = { stickX = 0f; stickY = 0f },
                        onDragCancel = { stickX = 0f; stickY = 0f },
                    ) { change, drag ->
                        stickX = (stickX + drag.x).coerceIn(-fullPx, fullPx)
                        stickY = (stickY + drag.y).coerceIn(-fullPx, fullPx)
                        change.consume()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val knobMag = hypot(stickX, stickY)
            val knobScale = if (knobMag > fullPx) fullPx / knobMag else 1f
            Box(
                Modifier
                    .offset {
                        androidx.compose.ui.unit.IntOffset(
                            (stickX * knobScale).toInt(),
                            (stickY * knobScale).toInt(),
                        )
                    }
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(EggColors.Console.copy(alpha = 0.85f)),
            )
        }
        Text(
            "DRAG TO FLY · STAY CLOSE AND SLOW NEAR A PLANET TO SURVEY IT",
            color = EggColors.Console.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
        )
    }
}

@Composable
private fun HudLine(text: String) {
    Text(
        text,
        color = EggColors.Console,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
    )
}

@Composable
private fun EggButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(EggColors.Grid)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
