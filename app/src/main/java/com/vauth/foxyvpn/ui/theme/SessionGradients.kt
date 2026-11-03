package com.vauth.foxyvpn.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Session gradients for the Pixel/Old designs (Phase B2) — the design
 * values of ZedSecure v3.1.8's gradient system (DESIGN-SYSTEM §2/§6):
 * five 3-stop session palettes built from the brand colors, chosen by
 * session key mod 5; the connected gradient prepends DeepViolet; the
 * idle gradient is DeepViolet → Violet → HotPink at 70%.
 *
 * The session key is the connection's start timestamp (a new key per
 * session, stable within it) — FoxyVPN has no numeric session id.
 */
object SessionGradients {
    val DeepViolet = Color(0xFF2A1361)
    val Violet = Color(0xFF7A5CFF)
    val HotPink = Color(0xFFFF5FA2)
    val Lime = Color(0xFFC7F24E)
    val Cyan = Color(0xFF37E0D8)

    val sessionPalettes: List<List<Color>> = listOf(
        listOf(Violet, HotPink, Lime),
        listOf(DeepViolet, Violet, Cyan),
        listOf(HotPink, Violet, Cyan),
        listOf(Violet, Cyan, Lime),
        listOf(DeepViolet, HotPink, Violet),
    )

    fun paletteFor(sessionKey: Long): List<Color> {
        val idx = ((sessionKey % sessionPalettes.size) + sessionPalettes.size) % sessionPalettes.size
        return sessionPalettes[idx.toInt()]
    }

    fun connectedStops(sessionKey: Long): List<Color> = listOf(DeepViolet) + paletteFor(sessionKey)

    val connectingStops: List<Color> = listOf(DeepViolet, Violet, HotPink)

    fun brushFor(stops: List<Color>): Brush = Brush.linearGradient(
        colors = stops,
        start = Offset.Zero,
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
    )

    val idle: Brush
        get() = Brush.linearGradient(
            colors = listOf(DeepViolet, Violet, HotPink.copy(alpha = 0.7f)),
            start = Offset.Zero,
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
        )

    val connecting: Brush
        get() = Brush.linearGradient(
            colors = listOf(Violet, Cyan, Lime),
            start = Offset.Zero,
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
        )
}
