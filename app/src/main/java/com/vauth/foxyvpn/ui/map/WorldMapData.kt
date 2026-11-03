package com.vauth.foxyvpn.ui.map

import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The Map screen's bundled land geometry (Phase B2).
 *
 * Asset `world.bin` in OUR OWN format (see tools/build_world_map.py):
 * magic "WLD1", u16 scale (degrees × 100), u32 ring count; per ring a
 * u32 point count followed by (i16 lon×100, i16 lat×100) pairs, all
 * little-endian. The source data is Natural Earth (public domain),
 * simplified at 0.08° — coastlines only; country identity is
 * deliberately not stored, because FoxyVPN's pins come from the
 * server inventory's declared locations, never from the geometry.
 *
 * Parsed once per process into degree-space rings (lon/lat pairs);
 * the canvas draws them under a transform, so zooming never rebuilds
 * the path.
 */
object WorldMapData {
    @Volatile private var cached: List<FloatArray>? = null

    fun load(context: Context): List<FloatArray> {
        cached?.let { return it }
        val parsed = runCatching {
            val bytes = context.applicationContext.assets.open("world.bin").use { it.readBytes() }
            parse(bytes)
        }.getOrDefault(emptyList())
        cached = parsed
        return parsed
    }

    /** Pure parse (unit-testable): degree-space rings, or empty on bad input. */
    fun parse(bytes: ByteArray): List<FloatArray> {
        if (bytes.size < 10) return emptyList()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = ByteArray(4).also { buffer.get(it) }
        if (!magic.contentEquals(byteArrayOf('W'.code.toByte(), 'L'.code.toByte(), 'D'.code.toByte(), '1'.code.toByte()))) {
            return emptyList()
        }
        val scale = buffer.short.toInt() and 0xFFFF
        if (scale <= 0) return emptyList()
        val ringCount = buffer.int
        if (ringCount < 0 || ringCount > 100_000) return emptyList()
        val rings = ArrayList<FloatArray>(ringCount)
        for (r in 0 until ringCount) {
            if (buffer.remaining() < 4) return emptyList()
            val count = buffer.int
            if (count < 0 || buffer.remaining() < count * 4) return emptyList()
            val ring = FloatArray(count * 2)
            for (i in 0 until count) {
                ring[i * 2] = buffer.short / scale.toFloat()
                ring[i * 2 + 1] = buffer.short / scale.toFloat()
            }
            rings += ring
        }
        return rings
    }
}
