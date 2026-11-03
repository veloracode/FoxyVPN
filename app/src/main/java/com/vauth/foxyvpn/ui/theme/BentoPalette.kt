package com.vauth.foxyvpn.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.max

/**
 * Bento color system — Phase B1 of the Zed overhaul.
 *
 * Every value below is ZedSecure v3.1.8's Bento palette spec as extracted
 * into DESIGN-SYSTEM §3.2 (eight hand-authored palettes in dark + light,
 * plus the HCT tonal generators behind Accent/Pastel/Vivid/Mono). The
 * code is original; the numbers are the design spec. Tonal generation
 * runs on the in-repo HCT implementation ([HctMath]).
 */

@Immutable
data class BentoTone(val container: Color, val content: Color)

@Immutable
data class BentoColors(
    val dark: Boolean,
    val ground: Color,
    val surface: Color,
    val surface2: Color,
    val line: Color,
    val ink: Color,
    val muted: Color,
    val stage: Color,
    val onStage: Color,
    val chipOnStage: Color,
    val goodOnStage: Color,
    val lime: Color,
    val onLime: Color,
    val blue: BentoTone,
    val violet: BentoTone,
    val cyan: BentoTone,
    val pink: BentoTone,
    val coral: BentoTone,
    val good: Color,
    val mid: Color,
    val bad: Color,
    val selected: Color,
    val dock: Color,
    val dockLine: Color,
    val danger: Color,
    val onDanger: Color,
    val colorful: Boolean = false,
) {
    val stageTone: BentoTone get() = BentoTone(stage, onStage)
    val limeTone: BentoTone get() = BentoTone(lime, onLime)
    val surfaceTone: BentoTone get() = BentoTone(surface, ink)

    fun tone(kind: BentoToneKind): BentoTone = when (kind) {
        BentoToneKind.Stage -> stageTone
        BentoToneKind.Lime -> limeTone
        BentoToneKind.Blue -> blue
        BentoToneKind.Violet -> violet
        BentoToneKind.Cyan -> cyan
        BentoToneKind.Pink -> pink
        BentoToneKind.Coral -> coral
        BentoToneKind.Surface -> surfaceTone
    }

    /** Ping quality color: <=0 unknown/bad, <300 good, <800 mid, else bad. */
    fun pingColor(ms: Int): Color = when {
        ms <= 0 -> bad
        ms < 300 -> good
        ms < 800 -> mid
        else -> bad
    }
}

private fun c(hex: Long): Color = Color(0xFF000000L or hex)

object BentoPalettes {

    val BrandDark = BentoColors(
        dark = true,
        ground = c(0x0B0A10), surface = c(0x18161F), surface2 = c(0x24212D),
        line = c(0x3A3646), ink = c(0xEEEAF6), muted = c(0xA6A0B8),
        stage = c(0x311B6B), onStage = c(0xEFE8FF),
        chipOnStage = Color(0x1FFFFFFF), goodOnStage = c(0x9BF0AE),
        lime = c(0xC7F24E), onLime = c(0x1A2200),
        blue = BentoTone(c(0x15294A), c(0xCFE1FF)),
        violet = BentoTone(c(0x2A2352), c(0xDDD5FF)),
        cyan = BentoTone(c(0x103D3A), c(0xB3F1EC)),
        pink = BentoTone(c(0x4A1C35), c(0xFFD0E5)),
        coral = BentoTone(c(0x4A251D), c(0xFFD3C6)),
        good = c(0x7BE495), mid = c(0xF2C14E), bad = c(0xFF8A80),
        selected = c(0x2B1D57), dock = c(0x1E1B27), dockLine = Color(0x24FFFFFF),
        danger = c(0xFFB4AB), onDanger = c(0x690005),
    )

    val BrandLight = BentoColors(
        dark = false,
        ground = c(0xF6F6F0), surface = c(0xEAEAE2), surface2 = c(0xDCDCD2),
        line = c(0xC5C5BA), ink = c(0x16161C), muted = c(0x55555F),
        stage = c(0x2A1361), onStage = c(0xF1EBFF),
        chipOnStage = Color(0x24FFFFFF), goodOnStage = c(0x9BF0AE),
        lime = c(0xC7F24E), onLime = c(0x1A2200),
        blue = BentoTone(c(0xD4E4FF), c(0x123A70)),
        violet = BentoTone(c(0xE3DBFF), c(0x36268A)),
        cyan = BentoTone(c(0xC6F1ED), c(0x0B4A45)),
        pink = BentoTone(c(0xFFD6E7), c(0x6E1543)),
        coral = BentoTone(c(0xFFDCCD), c(0x6D2914)),
        good = c(0x16803F), mid = c(0x8A5A00), bad = c(0xB3261E),
        selected = c(0xE3DBFF), dock = Color(0xFFFFFFFF), dockLine = Color(0x1A16161C),
        danger = c(0xFFDAD6), onDanger = c(0x410002),
    )

    val CitrusDark = BentoColors(
        dark = true,
        ground = c(0x0C0D07), surface = c(0x181A0F), surface2 = c(0x23261A),
        line = c(0x3A3D2A), ink = c(0xF1F2E4), muted = c(0xB0B39A),
        stage = c(0x2D3A0B), onStage = c(0xEEF8CF),
        chipOnStage = BrandDark.chipOnStage, goodOnStage = BrandDark.goodOnStage,
        lime = c(0xE2F53A), onLime = c(0x1F2400),
        blue = BentoTone(c(0x3B3608), c(0xFFF3A1)),
        violet = BentoTone(c(0x123A2A), c(0xB9F5D7)),
        cyan = BentoTone(c(0x22370F), c(0xD3F5A7)),
        pink = BentoTone(c(0x40280C), c(0xFFD6A6)),
        coral = BentoTone(c(0x43201A), c(0xFFC8BC)),
        good = BrandDark.good, mid = BrandDark.mid, bad = BrandDark.bad,
        selected = c(0x3A4A12), dock = c(0x23261A), dockLine = BrandDark.dockLine,
        danger = BrandDark.danger, onDanger = BrandDark.onDanger,
        colorful = true,
    )

    val CitrusLight = BentoColors(
        dark = false,
        ground = c(0xF7F8EC), surface = c(0xEBEDDB), surface2 = c(0xDEE1C9),
        line = c(0xC6C9AE), ink = c(0x17180F), muted = c(0x565A44),
        stage = c(0x2D3A0B), onStage = c(0xF2FBD6),
        chipOnStage = BrandLight.chipOnStage, goodOnStage = BrandLight.goodOnStage,
        lime = c(0xD8EE2C), onLime = c(0x1F2400),
        blue = BentoTone(c(0xFFF3B0), c(0x5A4A00)),
        violet = BentoTone(c(0xCFF5E2), c(0x0E4A33)),
        cyan = BentoTone(c(0xE0F5C0), c(0x2C4A0C)),
        pink = BentoTone(c(0xFFE0BE), c(0x6A3A06)),
        coral = BentoTone(c(0xFFD9D0), c(0x6E2414)),
        good = BrandLight.good, mid = BrandLight.mid, bad = BrandLight.bad,
        selected = c(0xE5F0B8), dock = Color(0xFFFFFFFF), dockLine = BrandLight.dockLine,
        danger = BrandLight.danger, onDanger = BrandLight.onDanger,
        colorful = true,
    )

    val MatchaDark = BentoColors(
        dark = true,
        ground = c(0x0A0D0A), surface = c(0x151A15), surface2 = c(0x20271F),
        line = c(0x364034), ink = c(0xE8F0E6), muted = c(0xA3B09F),
        stage = c(0x1F3D27), onStage = c(0xDDF3E2),
        chipOnStage = BrandDark.chipOnStage, goodOnStage = BrandDark.goodOnStage,
        lime = c(0xB4E36B), onLime = c(0x172600),
        blue = BentoTone(c(0x1E3326), c(0xCDEDD6)),
        violet = BentoTone(c(0x2C3416), c(0xE1EFB6)),
        cyan = BentoTone(c(0x0F393A), c(0xB2EDEE)),
        pink = BentoTone(c(0x3D2619), c(0xF4CDB6)),
        coral = BentoTone(c(0x36301E), c(0xF2E6C2)),
        good = BrandDark.good, mid = BrandDark.mid, bad = BrandDark.bad,
        selected = c(0x28452F), dock = c(0x20271F), dockLine = BrandDark.dockLine,
        danger = BrandDark.danger, onDanger = BrandDark.onDanger,
        colorful = true,
    )

    val MatchaLight = BentoColors(
        dark = false,
        ground = c(0xF4F7F2), surface = c(0xE7EDE4), surface2 = c(0xD9E1D5),
        line = c(0xBFC9BB), ink = c(0x141A13), muted = c(0x4E594B),
        stage = c(0x1F3D27), onStage = c(0xE2F6E7),
        chipOnStage = BrandLight.chipOnStage, goodOnStage = BrandLight.goodOnStage,
        lime = c(0xA6D65A), onLime = c(0x172600),
        blue = BentoTone(c(0xD3EEDB), c(0x1C4A2B)),
        violet = BentoTone(c(0xE6F0C2), c(0x3F4A0E)),
        cyan = BentoTone(c(0xC6EEEE), c(0x0B4748)),
        pink = BentoTone(c(0xF7DCCB), c(0x6A3415)),
        coral = BentoTone(c(0xF3EAC9), c(0x5A4A14)),
        good = BrandLight.good, mid = BrandLight.mid, bad = BrandLight.bad,
        selected = c(0xD6EAD9), dock = Color(0xFFFFFFFF), dockLine = BrandLight.dockLine,
        danger = BrandLight.danger, onDanger = BrandLight.onDanger,
        colorful = true,
    )

    val NeonDark = BentoColors(
        dark = true,
        ground = c(0x08070C), surface = c(0x15131C), surface2 = c(0x201D2A),
        line = c(0x383346), ink = c(0xF2EEFA), muted = c(0xABA3BF),
        stage = c(0x3D0B4F), onStage = c(0xFBDDFF),
        chipOnStage = BrandDark.chipOnStage, goodOnStage = BrandDark.goodOnStage,
        lime = c(0xB9FF3B), onLime = c(0x142400),
        blue = BentoTone(c(0x4A0E3B), c(0xFFC1EA)),
        violet = BentoTone(c(0x07344A), c(0xA3EAFF)),
        cyan = BentoTone(c(0x2B1657), c(0xE1D0FF)),
        pink = BentoTone(c(0x373A08), c(0xF8FFA0)),
        coral = BentoTone(c(0x4A1810), c(0xFFC5B5)),
        good = BrandDark.good, mid = BrandDark.mid, bad = BrandDark.bad,
        selected = c(0x4A1060), dock = c(0x201D2A), dockLine = BrandDark.dockLine,
        danger = BrandDark.danger, onDanger = BrandDark.onDanger,
        colorful = true,
    )

    val NeonLight = BentoColors(
        dark = false,
        ground = c(0xF8F6FB), surface = c(0xECE8F2), surface2 = c(0xDED8E8),
        line = c(0xC5BDD3), ink = c(0x15121C), muted = c(0x554C66),
        stage = c(0x3D0B4F), onStage = c(0xFCE2FF),
        chipOnStage = BrandLight.chipOnStage, goodOnStage = BrandLight.goodOnStage,
        lime = c(0xA8F21E), onLime = c(0x142400),
        blue = BentoTone(c(0xFFD6F1), c(0x6E0F55)),
        violet = BentoTone(c(0xC8F1FF), c(0x00475E)),
        cyan = BentoTone(c(0xE7DBFF), c(0x3D1F80)),
        pink = BentoTone(c(0xF6FAB4), c(0x4F5200)),
        coral = BentoTone(c(0xFFDCD2), c(0x741D0A)),
        good = BrandLight.good, mid = BrandLight.mid, bad = BrandLight.bad,
        selected = c(0xF1DDF7), dock = Color(0xFFFFFFFF), dockLine = BrandLight.dockLine,
        danger = BrandLight.danger, onDanger = BrandLight.onDanger,
        colorful = true,
    )

    val PistachioDark = BentoColors(
        dark = true,
        ground = c(0x0B0C0A), surface = c(0x171915), surface2 = c(0x22251F),
        line = c(0x393D34), ink = c(0xEEF1E9), muted = c(0xA8AE9F),
        stage = c(0x2B2348), onStage = c(0xE9E2FF),
        chipOnStage = BrandDark.chipOnStage, goodOnStage = BrandDark.goodOnStage,
        lime = c(0xD3F2A2), onLime = c(0x1E2A08),
        blue = BentoTone(c(0x2A3420), c(0xDDEFC7)),
        violet = BentoTone(c(0x2C2547), c(0xE2DAFF)),
        cyan = BentoTone(c(0x14363A), c(0xC2EDF0)),
        pink = BentoTone(c(0x43242E), c(0xFFD6E1)),
        coral = BentoTone(c(0x3A3420), c(0xF7EBC4)),
        good = BrandDark.good, mid = BrandDark.mid, bad = BrandDark.bad,
        selected = c(0x352C57), dock = c(0x22251F), dockLine = BrandDark.dockLine,
        danger = BrandDark.danger, onDanger = BrandDark.onDanger,
        colorful = true,
    )

    val PistachioLight = BentoColors(
        dark = false,
        ground = c(0xF6F7F2), surface = c(0xEAECE3), surface2 = c(0xDDE0D4),
        line = c(0xC4C8BA), ink = c(0x151713), muted = c(0x535849),
        stage = c(0x2B2348), onStage = c(0xEEE8FF),
        chipOnStage = BrandLight.chipOnStage, goodOnStage = BrandLight.goodOnStage,
        lime = c(0xC6EB8A), onLime = c(0x1E2A08),
        blue = BentoTone(c(0xE4F0D2), c(0x344A1A)),
        violet = BentoTone(c(0xE7E0FF), c(0x3A2C7A)),
        cyan = BentoTone(c(0xCFEFF1), c(0x134A50)),
        pink = BentoTone(c(0xFFDCE6), c(0x6E2338)),
        coral = BentoTone(c(0xF7EDCC), c(0x5A4614)),
        good = BrandLight.good, mid = BrandLight.mid, bad = BrandLight.bad,
        selected = c(0xE3DDF7), dock = Color(0xFFFFFFFF), dockLine = BrandLight.dockLine,
        danger = BrandLight.danger, onDanger = BrandLight.onDanger,
        colorful = true,
    )

    val OceanDark = BentoColors(
        dark = true,
        ground = c(0x070B10), surface = c(0x121923), surface2 = c(0x1C2431),
        line = c(0x313D4F), ink = c(0xE7EEF8), muted = c(0x9FAABD),
        stage = c(0x0E2F57), onStage = c(0xD8E9FF),
        chipOnStage = BrandDark.chipOnStage, goodOnStage = BrandDark.goodOnStage,
        lime = c(0x5CF2C8), onLime = c(0x00281D),
        blue = BentoTone(c(0x10304F), c(0xC4E2FF)),
        violet = BentoTone(c(0x1E2357), c(0xD6D9FF)),
        cyan = BentoTone(c(0x0A3838), c(0xA8F0EC)),
        pink = BentoTone(c(0x0A3340), c(0xA6E8FF)),
        coral = BentoTone(c(0x38301F), c(0xF2E2BA)),
        good = BrandDark.good, mid = BrandDark.mid, bad = BrandDark.bad,
        selected = c(0x123A6A), dock = c(0x1C2431), dockLine = BrandDark.dockLine,
        danger = BrandDark.danger, onDanger = BrandDark.onDanger,
        colorful = true,
    )

    val OceanLight = BentoColors(
        dark = false,
        ground = c(0xF3F7FB), surface = c(0xE4EBF3), surface2 = c(0xD5DEE9),
        line = c(0xB9C5D4), ink = c(0x0F1620), muted = c(0x4A5768),
        stage = c(0x0E2F57), onStage = c(0xDDEBFF),
        chipOnStage = BrandLight.chipOnStage, goodOnStage = BrandLight.goodOnStage,
        lime = c(0x3BE0B4), onLime = c(0x00281D),
        blue = BentoTone(c(0xD2E6FF), c(0x0F3B6E)),
        violet = BentoTone(c(0xDCDEFF), c(0x262E80)),
        cyan = BentoTone(c(0xC4F1EC), c(0x0A4A45)),
        pink = BentoTone(c(0xC6ECF7), c(0x064559)),
        coral = BentoTone(c(0xF3E8CC), c(0x5A4614)),
        good = BrandLight.good, mid = BrandLight.mid, bad = BrandLight.bad,
        selected = c(0xD6E5F7), dock = Color(0xFFFFFFFF), dockLine = BrandLight.dockLine,
        danger = BrandLight.danger, onDanger = BrandLight.onDanger,
        colorful = true,
    )

    val SunsetDark = BentoColors(
        dark = true,
        ground = c(0x0E0A09), surface = c(0x1C1513), surface2 = c(0x28201D),
        line = c(0x43362F), ink = c(0xF8EDE8), muted = c(0xBBA79E),
        stage = c(0x4A1F12), onStage = c(0xFFE3D6),
        chipOnStage = BrandDark.chipOnStage, goodOnStage = BrandDark.goodOnStage,
        lime = c(0xFFB547), onLime = c(0x2E1800),
        blue = BentoTone(c(0x4A2A16), c(0xFFD8B8)),
        violet = BentoTone(c(0x2E1A40), c(0xE6D2FF)),
        cyan = BentoTone(c(0x3F3110), c(0xFFE7A3)),
        pink = BentoTone(c(0x4A1A2A), c(0xFFC6D9)),
        coral = BentoTone(c(0x4A1F1A), c(0xFFC4B8)),
        good = BrandDark.good, mid = BrandDark.mid, bad = BrandDark.bad,
        selected = c(0x5A2A18), dock = c(0x28201D), dockLine = BrandDark.dockLine,
        danger = BrandDark.danger, onDanger = BrandDark.onDanger,
        colorful = true,
    )

    val SunsetLight = BentoColors(
        dark = false,
        ground = c(0xFBF6F3), surface = c(0xF1E8E3), surface2 = c(0xE5D9D2),
        line = c(0xCDBDB4), ink = c(0x1C1310), muted = c(0x64514A),
        stage = c(0x4A1F12), onStage = c(0xFFE6DA),
        chipOnStage = BrandLight.chipOnStage, goodOnStage = BrandLight.goodOnStage,
        lime = c(0xFFA62B), onLime = c(0x2E1800),
        blue = BentoTone(c(0xFFE2C9), c(0x6A3A10)),
        violet = BentoTone(c(0xEBDDFF), c(0x4A2A70)),
        cyan = BentoTone(c(0xFFEDB8), c(0x5A4300)),
        pink = BentoTone(c(0xFFD9E5), c(0x6E1A38)),
        coral = BentoTone(c(0xFFDAD0), c(0x742214)),
        good = BrandLight.good, mid = BrandLight.mid, bad = BrandLight.bad,
        selected = c(0xF5DCD0), dock = Color(0xFFFFFFFF), dockLine = BrandLight.dockLine,
        danger = BrandLight.danger, onDanger = BrandLight.onDanger,
        colorful = true,
    )

    val BerryDark = BentoColors(
        dark = true,
        ground = c(0x0D080C), surface = c(0x1B1219), surface2 = c(0x271B24),
        line = c(0x42303D), ink = c(0xF7EAF2), muted = c(0xB8A2B0),
        stage = c(0x4A0E3A), onStage = c(0xFFD9F2),
        chipOnStage = BrandDark.chipOnStage, goodOnStage = BrandDark.goodOnStage,
        lime = c(0xD7F59A), onLime = c(0x1E2A06),
        blue = BentoTone(c(0x4A1230), c(0xFFC6E0)),
        violet = BentoTone(c(0x341B4A), c(0xEBD3FF)),
        cyan = BentoTone(c(0x123A2C), c(0xB8F2D8)),
        pink = BentoTone(c(0x4A1C24), c(0xFFCCD5)),
        coral = BentoTone(c(0x46281A), c(0xFFD9C2)),
        good = BrandDark.good, mid = BrandDark.mid, bad = BrandDark.bad,
        selected = c(0x5A1548), dock = c(0x271B24), dockLine = BrandDark.dockLine,
        danger = BrandDark.danger, onDanger = BrandDark.onDanger,
        colorful = true,
    )

    val BerryLight = BentoColors(
        dark = false,
        ground = c(0xFBF5F8), surface = c(0xF1E6EC), surface2 = c(0xE5D6DF),
        line = c(0xCDB9C5), ink = c(0x1C1018), muted = c(0x634C5A),
        stage = c(0x4A0E3A), onStage = c(0xFFDDF3),
        chipOnStage = BrandLight.chipOnStage, goodOnStage = BrandLight.goodOnStage,
        lime = c(0xC4EC7C), onLime = c(0x1E2A06),
        blue = BentoTone(c(0xFFD7E9), c(0x6E1240)),
        violet = BentoTone(c(0xEEDDFF), c(0x4F2A72)),
        cyan = BentoTone(c(0xC9F2DF), c(0x0F4A34)),
        pink = BentoTone(c(0xFFD9E0), c(0x6E1C2C)),
        coral = BentoTone(c(0xFFE0CE), c(0x6A3414)),
        good = BrandLight.good, mid = BrandLight.mid, bad = BrandLight.bad,
        selected = c(0xF5D8E9), dock = Color(0xFFFFFFFF), dockLine = BrandLight.dockLine,
        danger = BrandLight.danger, onDanger = BrandLight.onDanger,
        colorful = true,
    )

    /** The palette resolver: authored tables, or the HCT tonal generators. */
    fun of(palette: BentoPalette, dark: Boolean, seed: Color, amoled: Boolean): BentoColors {
        val base = when (palette) {
            BentoPalette.Brand -> if (dark) BrandDark else BrandLight
            BentoPalette.Accent -> tonal(seed, dark, TonalKind.Accent)
            BentoPalette.Pastel -> tonal(seed, dark, TonalKind.Pastel)
            BentoPalette.Vivid -> tonal(seed, dark, TonalKind.Vivid)
            BentoPalette.Mono -> mono(seed, dark)
            BentoPalette.Citrus -> if (dark) CitrusDark else CitrusLight
            BentoPalette.Matcha -> if (dark) MatchaDark else MatchaLight
            BentoPalette.Neon -> if (dark) NeonDark else NeonLight
            BentoPalette.Pistachio -> if (dark) PistachioDark else PistachioLight
            BentoPalette.Ocean -> if (dark) OceanDark else OceanLight
            BentoPalette.Sunset -> if (dark) SunsetDark else SunsetLight
            BentoPalette.Berry -> if (dark) BerryDark else BerryLight
        }
        return if (amoled && dark) base.amoled() else base
    }

    private enum class TonalKind { Accent, Pastel, Vivid }

    private fun tonal(seed: Color, dark: Boolean, kind: TonalKind): BentoColors {
        val seedArgb = seed.toArgbInt()
        val hct = HctMath.hctOf(seedArgb)
        val hue = hct[0]
        val neutral = TonalPalette.fromHueAndChroma(hue, 6.0)
        val neutralVariant = TonalPalette.fromHueAndChroma(hue, 10.0)
        val accent = TonalPalette.fromHueAndChroma(
            hue,
            when (kind) {
                TonalKind.Accent -> max(hct[1], 56.0)
                TonalKind.Pastel -> 40.0
                TonalKind.Vivid -> 92.0
            },
        )
        val stage = TonalPalette.fromHueAndChroma(hue, if (kind == TonalKind.Pastel) 40.0 else 64.0)
        val tileChroma = when (kind) {
            TonalKind.Accent -> 36.0
            TonalKind.Pastel -> 26.0
            TonalKind.Vivid -> 66.0
        }
        fun tile(offset: Double): BentoTone {
            val p = TonalPalette.fromHueAndChroma(hue + offset, tileChroma)
            return when (kind) {
                TonalKind.Pastel -> BentoTone(
                    Color(p.tone(if (dark) 82 else 92)),
                    Color(p.tone(if (dark) 16 else 24)),
                )
                TonalKind.Vivid -> BentoTone(
                    Color(p.tone(if (dark) 38 else 76)),
                    Color(p.tone(if (dark) 96 else 10)),
                )
                TonalKind.Accent -> if (dark) {
                    BentoTone(Color(p.tone(20)), Color(p.tone(88)))
                } else {
                    BentoTone(Color(p.tone(90)), Color(p.tone(24)))
                }
            }
        }
        val goodPalette = TonalPalette.fromHueAndChroma(145.0, 48.0)
        val midPalette = TonalPalette.fromHueAndChroma(80.0, 60.0)
        val badPalette = TonalPalette.fromHueAndChroma(25.0, 70.0)
        return BentoColors(
            dark = dark,
            ground = Color(neutral.tone(if (dark) 4 else 97)),
            surface = Color(neutral.tone(if (dark) 10 else 92)),
            surface2 = Color(neutral.tone(if (dark) 15 else 87)),
            line = Color(neutralVariant.tone(if (dark) 26 else 78)),
            ink = Color(neutral.tone(if (dark) 93 else 8)),
            muted = Color(neutralVariant.tone(if (dark) 70 else 36)),
            stage = Color(stage.tone(if (dark) 22 else 18)),
            onStage = Color(stage.tone(if (dark) 92 else 95)),
            chipOnStage = Color.White.copy(alpha = if (dark) 0.12f else 0.14f),
            goodOnStage = Color(goodPalette.tone(88)),
            lime = Color(accent.tone(88)),
            onLime = Color(accent.tone(14)),
            blue = tile(60.0),
            violet = tile(120.0),
            cyan = tile(180.0),
            pink = tile(240.0),
            coral = tile(300.0),
            good = Color(goodPalette.tone(if (dark) 80 else 40)),
            mid = Color(midPalette.tone(if (dark) 80 else 42)),
            bad = Color(badPalette.tone(if (dark) 72 else 40)),
            selected = Color(stage.tone(if (dark) 20 else 90)),
            dock = Color(neutral.tone(if (dark) 13 else 99)),
            dockLine = (if (dark) Color.White else Color.Black)
                .copy(alpha = if (dark) 0.08f else 0.10f),
            danger = Color(badPalette.tone(if (dark) 80 else 90)),
            onDanger = Color(badPalette.tone(if (dark) 20 else 10)),
        )
    }

    private fun mono(seed: Color, dark: Boolean): BentoColors {
        val hue = HctMath.hctOf(seed.toArgbInt())[0]
        val n = TonalPalette.fromHueAndChroma(hue, 3.0)
        fun tile(dTone: Int, lTone: Int): BentoTone =
            if (dark) {
                BentoTone(Color(n.tone(dTone)), Color(n.tone(94)))
            } else {
                BentoTone(Color(n.tone(lTone)), Color(n.tone(10)))
            }
        val goodPalette = TonalPalette.fromHueAndChroma(145.0, 40.0)
        val midPalette = TonalPalette.fromHueAndChroma(80.0, 50.0)
        val badPalette = TonalPalette.fromHueAndChroma(25.0, 64.0)
        return BentoColors(
            dark = dark,
            ground = Color(n.tone(if (dark) 4 else 97)),
            surface = Color(n.tone(if (dark) 10 else 92)),
            surface2 = Color(n.tone(if (dark) 16 else 86)),
            line = Color(n.tone(if (dark) 27 else 76)),
            ink = Color(n.tone(if (dark) 94 else 8)),
            muted = Color(n.tone(if (dark) 70 else 36)),
            stage = Color(n.tone(if (dark) 22 else 16)),
            onStage = Color(n.tone(if (dark) 95 else 96)),
            chipOnStage = Color.White.copy(alpha = 0.12f),
            goodOnStage = Color(goodPalette.tone(88)),
            lime = Color(n.tone(if (dark) 94 else 12)),
            onLime = Color(n.tone(if (dark) 8 else 96)),
            blue = tile(14, 90),
            violet = tile(18, 86),
            cyan = tile(12, 88),
            pink = tile(20, 84),
            coral = tile(16, 91),
            good = Color(goodPalette.tone(if (dark) 80 else 38)),
            mid = Color(midPalette.tone(if (dark) 80 else 40)),
            bad = Color(badPalette.tone(if (dark) 72 else 40)),
            selected = Color(n.tone(if (dark) 20 else 84)),
            dock = Color(n.tone(if (dark) 13 else 99)),
            dockLine = (if (dark) Color.White else Color.Black)
                .copy(alpha = if (dark) 0.08f else 0.10f),
            danger = Color(badPalette.tone(if (dark) 80 else 90)),
            onDanger = Color(badPalette.tone(if (dark) 20 else 10)),
        )
    }

    private fun BentoColors.amoled(): BentoColors = copy(
        ground = Color.Black,
        surface = c(0x111015),
        surface2 = c(0x1B1A21),
        dock = c(0x141319),
    )
}

/** Compose Color -> packed ARGB int (sRGB). */
fun Color.toArgbInt(): Int {
    val a = (alpha * 255.0f + 0.5f).toInt().coerceIn(0, 255)
    val r = (red * 255.0f + 0.5f).toInt().coerceIn(0, 255)
    val g = (green * 255.0f + 0.5f).toInt().coerceIn(0, 255)
    val b = (blue * 255.0f + 0.5f).toInt().coerceIn(0, 255)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}
