package com.vauth.foxyvpn.ui.bento

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vauth.foxyvpn.ui.l10n.s
import com.vauth.foxyvpn.ui.theme.Bento
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The settings hub — Phase B1 of the Zed overhaul.
 *
 * A numbered bento board of tiles (pack layout from [BentoTiles]),
 * with search, an edit mode (hide / resize / drag-to-reorder / add /
 * reset), and a hidden-tiles section. The board layout persists
 * through [BentoPrefs.tiles]; the hub itself is state-driven by the
 * caller: entries carry live summaries, the layout string round-trips
 * through [onLayoutChange].
 */
data class BentoHubEntry(
    val key: String,
    val title: String,
    val summary: String,
    val art: SettingsArt,
    val keywords: String,
    val onOpen: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BentoHub(
    entries: List<BentoHubEntry>,
    layoutEncoded: String,
    onLayoutChange: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Bento.colors
    val prefs = Bento.prefs
    val byKey = remember(entries) { entries.associateBy { it.key } }
    val layout = remember(layoutEncoded, entries) {
        BentoTiles.decode(layoutEncoded, entries.map { it.key })
    }
    var editing by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var addSheetOpen by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()

    fun commit(next: BentoTileLayout) {
        onLayoutChange(BentoTiles.encode(next))
    }

    Column(
        modifier
            .fillMaxSize()
            .background(c.ground)
            .verticalScroll(scroll),
    ) {
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = BentoMetrics.gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BentoBackButton(onBack)
        }
        BentoTopBar(
            title = s("Settings"),
        ) {
            if (editing) {
                DonePill { editing = false }
            } else {
                MotifButton(
                    shape = MaterialShapes.Clover4Leaf,
                    container = c.stage,
                    content = c.onStage,
                    icon = Icons.Filled.Edit,
                    label = s("Edit tiles"),
                    onClick = { editing = true },
                    size = 52.dp,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Column(
            Modifier.fillMaxWidth().padding(horizontal = BentoMetrics.gutter),
            verticalArrangement = Arrangement.spacedBy(BentoMetrics.gap()),
        ) {
            if (!editing) {
                BentoSearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = s("Search settings"),
                )
            }
            if (!editing && query.isNotBlank()) {
                val matches = entries.filter { entry ->
                    entry.title.contains(query, ignoreCase = true) ||
                        entry.summary.contains(query, ignoreCase = true) ||
                        entry.keywords.contains(query, ignoreCase = true)
                }
                if (matches.isEmpty()) {
                    Text(
                        s("No results"),
                        style = BentoType.body(),
                        color = c.muted,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        matches.forEachIndexed { i, entry ->
                            EntryRow(entry, i, matches.size) { entry.onOpen() }
                        }
                    }
                }
            } else {
                Board(
                    layout = layout,
                    byKey = byKey,
                    editing = editing,
                    onOpen = { key -> byKey[key]?.onOpen?.invoke() },
                    onCommit = ::commit,
                )
                if (editing) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlineAction(s("Add tile"), Icons.Filled.Add, Modifier.weight(1f)) {
                            addSheetOpen = true
                        }
                        OutlineAction(s("Reset"), null, Modifier.weight(1f)) {
                            commit(BentoTileLayout(BentoTiles.DEFAULT.filter { it.key in byKey }, emptyList()))
                        }
                    }
                } else if (layout.hidden.isNotEmpty()) {
                    BentoCaption(s("Hidden"))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        layout.hidden.forEachIndexed { i, key ->
                            val entry = byKey[key] ?: return@forEachIndexed
                            EntryRow(entry, i, layout.hidden.size) { entry.onOpen() }
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    if (addSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { addSheetOpen = false },
            containerColor = c.ground,
        ) {
            Column(
                Modifier.padding(horizontal = BentoMetrics.gutter, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val hiddenEntries = layout.hidden.mapNotNull { byKey[it] }
                if (hiddenEntries.isEmpty()) {
                    Text(s("No hidden tiles"), style = BentoType.body(), color = c.muted)
                }
                hiddenEntries.forEachIndexed { i, entry ->
                    EntryRow(entry, i, hiddenEntries.size, trailingAdd = true) {
                        commit(layout.with(entry.key, BentoTiles.defaultSize(entry.key)))
                        addSheetOpen = false
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DonePill(onClick: () -> Unit) {
    val c = Bento.colors
    Row(
        Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(c.lime)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Filled.Check, contentDescription = null, tint = c.onLime, modifier = Modifier.size(18.dp))
        Text(
            s("Done"),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = c.onLime,
        )
    }
}

@Composable
private fun OutlineAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = Bento.colors
    val shape = RoundedCornerShape(25.dp)
    Row(
        modifier
            .height(50.dp)
            .clip(shape)
            .border(1.dp, c.line, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = c.ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = c.ink,
        )
    }
}

@Composable
private fun EntryRow(
    entry: BentoHubEntry,
    index: Int,
    count: Int,
    trailingAdd: Boolean = false,
    onClick: () -> Unit,
) {
    val c = Bento.colors
    val tone = c.tone(BentoTiles.toneOf(entry.key))
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
        BentoBadge(
            shape = entry.art.shape,
            container = tone.container,
            content = tone.content,
            icon = entry.art.icon,
            size = 40.dp,
            rotation = 0f,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(entry.title, style = BentoType.rowTitle(), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                entry.summary,
                style = BentoType.body().copy(fontSize = 12.5.sp),
                color = c.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            if (trailingAdd) Icons.Filled.Add else Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = c.muted,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun Board(
    layout: BentoTileLayout,
    byKey: Map<String, BentoHubEntry>,
    editing: Boolean,
    onOpen: (String) -> Unit,
    onCommit: (BentoTileLayout) -> Unit,
) {
    val prefs = Bento.prefs
    val gap = BentoMetrics.gap()
    val rowHeight = BentoMetrics.tileRow()
    var draggedKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var previewLayout by remember(layout) { mutableStateOf(layout) }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 600.dp) 4 else 2
        val density = LocalDensity.current
        val gapPx = with(density) { gap.toPx() }
        val rowPx = with(density) { rowHeight.toPx() }
        val cellW = (constraints.maxWidth - gapPx * (columns - 1)) / columns
        val shown = if (draggedKey != null) previewLayout else layout
        val placed = remember(shown, columns) { BentoTiles.pack(shown.visible, columns) }
        val rows = BentoTiles.rowsOf(placed)
        val boardHeight = with(density) { (rows * rowPx + (rows - 1).coerceAtLeast(0) * gapPx).toDp() }

        Box(Modifier.fillMaxWidth().height(boardHeight)) {
            placed.forEach { cell ->
                val entry = byKey[cell.slot.key] ?: return@forEach
                val isDragged = cell.slot.key == draggedKey
                val xPx = cell.x * (cellW + gapPx)
                val yPx = cell.y * (rowPx + gapPx)
                val wPx = cellW * cell.slot.size.spanW + gapPx * (cell.slot.size.spanW - 1)
                val hPx = rowPx * cell.slot.size.spanH + gapPx * (cell.slot.size.spanH - 1)
                HubTile(
                    entry = entry,
                    slot = cell.slot,
                    editing = editing,
                    isDragged = isDragged,
                    modifier = Modifier
                        .offset {
                            if (isDragged) {
                                IntOffset((xPx + dragOffset.x).roundToInt(), (yPx + dragOffset.y).roundToInt())
                            } else {
                                IntOffset(xPx.roundToInt(), yPx.roundToInt())
                            }
                        }
                        .size(with(density) { wPx.toDp() }, with(density) { hPx.toDp() })
                        .then(
                            if (editing) {
                                Modifier.pointerInput(cell.slot.key, layout, columns) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggedKey = cell.slot.key
                                            dragOffset = Offset.Zero
                                            previewLayout = layout
                                        },
                                        onDragEnd = {
                                            onCommit(previewLayout)
                                            draggedKey = null
                                            dragOffset = Offset.Zero
                                        },
                                        onDragCancel = {
                                            draggedKey = null
                                            dragOffset = Offset.Zero
                                            previewLayout = layout
                                        },
                                        onDrag = { change, delta ->
                                            change.consume()
                                            dragOffset += delta
                                            val gx = (xPx + dragOffset.x) / (cellW + gapPx)
                                            val gy = (yPx + dragOffset.y) / (rowPx + gapPx)
                                            val target = BentoTiles.dropIndex(
                                                layout, cell.slot.key, columns, gx, gy,
                                            )
                                            val current = layout.visible.indexOfFirst { it.key == cell.slot.key }
                                            if (target != current) {
                                                previewLayout = layout.moved(current, target)
                                            }
                                        },
                                    )
                                }
                            } else {
                                Modifier
                            },
                        ),
                    onOpen = { onOpen(cell.slot.key) },
                    onHide = { onCommit(layout.without(cell.slot.key)) },
                    onResize = { onCommit(layout.resized(cell.slot.key)) },
                )
            }
        }
    }
    // Keep prefs referenced: liveTiles/indexes are read inside HubTile.
    remember(prefs) { prefs }
}

@Composable
private fun HubTile(
    entry: BentoHubEntry,
    slot: BentoTileSlot,
    editing: Boolean,
    isDragged: Boolean,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onHide: () -> Unit,
    onResize: () -> Unit,
) {
    val c = Bento.colors
    val prefs = Bento.prefs
    val prominent = BentoTiles.isProminent(entry.key)
    val tone = c.tone(BentoTiles.toneOf(entry.key))
    val accent = if (prominent) c.lime else tone.content
    val accentInk = if (prominent) c.onLime else tone.container
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val pressScale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed && !editing) (if (prefs.playfulTouch) 0.955f else 0.98f) else 1f,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "tile-press",
    )
    val badgeSpin by androidx.compose.animation.core.animateFloatAsState(
        if (pressed && prefs.playfulTouch) 22f else 0f,
        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
        label = "tile-badge-spin",
    )
    val wiggle = if (editing && prefs.playfulTouch) {
        val transition = rememberInfiniteTransition(label = "tile-wiggle")
        val w by transition.animateFloat(
            -0.6f, 0.6f,
            infiniteRepeatable(tween(150 + (slot.key.hashCode() and 4) * 18, easing = androidx.compose.animation.core.LinearEasing), RepeatMode.Reverse),
            label = "tile-wiggle-v",
        )
        w
    } else {
        0f
    }
    val compact = prefs.density == com.vauth.foxyvpn.ui.theme.BentoDensity.Compact
    Box(
        modifier
            .graphicsLayer {
                scaleX = if (isDragged) 1.05f else pressScale
                scaleY = if (isDragged) 1.05f else pressScale
                rotationZ = wiggle
            }
            .clip(if (editing) RoundedCornerShape(16.dp) else BentoShapes.tile(prominent))
            .background(tone.container)
            .then(
                if (editing) {
                    Modifier
                } else {
                    Modifier.clickable(
                        interactionSource = source,
                        indication = androidx.compose.material3.ripple(color = tone.content),
                        role = Role.Button,
                        onClick = onOpen,
                    )
                },
            )
            .semantics { contentDescription = entry.title },
    ) {
        Column(
            Modifier.fillMaxSize().padding(if (compact) 13.dp else 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                if (prefs.indexes && !editing) {
                    BentoIndex(BentoTiles.ordinalOf(entry.key), tone.content)
                }
                Spacer(Modifier.weight(1f))
                if (editing) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(c.danger)
                            .clickable(role = Role.Button, onClick = onHide),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = s("Hide tile"), tint = c.onDanger, modifier = Modifier.size(16.dp))
                    }
                } else {
                    BentoBadge(
                        shape = entry.art.shape,
                        container = accent,
                        content = accentInk,
                        icon = entry.art.icon,
                        size = when {
                            prominent -> 44.dp
                            compact -> 32.dp
                            else -> 38.dp
                        },
                        rotation = badgeSpin + 10f,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    entry.title,
                    style = BentoType.title(
                        when {
                            prominent -> if (compact) 22.sp else 26.sp
                            slot.size == BentoTileSize.Small -> if (compact) 17.sp else 19.sp
                            else -> if (compact) 19.sp else 21.sp
                        },
                    ),
                    color = tone.content,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (editing) {
                        s(
                            when (slot.size) {
                                BentoTileSize.Small -> "Small"
                                BentoTileSize.Wide -> "Wide"
                                BentoTileSize.Tall -> "Tall"
                            },
                        )
                    } else {
                        entry.summary
                    },
                    style = BentoType.body(),
                    color = tone.content.copy(alpha = 0.85f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (editing) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .size(32.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(c.ground)
                    .clickable(role = Role.Button, onClick = onResize),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.OpenInFull, contentDescription = s("Resize tile"), tint = c.ink, modifier = Modifier.size(16.dp))
            }
        }
    }
}
