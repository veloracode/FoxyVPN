package com.vauth.foxyvpn.ui.map

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Map (B2): the bundled world.bin format round-trip + rejection. */
class WorldMapDataTest {

    private fun wld1(scale: Int, rings: List<List<Pair<Int, Int>>>): ByteArray {
        val size = 4 + 2 + 4 + rings.sumOf { 4 + it.size * 4 }
        val buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(byteArrayOf('W'.code.toByte(), 'L'.code.toByte(), 'D'.code.toByte(), '1'.code.toByte()))
        buffer.putShort(scale.toShort())
        buffer.putInt(rings.size)
        for (ring in rings) {
            buffer.putInt(ring.size)
            for ((lon, lat) in ring) {
                buffer.putShort(lon.toShort())
                buffer.putShort(lat.toShort())
            }
        }
        return buffer.array()
    }

    @Test
    fun parse_roundTrip() {
        val bytes = wld1(
            scale = 100,
            rings = listOf(
                listOf(1000 to 2000, -500 to 250, 0 to -4599),
                listOf(13965 to 3567),
            ),
        )
        val rings = WorldMapData.parse(bytes)
        assertEquals(2, rings.size)
        assertEquals(6, rings[0].size)
        assertEquals(10f, rings[0][0], 1e-4f)
        assertEquals(20f, rings[0][1], 1e-4f)
        assertEquals(-5f, rings[0][2], 1e-4f)
        assertEquals(2.5f, rings[0][3], 1e-4f)
        assertEquals(139.65f, rings[1][0], 1e-4f)
        assertEquals(35.67f, rings[1][1], 1e-4f)
    }

    @Test
    fun parse_rejectsBadInput() {
        assertTrue(WorldMapData.parse(ByteArray(0)).isEmpty())
        assertTrue(WorldMapData.parse("XXXX........".toByteArray()).isEmpty())
        // Truncated ring data.
        val good = wld1(100, listOf(listOf(1 to 2, 3 to 4)))
        assertTrue(WorldMapData.parse(good.copyOf(good.size - 2)).isEmpty())
    }
}
