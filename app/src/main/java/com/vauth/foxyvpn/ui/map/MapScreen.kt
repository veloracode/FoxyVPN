package com.vauth.foxyvpn.ui.map

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vauth.foxyvpn.AppContainer
import com.vauth.foxyvpn.data.model.ConnectionState
import com.vauth.foxyvpn.data.model.displayName
import com.vauth.foxyvpn.ui.ServerListViewModel
import com.vauth.foxyvpn.ui.bento.SettingsPageScaffold
import com.vauth.foxyvpn.ui.l10n.s
import com.vauth.foxyvpn.ui.theme.Bento
import com.vauth.foxyvpn.ui.theme.LocalAppDesign
import com.vauth.foxyvpn.ui.theme.LocalMotionBudget
import com.vauth.foxyvpn.ui.theme.MotionBudget
import com.vauth.foxyvpn.vpn.FoxyVpnService
import com.vauth.foxyvpn.vpn.PingUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Map screen (Phase B2, DESIGN-SYSTEM §9 as adapted by the
 * overhaul's adopted rule): bundled offline land geometry; pins ONLY
 * for inventory cities with declared coordinates; the highlighted pin
 * is the selected (or connected) server. NO GPS, NO IP-geolocation
 * lookups, NO third-party SDK — this screen's only network act is a
 * TCP ping to the selected server, the same probe the server list
 * already runs. Deliberate deviation from Zed's layout, forced by
 * that rule: there is no origin point and no route arc (Zed derives
 * the origin from a persisted IP lookup); the exit pin pulses while
 * connected instead.
 */
private data class MapPin(val lon: Float, val lat: Float, val focus: Boolean)

@Composable
fun MapScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val rings = remember { WorldMapData.load(context) }
    val serverListModel: ServerListViewModel = viewModel(
        factory = ServerListViewModel.factory(
            serverListClient = container.serverListClient,
            proxyStateStore = container.proxyStateStore,
        ),
    )
    val serverState by serverListModel.state.collectAsState()
    val selected by container.proxyStateStore.selectedProxyFlow.collectAsState()
    val vpnState by FoxyVpnService.state.collectAsState()
    val connected = vpnState == ConnectionState.CONNECTED
    val design = LocalAppDesign.current
    val budget = LocalMotionBudget.current
    val animate = budget == MotionBudget.Full

    val selectedNow = selected
    val focusPoint = selectedNow?.let { CityCoordinates.forCityCode(it.cityCode) }
    val pins = remember(serverState.countries, selectedNow) {
        val list = ArrayList<MapPin>()
        for (country in serverState.countries) {
            for (city in country.cities) {
                val point = CityCoordinates.forCityCode(city.code) ?: continue
                val isFocus = selectedNow != null &&
                    selectedNow.countryCode == country.code && selectedNow.cityCode == city.code
                list += MapPin(point.lon.toFloat(), point.lat.toFloat(), isFocus)
            }
        }
        list
    }

    var pingMs by remember { mutableStateOf<Int?>(null) }
    var probeNonce by remember { mutableIntStateOf(0) }
    LaunchedEffect(selected, probeNonce) {
        pingMs = null
        val target = selected
        if (target != null) {
            pingMs = withContext(Dispatchers.IO) {
                PingUtil.measureTcpLatencyMs(target.host, target.port)
            }
        }
    }

    val exitLabel = when {
        focusPoint != null && selectedNow != null ->
            "${focusPoint.cityName}, ${selectedNow.displayName}"
        selectedNow != null -> selectedNow.displayName
        else -> "—"
    }
    val statusLabel = when {
        connected -> s("Connected")
        selectedNow != null -> s("Selected")
        else -> "—"
    }

    if (design == com.vauth.foxyvpn.data.SettingsStore.AppDesign.BENTO) {
        SettingsPageScaffold(title = s("Map"), art = null, onBack = onBack) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .background(Bento.colors.stageTone.container),
                ) {
                    WorldMapCanvas(rings, pins, focusPoint, connected, animate, Modifier.fillMaxSize())
                    Text(
                        "ROUTE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Bento.colors.stageTone.content.copy(alpha = 0.6f),
                        modifier = Modifier.padding(14.dp),
                    )
                    if (pingMs != null) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .clip(CircleShape)
                                .background(Bento.colors.surface)
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(
                                "$pingMs ms",
                                style = MaterialTheme.typography.labelMedium,
                                color = Bento.colors.ink,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MapFactTile(s("Exit"), exitLabel, Modifier.weight(1f))
                    MapFactTile(s("Server"), selected?.authority ?: "—", Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MapFactTile(s("Ping"), pingMs?.let { "$it ms" } ?: "—", Modifier.weight(1f))
                    MapFactTile(s("Status"), statusLabel, Modifier.weight(1f))
                }
                if (!connected) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        s("Not connected — showing the selected server"),
                        style = MaterialTheme.typography.bodySmall,
                        color = Bento.colors.muted,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(27.dp))
                        .background(Bento.colors.limeTone.container)
                        .clickable(role = Role.Button) {
                            probeNonce++
                            serverListModel.loadServerList(force = true)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Refresh, contentDescription = null,
                            tint = Bento.colors.limeTone.content, modifier = Modifier.size(19.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            s("Refresh"),
                            style = MaterialTheme.typography.titleSmall,
                            color = Bento.colors.limeTone.content,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    } else {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            WorldMapCanvas(rings, pins, focusPoint, connected, animate, Modifier.fillMaxSize())
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                        .clickable(role = Role.Button, onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s("Back"),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.size(10.dp))
                Text(
                    s("Map"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                Text(exitLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    listOfNotNull(
                        selected?.authority,
                        pingMs?.let { "$it ms" },
                        statusLabel,
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!connected) {
                    Text(
                        s("Not connected — showing the selected server"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MapFactTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Bento.colors.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Bento.colors.muted)
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            color = Bento.colors.ink,
            maxLines = 1,
        )
    }
}

@Composable
private fun WorldMapCanvas(
    rings: List<FloatArray>,
    pins: List<MapPin>,
    focus: CityPoint?,
    connected: Boolean,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    var zoom by remember { mutableFloatStateOf(1f) }
    var centerLon by remember { mutableFloatStateOf(0f) }
    var centerLat by remember { mutableFloatStateOf(20f) }
    var frameNonce by remember { mutableIntStateOf(0) }

    val landPath = remember(rings) {
        Path().apply {
            for (ring in rings) {
                if (ring.size < 4) continue
                moveTo(ring[0], ring[1])
                var i = 2
                while (i + 1 < ring.size) {
                    lineTo(ring[i], ring[i + 1])
                    i += 2
                }
                close()
            }
        }
    }

    val pulse = if (animate && connected) {
        val transition = rememberInfiniteTransition(label = "map-pulse")
        val value by transition.animateFloat(
            0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)),
            label = "map-pulse-value",
        )
        value
    } else {
        -1f
    }

    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { frameNonce++ })
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoomChange, _ ->
                    // Keep the centroid's lon/lat fixed under the fingers.
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val fitK = minOf(w / 360f, h / 180f)
                    val kBefore = fitK * zoom
                    val lonAt = centerLon + (centroid.x - w / 2f) / kBefore
                    val latAt = centerLat - (centroid.y - h / 2f) / kBefore
                    zoom = (zoom * zoomChange).coerceIn(1f, 24f)
                    val kAfter = fitK * zoom
                    centerLon = lonAt - (centroid.x - w / 2f) / kAfter - pan.x / kAfter
                    centerLat = latAt + (centroid.y - h / 2f) / kAfter + pan.y / kAfter
                    centerLon = centerLon.coerceIn(-180f, 180f)
                    centerLat = centerLat.coerceIn(-85f, 85f)
                }
            },
    ) {
        // Auto-frame once size is known and whenever the target changes
        // or a double-tap asks for a reframe.
        val w = size.width
        val h = size.height
        val fitK = minOf(w / 360f, h / 180f)
        val k = fitK * zoom
        fun px(lon: Float) = w / 2f + (lon - centerLon) * k
        fun py(lat: Float) = h / 2f - (lat - centerLat) * k

        // Landmass + graticule in degree space under one transform.
        withTransform({
            translate(w / 2f, h / 2f)
            scale(k, -k, pivot = Offset.Zero)
            translate(-centerLon, -centerLat)
        }) {
            drawPath(landPath, ink.copy(alpha = 0.10f))
            drawPath(landPath, ink.copy(alpha = 0.26f), style = Stroke(1.1f / k))
            val grat = ink.copy(alpha = 0.06f)
            var lon = -180f
            while (lon <= 180f) {
                drawLine(grat, Offset(lon, -90f), Offset(lon, 90f), strokeWidth = 1f / k)
                lon += 30f
            }
            var lat = -60f
            while (lat <= 60f) {
                drawLine(grat, Offset(-180f, lat), Offset(180f, lat), strokeWidth = 1f / k)
                lat += 30f
            }
        }

        // Sea captions (§9.2 positions/spans), only when they'd be legible.
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = ink.copy(alpha = 0.45f).toArgb()
            textAlign = android.graphics.Paint.Align.CENTER
        }
        if (k * 6.5f > 90f) {
            paint.textSize = k * 1.6f
            drawContext.canvas.nativeCanvas.drawText("Persian Gulf", px(51.8f), py(26.8f), paint)
        }
        if (k * 4.0f > 90f) {
            paint.textSize = k * 1.6f
            drawContext.canvas.nativeCanvas.drawText("Caspian Sea", px(50.9f), py(41.6f), paint)
        }

        // Pins.
        for (pin in pins) {
            val x = px(pin.lon)
            val y = py(pin.lat)
            if (x < -20f || x > w + 20f || y < -20f || y > h + 20f) continue
            if (pin.focus) continue
            drawCircle(ink.copy(alpha = 0.45f), radius = 3.dp.toPx(), center = Offset(x, y))
        }
        val focusPin = pins.firstOrNull { it.focus }
        if (focusPin != null) {
            val x = px(focusPin.lon)
            val y = py(focusPin.lat)
            if (pulse >= 0f) {
                drawCircle(
                    Color(0xFFC7F24E).copy(alpha = (1f - pulse) * 0.55f),
                    radius = (6f + 26f * pulse).dp.toPx() / 2f + 6.dp.toPx() * pulse,
                    center = Offset(x, y),
                    style = Stroke(2.dp.toPx()),
                )
            }
            drawCircle(Color(0xFFC7F24E), radius = 6.dp.toPx(), center = Offset(x, y))
            drawCircle(Color.White, radius = 2.4.dp.toPx(), center = Offset(x, y))
        }
    }

    // Auto-framing (§9.2: single point => 2.6x whole world on the point;
    // no point => whole world). Runs when the focus appears/changes or
    // on a double-tap reframe.
    LaunchedEffect(focus, frameNonce) {
        if (focus != null) {
            zoom = 2.6f
            centerLon = focus.lon.toFloat()
            centerLat = focus.lat.toFloat()
        } else {
            zoom = 1f
            centerLon = 0f
            centerLat = 20f
        }
    }
}
