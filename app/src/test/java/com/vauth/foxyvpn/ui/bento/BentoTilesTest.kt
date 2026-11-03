package com.vauth.foxyvpn.ui.bento

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase B1 (Zed overhaul): the settings board's tile layout model —
 * total decode, encode round-trips, first-fit packing and the
 * drag drop-index. Pure JVM: the board must never lose or invent a
 * tile, whatever string an older build left in the pref.
 */
class BentoTilesTest {

    private val available = BentoTiles.DEFAULT.map { it.key }

    @Test
    fun blankDecodesToTheDefaultBoard() {
        val layout = BentoTiles.decode("", available)
        assertEquals(BentoTiles.DEFAULT, layout.visible)
        assertTrue(layout.hidden.isEmpty())
    }

    @Test
    fun defaultBoardHasFifteenTilesWithAccountsAtTheVaultSlot() {
        assertEquals(15, BentoTiles.DEFAULT.size)
        // Zed's order with the drops removed: appearance first,
        // accounts where the vault tile sat (index 7), anti-block last.
        assertEquals(BentoTiles.APPEARANCE, BentoTiles.DEFAULT[0].key)
        assertEquals(BentoTiles.ACCOUNTS, BentoTiles.DEFAULT[7].key)
        assertEquals(BentoTiles.ANTI_BLOCK, BentoTiles.DEFAULT[14].key)
        assertEquals(BentoTileSize.Wide, BentoTiles.defaultSize(BentoTiles.ACCOUNTS))
        assertEquals(BentoTileSize.Tall, BentoTiles.defaultSize(BentoTiles.VPN))
    }

    @Test
    fun encodeDecodeRoundTrips() {
        val layout = BentoTiles.decode("", available)
            .without(BentoTiles.DNS)
            .resized(BentoTiles.VPN)
            .moved(0, 3)
        val decoded = BentoTiles.decode(BentoTiles.encode(layout), available)
        assertEquals(layout, decoded)
    }

    @Test
    fun unknownAndDuplicateKeysDropAndMissingAppendInDefaultOrder() {
        val raw = "vpn:x,tor:w,vpn:t,mystery:s|dns"
        val layout = BentoTiles.decode(raw, available)
        val keys = layout.visible.map { it.key }
        assertEquals(BentoTiles.VPN, keys.first())
        assertTrue("unknown key dropped", "tor" !in keys && "mystery" !in keys)
        assertEquals("duplicate dropped", 1, keys.count { it == BentoTiles.VPN })
        assertEquals("bad size falls back to default", BentoTileSize.Tall, layout.visible.first().size)
        assertEquals(listOf(BentoTiles.DNS), layout.hidden)
        // Every other available key appended, in DEFAULT order.
        val appended = keys.drop(1)
        val expectedTail = BentoTiles.DEFAULT.map { it.key }
            .filter { it != BentoTiles.VPN && it != BentoTiles.DNS }
        assertEquals(expectedTail, appended)
    }

    @Test
    fun packPlacesWideAndTallWithoutOverlap() {
        val placed = BentoTiles.pack(BentoTiles.DEFAULT, columns = 2)
        assertEquals(BentoTiles.DEFAULT.size, placed.size)
        val cells = HashSet<Pair<Int, Int>>()
        for (cell in placed) {
            for (dx in 0 until cell.slot.size.spanW) {
                for (dy in 0 until cell.slot.size.spanH) {
                    assertTrue("overlap at ${cell.x + dx},${cell.y + dy}", cells.add(cell.x + dx to cell.y + dy))
                }
            }
        }
        // First tile is Wide at the origin; the Tall vpn tile starts row 1.
        assertEquals(0, placed[0].x)
        assertEquals(0, placed[0].y)
        val vpn = placed.first { it.slot.key == BentoTiles.VPN }
        assertEquals(1, vpn.y)
    }

    @Test
    fun dropIndexKeepsCurrentInsideTheDeadband() {
        val layout = BentoTiles.decode("", available)
        val placed = BentoTiles.pack(layout.visible, 2)
        val vpn = placed.first { it.slot.key == BentoTiles.VPN }
        // gridX/gridY are the dragged tile's top-left in cell units
        // (what the hub's drag handler passes); dropping exactly at
        // the tile's own top-left keeps its index.
        val index = BentoTiles.dropIndex(layout, BentoTiles.VPN, 2, vpn.x.toFloat(), vpn.y.toFloat())
        assertEquals(layout.visible.indexOfFirst { it.key == BentoTiles.VPN }, index)
    }

    @Test
    fun movedReinsertsAtTheTargetIndex() {
        val layout = BentoTiles.decode("", available)
        val moved = layout.moved(0, 2)
        assertEquals(BentoTiles.DEFAULT[1].key, moved.visible[0].key)
        assertEquals(BentoTiles.DEFAULT[2].key, moved.visible[1].key)
        assertEquals(BentoTiles.DEFAULT[0].key, moved.visible[2].key)
        assertEquals(layout.visible.size, moved.visible.size)
        // Out-of-range moves are no-ops.
        assertEquals(layout, layout.moved(0, 99))
        assertEquals(layout, layout.moved(-1, 2))
    }

    @Test
    fun navKeysDecodeIsTotalAndPinsHomeAndSettings() {
        assertEquals(NavDestinationKeys.DEFAULT, decodeNavKeys(""))
        assertEquals(NavDestinationKeys.DEFAULT, decodeNavKeys("garbage,,"))
        assertEquals(
            listOf("home", "accounts", "settings"),
            decodeNavKeys("accounts"),
        )
        assertEquals(
            listOf("settings", "home"),
            decodeNavKeys("settings,home,settings"),
        )
        assertEquals("home,servers", encodeNavKeys(listOf("home", "servers")))
        assertEquals(
            NavDestinationKeys.DEFAULT,
            decodeNavKeys(encodeNavKeys(NavDestinationKeys.DEFAULT)),
        )
    }
}
