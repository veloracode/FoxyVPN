package com.vauth.foxyvpn.ui.theme

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * HCT color space (Hue–Chroma–Tone) and tonal palettes, implemented from
 * the published algorithm (CIE L*a*b* tone + CIECAM16 hue/chroma, D65,
 * the standard HCT viewing conditions) so the Bento tonal palettes can
 * be generated on-device without a color library.
 *
 * All math here is original code written against the public CIECAM16 /
 * CIE definitions. Colors are packed ARGB ints; the class is pure JVM
 * (no Android/Compose types) so unit tests can pin it against reference
 * vectors.
 *
 * Contract (the same contract the reference tonal generator documents):
 *  - [hctOf] is the exact forward transform.
 *  - [argbFromHct] returns a color whose tone equals the request
 *    (by construction, via the Y of CIE L*), whose hue equals the
 *    request, and whose chroma is the request reduced only as far as
 *    the sRGB gamut forces.
 *
 * Conventions used throughout (one consistent set):
 *  - XYZ is on the [0, 100] scale; cone responses are CAT16 of XYZ.
 *  - "disc" = rgbD * rgb / 100, the discounted cone response (~[0, 1]).
 *  - adaptedResponse maps disc -> the CIECAM16 response
 *    sign * 400*(FL*disc)^0.42 / ((FL*disc)^0.42 + 27.13) — no offset,
 *    no Nbb; the achromatic response ac = p2 * Nbb is formed explicitly
 *    where the algorithm asks for it, and the 0.305 constant appears
 *    only in the t denominator (u + 0.305) and the inverse gamma.
 */
object HctMath {

    // D65 white point, XYZ scaled to [0, 100].
    private const val WP_X = 95.047
    private const val WP_Y = 100.0
    private const val WP_Z = 108.883

    // HCT viewing conditions (average surround, no illuminant discounting).
    // Adapting luminance uses the reference default: (200/pi) * Y(L*50) / 100.
    private val adaptingLuminance: Double = 200.0 / PI * yFromLstar(50.0) / 100.0
    private const val BACKGROUND_LSTAR = 50.0
    private const val SURROUND_F = 1.0
    private const val SURROUND_C = 0.69
    private const val SURROUND_NC = 1.0

    private val nRatio: Double = yFromLstar(BACKGROUND_LSTAR) / WP_Y
    private val zBase: Double = 1.48 + sqrt(nRatio)
    private val nbb: Double = 0.725 * (1.0 / nRatio).pow(0.2)
    private val ncb: Double = nbb
    private val fl: Double = run {
        val k = 1.0 / (5.0 * adaptingLuminance + 1.0)
        val k4 = k * k * k * k
        k4 * adaptingLuminance +
            0.1 * (1.0 - k4) * (1.0 - k4) * (5.0 * adaptingLuminance).pow(1.0 / 3.0)
    }
    private val degreeOfAdaptation: Double =
        (SURROUND_F * (1.0 - (1.0 / 3.6) * exp((-adaptingLuminance - 42.0) / 92.0)))
            .coerceIn(0.0, 1.0)

    // CAT16 transform (XYZ -> cone responses) and its inverse.
    private val CAT16 = arrayOf(
        doubleArrayOf(0.401288, 0.650173, -0.051461),
        doubleArrayOf(-0.250268, 1.204414, 0.045854),
        doubleArrayOf(-0.002079, 0.048952, 0.953127),
    )
    private val CAT16_INV = invert3x3(CAT16)

    /** rgbD per cone channel: D * (100 / whiteCone) + (1 - D). */
    private val rgbD: DoubleArray = run {
        val w = matVec(CAT16, doubleArrayOf(WP_X, WP_Y, WP_Z))
        DoubleArray(3) { i -> degreeOfAdaptation * (100.0 / w[i]) + (1.0 - degreeOfAdaptation) }
    }

    /** Achromatic response of the white point, WITH the Nbb factor. */
    private val aw: Double = run {
        val w = matVec(CAT16, doubleArrayOf(WP_X, WP_Y, WP_Z))
        val af = DoubleArray(3) { i -> adaptedResponse(rgbD[i] * w[i] / 100.0) }
        ((40.0 * af[0] + 20.0 * af[1] + af[2]) / 20.0) * nbb
    }

    // ------------------------------------------------------------------
    // sRGB <-> XYZ <-> CIE L*
    // ------------------------------------------------------------------

    fun lstarFromArgb(argb: Int): Double = lstarFromY(xyzFromArgb(argb)[1])

    fun yFromLstar(lstar: Double): Double {
        val fy = (lstar + 16.0) / 116.0
        val y = if (lstar > 8.0) fy * fy * fy else lstar / 903.3
        return y * 100.0
    }

    fun lstarFromY(y100: Double): Double {
        val y = y100 / 100.0
        return if (y > 216.0 / 24389.0) 116.0 * y.pow(1.0 / 3.0) - 16.0 else 903.3 * y
    }

    /** XYZ in [0, 100] scale. */
    fun xyzFromArgb(argb: Int): DoubleArray {
        val r = linearChannel((argb shr 16) and 0xFF)
        val g = linearChannel((argb shr 8) and 0xFF)
        val b = linearChannel(argb and 0xFF)
        return doubleArrayOf(
            (0.41233895 * r + 0.35762064 * g + 0.18051042 * b) * 100.0,
            (0.2126 * r + 0.7152 * g + 0.0722 * b) * 100.0,
            (0.01932141 * r + 0.11916382 * g + 0.95034478 * b) * 100.0,
        )
    }

    fun argbFromXyz(xyz: DoubleArray): Int {
        val rgb = linearRgbFromXyz(xyz)
        return (0xFF shl 24) or
            (delinearChannel(rgb[0]) shl 16) or
            (delinearChannel(rgb[1]) shl 8) or
            delinearChannel(rgb[2])
    }

    /** Linear sRGB channels (unclamped) for an XYZ color. */
    fun linearRgbFromXyz(xyz: DoubleArray): DoubleArray {
        val x = xyz[0] / 100.0
        val y = xyz[1] / 100.0
        val z = xyz[2] / 100.0
        return doubleArrayOf(
            3.2404542 * x - 1.5371385 * y - 0.4985314 * z,
            -0.9692660 * x + 1.8760108 * y + 0.0415560 * z,
            0.0556434 * x - 0.2040259 * y + 1.0572252 * z,
        )
    }

    private fun linearChannel(v8: Int): Double {
        val v = v8 / 255.0
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun delinearChannel(linear: Double): Int {
        val v = if (linear <= 0.0031308) {
            linear * 12.92
        } else {
            1.055 * linear.pow(1.0 / 2.4) - 0.055
        }
        return floor(v * 255.0 + 0.5).toInt().coerceIn(0, 255)
    }

    // ------------------------------------------------------------------
    // CIECAM16 forward (XYZ -> hue, chroma, J)
    // ------------------------------------------------------------------

    private fun adaptedResponse(disc: Double): Double {
        val powered = (fl * abs(disc)).pow(0.42)
        return sign(disc) * 400.0 * powered / (powered + 27.13)
    }

    private fun inverseAdaptedResponse(response: Double): Double {
        val base = abs(response) * 27.13 / (400.0 - abs(response))
        return sign(response) * (1.0 / fl) * base.pow(1.0 / 0.42)
    }

    /** Returns (hue degrees [0, 360), chroma, J) for an XYZ color. */
    fun cam16FromXyz(xyz: DoubleArray): DoubleArray {
        val cone = matVec(CAT16, xyz)
        val rA = adaptedResponse(rgbD[0] * cone[0] / 100.0)
        val gA = adaptedResponse(rgbD[1] * cone[1] / 100.0)
        val bA = adaptedResponse(rgbD[2] * cone[2] / 100.0)
        val a = (11.0 * rA - 12.0 * gA + bA) / 11.0
        val b = (rA + gA - 2.0 * bA) / 9.0
        val hueRad = Math.atan2(b, a)
        var hue = hueRad * 180.0 / PI
        if (hue < 0) hue += 360.0
        if (a == 0.0 && b == 0.0) hue = 0.0
        val p2 = (40.0 * rA + 20.0 * gA + bA) / 20.0
        val achromatic = p2 * nbb
        val j = 100.0 * (achromatic / aw).pow(SURROUND_C * zBase)
        val eT = 0.25 * (cos(hueRad + 2.0) + 3.8)
        val u = (20.0 * rA + 20.0 * gA + 21.0 * bA) / 20.0
        val t = (50000.0 / 13.0) * eT * SURROUND_NC * ncb * sqrt(a * a + b * b) / (u + 0.305)
        val chroma = t.pow(0.9) * sqrt(j / 100.0) * (1.64 - 0.29.pow(nRatio)).pow(0.73)
        return doubleArrayOf(hue, chroma, j)
    }

    /** HCT (hue, chroma, tone) for a packed ARGB color. */
    fun hctOf(argb: Int): DoubleArray {
        val xyz = xyzFromArgb(argb)
        val cam = cam16FromXyz(xyz)
        return doubleArrayOf(cam[0], cam[1], lstarFromY(xyz[1]))
    }

    // ------------------------------------------------------------------
    // CIECAM16 inverse (J, C, h -> XYZ)
    // ------------------------------------------------------------------

    fun xyzFromJch(j: Double, chroma: Double, hueDegrees: Double): DoubleArray {
        val hueRad = hueDegrees * PI / 180.0
        val alpha = if (chroma <= 0.0 || j <= 0.0) 0.0 else chroma / sqrt(j / 100.0)
        val t = (alpha / (1.64 - 0.29.pow(nRatio)).pow(0.73)).pow(1.0 / 0.9)
        val eT = 0.25 * (cos(hueRad + 2.0) + 3.8)
        val p1 = eT * (50000.0 / 13.0) * SURROUND_NC * ncb
        val ac = aw * (j / 100.0).pow(1.0 / (SURROUND_C * zBase))
        val p2 = ac / nbb
        val gamma = 23.0 * (p2 + 0.305) * t /
            (23.0 * p1 + 11.0 * t * cos(hueRad) + 108.0 * t * sin(hueRad))
        val a = gamma * cos(hueRad)
        val b = gamma * sin(hueRad)
        val rA = (460.0 * p2 + 451.0 * a + 288.0 * b) / 1403.0
        val gA = (460.0 * p2 - 891.0 * a - 261.0 * b) / 1403.0
        val bA = (460.0 * p2 - 220.0 * a - 6300.0 * b) / 1403.0
        val cone = doubleArrayOf(
            100.0 * inverseAdaptedResponse(rA) / rgbD[0],
            100.0 * inverseAdaptedResponse(gA) / rgbD[1],
            100.0 * inverseAdaptedResponse(bA) / rgbD[2],
        )
        return matVec(CAT16_INV, cone)
    }

    // ------------------------------------------------------------------
    // HCT solve: exact tone, exact hue, max in-gamut chroma
    // ------------------------------------------------------------------

    fun argbFromHct(hue: Double, chroma: Double, tone: Double): Int {
        if (tone <= 0.0) return 0xFF000000.toInt()
        if (tone >= 100.0) return 0xFFFFFFFF.toInt()
        val h = ((hue % 360.0) + 360.0) % 360.0
        val targetY = yFromLstar(tone)
        if (chroma < 0.5) {
            val scale = targetY / WP_Y
            return argbFromXyz(doubleArrayOf(WP_X * scale, WP_Y * scale, WP_Z * scale))
        }
        // Outer bisection: the largest chroma <= request that stays in gamut.
        var lo = 0.0
        var hi = chroma
        var best = solveXyzAtChroma(h, 0.0, targetY)
        repeat(14) {
            val mid = (lo + hi) / 2.0
            val candidate = solveXyzAtChroma(h, mid, targetY)
            if (inGamut(candidate)) {
                best = candidate
                lo = mid
            } else {
                hi = mid
            }
        }
        return argbFromXyz(best)
    }

    /** XYZ with J bisected so the resulting Y equals [targetY]. */
    private fun solveXyzAtChroma(hue: Double, chroma: Double, targetY: Double): DoubleArray {
        var jLo = 0.0
        var jHi = 100.0
        var xyz = xyzFromJch(50.0, chroma, hue)
        repeat(16) {
            val jMid = (jLo + jHi) / 2.0
            xyz = xyzFromJch(jMid, chroma, hue)
            if (xyz[1] < targetY) jLo = jMid else jHi = jMid
        }
        return xyz
    }

    private fun inGamut(xyz: DoubleArray): Boolean {
        val eps = 1e-4
        return linearRgbFromXyz(xyz).all { it >= -eps && it <= 1.0 + eps }
    }

    // ------------------------------------------------------------------
    // Matrix helpers
    // ------------------------------------------------------------------

    private fun matVec(m: Array<DoubleArray>, v: DoubleArray): DoubleArray =
        DoubleArray(3) { i -> m[i][0] * v[0] + m[i][1] * v[1] + m[i][2] * v[2] }

    private fun invert3x3(m: Array<DoubleArray>): Array<DoubleArray> {
        val a = m[0][0]; val b = m[0][1]; val c = m[0][2]
        val d = m[1][0]; val e = m[1][1]; val f = m[1][2]
        val g = m[2][0]; val h = m[2][1]; val i = m[2][2]
        val det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        return arrayOf(
            doubleArrayOf((e * i - f * h) / det, (c * h - b * i) / det, (b * f - c * e) / det),
            doubleArrayOf((f * g - d * i) / det, (a * i - c * g) / det, (c * d - a * f) / det),
            doubleArrayOf((d * h - e * g) / det, (b * g - a * h) / det, (a * e - b * d) / det),
        )
    }
}

/** A tonal palette: one hue/chroma pair rendered at any tone (CIE L*). */
class TonalPalette(val hue: Double, val chroma: Double) {
    fun tone(tone: Int): Int = HctMath.argbFromHct(hue, chroma, tone.toDouble())

    companion object {
        fun fromHueAndChroma(hue: Double, chroma: Double): TonalPalette =
            TonalPalette(((hue % 360.0) + 360.0) % 360.0, chroma)

        fun fromArgb(argb: Int): TonalPalette {
            val hct = HctMath.hctOf(argb)
            return TonalPalette(hct[0], hct[1])
        }
    }
}
