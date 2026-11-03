package com.vauth.foxyvpn.ui.bento

import com.vauth.foxyvpn.ui.theme.BentoToneKind

/**
 * The settings board's tile model — Phase B1 of the Zed overhaul.
 *
 * Tiles are keyed slots with a size (Small 1×1, Wide 2×1, Tall 1×2),
 * packed first-fit row-major into the board's columns. The layout
 * persists as one string: "key:size,...|hidden,...". Decoding is
 * total: unknown or duplicate keys drop, missing available keys append
 * in default order, an empty string means the default board.
 *
 * The FoxyVPN board (DESIGN-MAP §2): the reference board's order with
 * the inapplicable tiles dropped, Accounts at the vault slot, and the
 * fork's anti-block section as its own tile (DEV-8). The speed-test and
 * map tiles join in Phase B2 with their screens (§12 staging note).
 */
enum class BentoTileSize(val code: Char, val spanW: Int, val spanH: Int) {
    Small('s', 1, 1),
    Wide('w', 2, 1),
    Tall('t', 1, 2),
    ;

    fun next(): BentoTileSize = when (this) {
        Small -> Wide
        Wide -> Tall
        Tall -> Small
    }

    companion object {
        fun of(code: Char): BentoTileSize? = entries.firstOrNull { it.code == code }
    }
}

data class BentoTileSlot(val key: String, val size: BentoTileSize)

data class BentoTileLayout(
    val visible: List<BentoTileSlot>,
    val hidden: List<String>,
) {
    fun without(key: String): BentoTileLayout {
        if (visible.none { it.key == key }) return this
        return BentoTileLayout(
            visible.filterNot { it.key == key },
            (hidden + key).distinct(),
        )
    }

    fun with(key: String, defaultSize: BentoTileSize): BentoTileLayout {
        if (visible.any { it.key == key }) return this
        return BentoTileLayout(
            visible + BentoTileSlot(key, defaultSize),
            hidden.filterNot { it == key },
        )
    }

    fun resized(key: String): BentoTileLayout =
        BentoTileLayout(
            visible.map { if (it.key == key) it.copy(size = it.size.next()) else it },
            hidden,
        )

    fun moved(from: Int, to: Int): BentoTileLayout {
        if (from !in visible.indices || to !in visible.indices || from == to) return this
        val list = visible.toMutableList()
        list.add(to, list.removeAt(from))
        return BentoTileLayout(list, hidden)
    }
}

object BentoTiles {

    const val APPEARANCE = "appearance"
    const val VPN = "vpn"
    const val DNS = "dns"
    const val ROUTING = "routing"
    const val PER_APP = "per-app"
    const val CORE = "core"
    const val MODE = "mode"
    const val ACCOUNTS = "accounts"
    const val MUX = "mux"
    const val FRAGMENT = "fragment"
    const val ADVANCED = "advanced"
    const val SUPPORT = "support"
    const val UPDATE = "update"
    const val ABOUT = "about"
    const val ANTI_BLOCK = "anti-block"

    /** The default board, in order (DESIGN-MAP §2). */
    val DEFAULT: List<BentoTileSlot> = listOf(
        BentoTileSlot(APPEARANCE, BentoTileSize.Wide),
        BentoTileSlot(VPN, BentoTileSize.Tall),
        BentoTileSlot(DNS, BentoTileSize.Small),
        BentoTileSlot(ROUTING, BentoTileSize.Small),
        BentoTileSlot(PER_APP, BentoTileSize.Wide),
        BentoTileSlot(CORE, BentoTileSize.Small),
        BentoTileSlot(MODE, BentoTileSize.Small),
        BentoTileSlot(ACCOUNTS, BentoTileSize.Wide),
        BentoTileSlot(MUX, BentoTileSize.Small),
        BentoTileSlot(FRAGMENT, BentoTileSize.Small),
        BentoTileSlot(ADVANCED, BentoTileSize.Small),
        BentoTileSlot(SUPPORT, BentoTileSize.Small),
        BentoTileSlot(UPDATE, BentoTileSize.Small),
        BentoTileSlot(ABOUT, BentoTileSize.Wide),
        BentoTileSlot(ANTI_BLOCK, BentoTileSize.Wide),
    )

    private val TONES: Map<String, BentoToneKind> = mapOf(
        APPEARANCE to BentoToneKind.Stage,
        VPN to BentoToneKind.Lime,
        DNS to BentoToneKind.Cyan,
        ROUTING to BentoToneKind.Pink,
        PER_APP to BentoToneKind.Surface,
        CORE to BentoToneKind.Violet,
        MODE to BentoToneKind.Cyan,
        ACCOUNTS to BentoToneKind.Violet,
        MUX to BentoToneKind.Pink,
        FRAGMENT to BentoToneKind.Violet,
        ADVANCED to BentoToneKind.Surface,
        SUPPORT to BentoToneKind.Coral,
        UPDATE to BentoToneKind.Blue,
        ABOUT to BentoToneKind.Stage,
        ANTI_BLOCK to BentoToneKind.Coral,
    )

    private val PROMINENT: Set<String> = setOf(APPEARANCE)

    fun toneOf(key: String): BentoToneKind = TONES[key] ?: BentoToneKind.Surface

    fun isProminent(key: String): Boolean = key in PROMINENT

    fun defaultSize(key: String): BentoTileSize =
        DEFAULT.firstOrNull { it.key == key }?.size ?: BentoTileSize.Small

    /** 1-based position in the default board (the board's index numbers). */
    fun ordinalOf(key: String): Int =
        DEFAULT.indexOfFirst { it.key == key }.let { if (it >= 0) it + 1 else DEFAULT.size + 1 }

    fun decode(raw: String, available: Collection<String>): BentoTileLayout {
        val availableSet = available.toSet()
        if (raw.isBlank()) {
            return BentoTileLayout(DEFAULT.filter { it.key in availableSet }, emptyList())
        }
        val parts = raw.split("|", limit = 2)
        val seen = HashSet<String>()
        val visible = ArrayList<BentoTileSlot>()
        for (token in parts[0].split(",")) {
            val trimmed = token.trim()
            if (trimmed.isEmpty()) continue
            val key = trimmed.substringBefore(":")
            if (key !in availableSet || !seen.add(key)) continue
            val size = trimmed.substringAfter(":", "")
                .firstOrNull()?.let { BentoTileSize.of(it) } ?: defaultSize(key)
            visible += BentoTileSlot(key, size)
        }
        val hidden = ArrayList<String>()
        if (parts.size > 1) {
            for (token in parts[1].split(",")) {
                val key = token.trim()
                if (key.isEmpty() || key !in availableSet || key in seen) continue
                seen += key
                hidden += key
            }
        }
        // Available keys the string never mentions join in default order.
        val defaultOrder = DEFAULT.map { it.key } + availableSet.filter { it !in DEFAULT_KEYS }
        for (key in defaultOrder) {
            if (key in availableSet && key !in seen) {
                seen += key
                visible += BentoTileSlot(key, defaultSize(key))
            }
        }
        return BentoTileLayout(visible, hidden)
    }

    private val DEFAULT_KEYS: Set<String> = DEFAULT.map { it.key }.toSet()

    fun encode(layout: BentoTileLayout): String =
        layout.visible.joinToString(",") { "${it.key}:${it.size.code}" } +
            "|" + layout.hidden.joinToString(",")

    /** A placed tile: its slot plus grid position (cell units). */
    data class Placed(val slot: BentoTileSlot, val x: Int, val y: Int)

    /** First-fit row-major packing into [columns] columns. */
    fun pack(slots: List<BentoTileSlot>, columns: Int): List<Placed> {
        val occupied = HashSet<Pair<Int, Int>>()
        val placed = ArrayList<Placed>(slots.size)
        for (slot in slots) {
            val w = minOf(slot.size.spanW, columns)
            val h = slot.size.spanH
            var y = 0
            var done = false
            while (!done) {
                for (x in 0..columns - w) {
                    val cells = (0 until w).flatMap { dx -> (0 until h).map { dy -> (x + dx) to (y + dy) } }
                    if (cells.none { it in occupied }) {
                        occupied += cells
                        placed += Placed(slot, x, y)
                        done = true
                        break
                    }
                }
                if (!done) y++
            }
        }
        return placed
    }

    /** Total grid rows a packing occupies. */
    fun rowsOf(placed: List<Placed>): Int =
        placed.maxOfOrNull { it.y + it.slot.size.spanH } ?: 0

    /**
     * The insertion index for a dragged tile whose center is at grid
     * ([gridX], [gridY]) (cell units). [stickiness] is a deadband around
     * the tile's current cell: drops inside it keep the current index.
     */
    fun dropIndex(
        layout: BentoTileLayout,
        key: String,
        columns: Int,
        gridX: Float,
        gridY: Float,
        stickiness: Float = 0.2f,
    ): Int {
        val current = layout.visible.indexOfFirst { it.key == key }
        if (current < 0) return layout.visible.size
        val others = layout.visible.filterNot { it.key == key }
        val placed = pack(others, columns)
        val dragged = layout.visible[current]
        val ownPlaced = pack(layout.visible, columns).firstOrNull { it.slot.key == key }
        if (ownPlaced != null) {
            val cx = gridX + dragged.size.spanW / 2f
            val cy = gridY + dragged.size.spanH / 2f
            val ownCx = ownPlaced.x + dragged.size.spanW / 2f
            val ownCy = ownPlaced.y + dragged.size.spanH / 2f
            if (kotlin.math.abs(cx - ownCx) <= stickiness && kotlin.math.abs(cy - ownCy) <= stickiness) {
                return current
            }
        }
        var index = placed.size
        for ((i, cell) in placed.withIndex()) {
            val cx = cell.x + cell.slot.size.spanW / 2f
            val cy = cell.y + cell.slot.size.spanH / 2f
            val pointCx = gridX + dragged.size.spanW / 2f
            val pointCy = gridY + dragged.size.spanH / 2f
            if (pointCy < cy - 0.5f || (pointCy <= cy + 0.5f && pointCx < cx)) {
                // Convert "index among others" to the full-list index.
                index = i
                break
            }
        }
        return index.coerceIn(0, layout.visible.size - 1)
    }
}
