package com.vauth.foxyvpn.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HCT math gates. The expected values were produced by the reference
 * Material Color Utilities implementation (materialyoucolor) — the same
 * numbers MaterialKolor/AndroidX produce — so the in-repo implementation
 * is pinned to the platform's color behavior, which the Bento tonal
 * palettes depend on.
 */
class HctMathTest {

    private fun assertHct(argb: Int, hue: Double, chroma: Double, tone: Double) {
        val hct = HctMath.hctOf(argb)
        assertEquals("hue of ${Integer.toHexString(argb)}", hue, hct[0], 0.05)
        assertEquals("chroma of ${Integer.toHexString(argb)}", chroma, hct[1], 0.15)
        assertEquals("tone of ${Integer.toHexString(argb)}", tone, hct[2], 0.05)
    }

    @Test
    fun knownColors_matchReferenceHct() {
        assertHct(0xFFFF0000.toInt(), 27.408, 113.358, 53.233)
        assertHct(0xFF00FF00.toInt(), 142.140, 108.410, 87.737)
        assertHct(0xFF0000FF.toInt(), 282.788, 87.231, 32.303)
        assertHct(0xFF5646D6.toInt(), 289.722, 68.798, 39.938) // Iris seed
        assertHct(0xFFC7F24E.toInt(), 124.595, 68.101, 89.950) // Bento lime
    }

    @Test
    fun tonalPalette_matchesReferenceTones() {
        val palette = TonalPalette.fromHueAndChroma(258.0, 56.0)
        assertEquals(0xFFD4E3FF.toInt(), palette.tone(90))
        assertEquals(0xFF00315D.toInt(), palette.tone(20))
    }

    @Test
    fun argbFromHct_isToneExact_andRoundTrips() {
        // Tone exactness is the property the whole tonal system rests on.
        for (tone in listOf(4, 10, 22, 40, 88, 97)) {
            val argb = HctMath.argbFromHct(258.0, 56.0, tone.toDouble())
            assertEquals("tone $tone", tone.toDouble(), HctMath.hctOf(argb)[2], 0.6)
        }
        for (argb in listOf(0xFFFF0000.toInt(), 0xFF5646D6.toInt(), 0xFFC7F24E.toInt())) {
            val hct = HctMath.hctOf(argb)
            val back = HctMath.argbFromHct(hct[0], hct[1], hct[2])
            val dr = Math.abs(((back shr 16) and 0xFF) - ((argb shr 16) and 0xFF))
            val dg = Math.abs(((back shr 8) and 0xFF) - ((argb shr 8) and 0xFF))
            val db = Math.abs((back and 0xFF) - (argb and 0xFF))
            assertTrue("round trip of ${Integer.toHexString(argb)}", maxOf(dr, dg, db) <= 1)
        }
    }

    @Test
    fun extremes_blackAndWhite() {
        assertEquals(0xFF000000.toInt(), HctMath.argbFromHct(200.0, 40.0, 0.0))
        assertEquals(0xFFFFFFFF.toInt(), HctMath.argbFromHct(200.0, 40.0, 100.0))
        val gray = HctMath.argbFromHct(200.0, 0.0, 50.0)
        val r = (gray shr 16) and 0xFF
        val g = (gray shr 8) and 0xFF
        val b = gray and 0xFF
        assertEquals(r, g)
        assertEquals(g, b)
    }
}
