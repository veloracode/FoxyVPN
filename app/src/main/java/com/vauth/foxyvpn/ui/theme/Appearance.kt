package com.vauth.foxyvpn.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.core.graphics.ColorUtils

/**
 * Interface/appearance options ported from ZedSecure's Ui settings page.
 * Design language studied from ZedSecure; all code below is original.
 */

enum class ColorStyle(val label: String) {
    // Enum name "ZedSecure" is kept so existing saved preferences still load.
    // OVERHAUL (Phase B1, DEV-10): the display label is ZedSecure's own name
    // for this style — "ZedSecure" (the v1 port showed "Signature").
    ZedSecure("ZedSecure"),
    TonalSpot("Tonal Spot"),
    Vibrant("Vibrant"),
    Expressive("Expressive"),
    Rainbow("Rainbow"),
    FruitSalad("Fruit Salad"),
    Fidelity("Fidelity"),
    Neutral("Neutral"),
    Monochrome("Monochrome"),
}

enum class ColorContrast(val label: String, val level: Double) {
    Standard("Standard", 0.0),
    Medium("Medium", 0.5),
    High("High", 1.0),
}

enum class AppLanguage(val label: String, val tag: String?) {
    System("System", null),
    English("English", "en"),
    Persian("فارسی", "fa"),
}

enum class UiFontScale(val label: String, val scale: Float) {
    Smallest("Smallest", 0.80f),
    Smaller("Smaller", 0.90f),
    Normal("Normal", 1.00f),
    Larger("Larger", 1.12f),
    Largest("Largest", 1.25f),
}

enum class ScreenTransition(val label: String) {
    Fade("Fade"),
    Slide("Slide"),
    Push("Push"),
    Depth("Depth"),
    Elastic("Elastic"),
    Curtain("Curtain"),
    Flip("Flip"),
}

enum class NavBarStyle(val label: String) {
    FloatingPill("Floating pill"),
    FullBar("Full bar"),
    ExpressivePill("Expressive pill"),
    CompactDock("Compact dock"),
    Underline("Underline"),
    Minimal("Minimal"),
    // OVERHAUL: Zed's seventh style — the floating Bento dock. It is the
    // Bento design's own nav style (BentoPrefs.navStyle defaults to it);
    // the other six remain selectable for the shared bottom bar.
    Dock("Dock"),
}

enum class ConnectButtonStyle(val label: String) {
    Pill("Pill"),
    Hero("Hero"),
    Ring("Ring"),
    Bar("Bar"),
    Icon("Icon"),
    Switch("Switch"),
}

/**
 * Card corner axis. OVERHAUL (Phase B1): values corrected to ZedSecure's —
 * (card radius dp, tile radius scale): Squared (6, 0.30), Rounded (22, 1.0),
 * Pill (34, 1.45). The v1 port's approximations (10/28/48, no scale) are
 * gone; stored enum names are unchanged, so saved values carry over.
 */
enum class CardCornerStyle(val label: String, val cardDp: Int, val tileScale: Float) {
    Squared("Squared", 6, 0.30f),
    Rounded("Rounded", 22, 1.0f),
    Pill("Pill", 34, 1.45f),
}

enum class ListDensity(val label: String) {
    Comfortable("Comfortable"),
    Compact("Compact"),
}

data class AccentPreset(val name: String, val light: Color, val dark: Color)

val AccentPresets: List<AccentPreset> = listOf(
    // Indices 0..9 stable so previously saved accentColorIndex values still map
    // to the same colours. Signal blue is appended; fresh installs default to it.
    AccentPreset("Iris", Color(0xFF5646D6), Color(0xFFC7BFFF)),
    AccentPreset("Ocean", Color(0xFF1E63D0), Color(0xFFAAC7FF)),
    AccentPreset("Teal", Color(0xFF00897E), Color(0xFF52DDCF)),
    AccentPreset("Forest", Color(0xFF2E7D32), Color(0xFF9BD89B)),
    AccentPreset("Amber", Color(0xFFB4690E), Color(0xFFFFB870)),
    AccentPreset("Rose", Color(0xFFC4326B), Color(0xFFFFB0C8)),
    AccentPreset("Crimson", Color(0xFFC0362C), Color(0xFFFFB4A9)),
    AccentPreset("Cyan", Color(0xFF00838F), Color(0xFF4DD0E1)),
    AccentPreset("Grape", Color(0xFF8E24AA), Color(0xFFE9B3FF)),
    AccentPreset("Slate", Color(0xFF4A5B7A), Color(0xFFB6C6EA)),
    AccentPreset("Signal blue", Color(0xFF2C6BED), Color(0xFF9DB8FF)),
)

/** Index of Signal blue — default only when no accent has been saved yet. */
val DEFAULT_ACCENT_INDEX: Int =
    AccentPresets.indexOfFirst { it.name == "Signal blue" }.coerceAtLeast(0)

fun accentSeed(index: Int, dark: Boolean): Color {
    val preset = AccentPresets.getOrNull(index) ?: AccentPresets[DEFAULT_ACCENT_INDEX]
    return if (dark) preset.dark else preset.light
}

private fun hslColor(h: Float, s: Float, l: Float): Color {
    val hue = ((h % 360f) + 360f) % 360f
    val packed = ColorUtils.HSLToColor(floatArrayOf(hue, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f)))
    return Color(packed)
}

private fun seedHsl(seed: Color): FloatArray {
    val out = FloatArray(3)
    ColorUtils.RGBToHSL(
        (seed.red * 255f).toInt(),
        (seed.green * 255f).toInt(),
        (seed.blue * 255f).toInt(),
        out,
    )
    return out
}

private fun Color.readableOn(): Color = if (luminance() > 0.5f) Color(0xFF101418) else Color.White

/**
 * Builds a full Material3 [ColorScheme] from a seed color, in the spirit of
 * ZedSecure's material-kolor based generator, using only androidx.core HSL
 * math (no extra dependency). Surfaces carry a subtle tint of the seed hue
 * so the app background feels alive but tasteful.
 */
fun seedColorScheme(
    dark: Boolean,
    seed: Color,
    style: ColorStyle,
    contrast: ColorContrast,
): ColorScheme {
    val hsl = seedHsl(seed)
    val hue = hsl[0]
    val satMul = when (style) {
        ColorStyle.ZedSecure, ColorStyle.TonalSpot -> 0.9f
        ColorStyle.Vibrant -> 1.25f
        ColorStyle.Expressive -> 1.1f
        ColorStyle.Rainbow -> 1.2f
        ColorStyle.FruitSalad -> 1.15f
        ColorStyle.Fidelity -> 1.0f
        ColorStyle.Neutral -> 0.55f
        ColorStyle.Monochrome -> 0.0f
    }
    val hueVar = when (style) {
        ColorStyle.Rainbow -> 90f
        ColorStyle.FruitSalad -> 60f
        ColorStyle.Expressive -> 45f
        else -> 32f
    }
    val s = (hsl[1] * satMul).coerceIn(0f, 1f)

    // Contrast pushes primaries toward vivid and surfaces toward pure.
    val contrastBoost = when (contrast) {
        ColorContrast.Standard -> 0f
        ColorContrast.Medium -> 0.06f
        ColorContrast.High -> 0.12f
    }

    val primary: Color
    val onPrimary: Color
    val primaryContainer: Color
    val onPrimaryContainer: Color
    val secondary: Color
    val onSecondary: Color
    val secondaryContainer: Color
    val onSecondaryContainer: Color
    val tertiary: Color
    val onTertiary: Color
    val tertiaryContainer: Color
    val onTertiaryContainer: Color
    val background: Color
    val onBackground: Color
    val surface: Color
    val onSurface: Color
    val surfaceVariant: Color
    val onSurfaceVariant: Color
    val surfaceTint: Color
    val surfaceBright: Color
    val surfaceDim: Color
    val surfaceContainer: Color
    val surfaceContainerHigh: Color
    val surfaceContainerHighest: Color
    val surfaceContainerLow: Color
    val surfaceContainerLowest: Color
    val outline: Color
    val outlineVariant: Color
    val inverseSurface: Color
    val inverseOnSurface: Color
    val error: Color
    val onError: Color
    val errorContainer: Color
    val onErrorContainer: Color

    if (!dark) {
        primary = hslColor(hue, (s + contrastBoost).coerceIn(0f, 1f), 0.42f)
        onPrimary = primary.readableOn()
        primaryContainer = hslColor(hue, s * 0.45f, 0.90f)
        onPrimaryContainer = hslColor(hue, s * 0.7f, 0.16f)
        secondary = hslColor(hue + hueVar, s * 0.8f, 0.44f)
        onSecondary = secondary.readableOn()
        secondaryContainer = hslColor(hue + hueVar, s * 0.4f, 0.90f)
        onSecondaryContainer = hslColor(hue + hueVar, s * 0.7f, 0.18f)
        tertiary = hslColor(hue - hueVar, s * 0.85f, 0.44f)
        onTertiary = tertiary.readableOn()
        tertiaryContainer = hslColor(hue - hueVar, s * 0.4f, 0.90f)
        onTertiaryContainer = hslColor(hue - hueVar, s * 0.7f, 0.18f)
        // Cheerful subtle tint: background carries ~6% of the seed hue.
        background = hslColor(hue, s * 0.10f, if (contrast == ColorContrast.High) 1f else 0.97f)
        onBackground = if (contrast == ColorContrast.High) Color.Black else Color(0xFF191C22)
        surface = hslColor(hue, s * 0.12f, if (contrast == ColorContrast.High) 1f else 0.96f)
        onSurface = if (contrast == ColorContrast.High) Color.Black else Color(0xFF191C22)
        surfaceVariant = hslColor(hue, s * 0.16f, 0.90f)
        onSurfaceVariant = hslColor(hue, s * 0.25f, 0.32f)
        surfaceTint = primary
        surfaceBright = hslColor(hue, s * 0.10f, 0.98f)
        surfaceDim = hslColor(hue, s * 0.14f, 0.88f)
        surfaceContainerLowest = hslColor(hue, s * 0.08f, 1f)
        surfaceContainerLow = hslColor(hue, s * 0.10f, 0.96f)
        surfaceContainer = hslColor(hue, s * 0.12f, 0.93f)
        surfaceContainerHigh = hslColor(hue, s * 0.14f, 0.90f)
        surfaceContainerHighest = hslColor(hue, s * 0.16f, 0.87f)
        outline = hslColor(hue, s * 0.20f, 0.55f)
        outlineVariant = hslColor(hue, s * 0.14f, 0.80f)
        inverseSurface = Color(0xFF2E3138)
        inverseOnSurface = Color(0xFFF0F1F5)
        error = hslColor(4f, 0.72f, 0.44f)
        onError = Color.White
        errorContainer = hslColor(4f, 0.6f, 0.90f)
        onErrorContainer = hslColor(4f, 0.7f, 0.18f)
    } else {
        primary = hslColor(hue, (s + contrastBoost).coerceIn(0f, 1f), 0.82f)
        onPrimary = primary.readableOn()
        primaryContainer = hslColor(hue, s * 0.7f, 0.30f)
        onPrimaryContainer = hslColor(hue, s * 0.5f, 0.90f)
        secondary = hslColor(hue + hueVar, s * 0.7f, 0.78f)
        onSecondary = secondary.readableOn()
        secondaryContainer = hslColor(hue + hueVar, s * 0.6f, 0.30f)
        onSecondaryContainer = hslColor(hue + hueVar, s * 0.5f, 0.90f)
        tertiary = hslColor(hue - hueVar, s * 0.7f, 0.78f)
        onTertiary = tertiary.readableOn()
        tertiaryContainer = hslColor(hue - hueVar, s * 0.6f, 0.30f)
        onTertiaryContainer = hslColor(hue - hueVar, s * 0.5f, 0.90f)
        background = hslColor(hue, s * 0.16f, if (contrast == ColorContrast.High) 0f else 0.07f)
        onBackground = if (contrast == ColorContrast.High) Color.White else Color(0xFFE3E5EA)
        surface = hslColor(hue, s * 0.16f, if (contrast == ColorContrast.High) 0f else 0.07f)
        onSurface = if (contrast == ColorContrast.High) Color.White else Color(0xFFE3E5EA)
        surfaceVariant = hslColor(hue, s * 0.20f, 0.20f)
        onSurfaceVariant = hslColor(hue, s * 0.25f, 0.72f)
        surfaceTint = primary
        surfaceBright = hslColor(hue, s * 0.16f, 0.16f)
        surfaceDim = hslColor(hue, s * 0.18f, 0.05f)
        surfaceContainerLowest = hslColor(hue, s * 0.18f, 0.04f)
        surfaceContainerLow = hslColor(hue, s * 0.16f, 0.09f)
        surfaceContainer = hslColor(hue, s * 0.16f, 0.12f)
        surfaceContainerHigh = hslColor(hue, s * 0.16f, 0.16f)
        surfaceContainerHighest = hslColor(hue, s * 0.18f, 0.21f)
        outline = hslColor(hue, s * 0.20f, 0.45f)
        outlineVariant = hslColor(hue, s * 0.22f, 0.26f)
        inverseSurface = Color(0xFFE3E5EA)
        inverseOnSurface = Color(0xFF2E3138)
        error = hslColor(4f, 0.72f, 0.72f)
        onError = Color(0xFF40090B)
        errorContainer = hslColor(4f, 0.6f, 0.30f)
        onErrorContainer = hslColor(4f, 0.6f, 0.90f)
    }

    val scheme = if (!dark) {
        lightColorScheme(
            primary = primary, onPrimary = onPrimary,
            primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
            inversePrimary = hslColor(hue, s, 0.80f),
            secondary = secondary, onSecondary = onSecondary,
            secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary, onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
            background = background, onBackground = onBackground,
            surface = surface, onSurface = onSurface,
            surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
            surfaceTint = surfaceTint,
            surfaceBright = surfaceBright, surfaceDim = surfaceDim,
            surfaceContainer = surfaceContainer, surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            surfaceContainerLow = surfaceContainerLow, surfaceContainerLowest = surfaceContainerLowest,
            outline = outline, outlineVariant = outlineVariant,
            inverseSurface = inverseSurface, inverseOnSurface = inverseOnSurface,
            error = error, onError = onError,
            errorContainer = errorContainer, onErrorContainer = onErrorContainer,
            scrim = Color.Black,
        )
    } else {
        darkColorScheme(
            primary = primary, onPrimary = onPrimary,
            primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
            inversePrimary = hslColor(hue, s, 0.42f),
            secondary = secondary, onSecondary = onSecondary,
            secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary, onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
            background = background, onBackground = onBackground,
            surface = surface, onSurface = onSurface,
            surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
            surfaceTint = surfaceTint,
            surfaceBright = surfaceBright, surfaceDim = surfaceDim,
            surfaceContainer = surfaceContainer, surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            surfaceContainerLow = surfaceContainerLow, surfaceContainerLowest = surfaceContainerLowest,
            outline = outline, outlineVariant = outlineVariant,
            inverseSurface = inverseSurface, inverseOnSurface = inverseOnSurface,
            error = error, onError = onError,
            errorContainer = errorContainer, onErrorContainer = onErrorContainer,
            scrim = Color.Black,
        )
    }
    return scheme
}

/** AMOLED variant: pure-black surfaces, like ZedSecure's amoled() extension. */
fun ColorScheme.applyAmoled(dark: Boolean): ColorScheme {
    if (!dark) return this
    return copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceBright = Color(0xFF1A1A1A),
        surfaceDim = Color.Black,
        surfaceContainer = Color(0xFF0F0F0F),
        surfaceContainerHigh = Color(0xFF161616),
        surfaceContainerHighest = Color(0xFF1E1E1E),
        surfaceContainerLow = Color(0xFF0B0B0B),
        surfaceContainerLowest = Color.Black,
    )
}

/**
 * Theme profiles. OVERHAUL (Phase B1): the v1 port's three invented
 * profiles (Lagoon/Sunset/Onyx) are replaced by ZedSecure v3.1.8's eleven,
 * values as extracted in DESIGN-SYSTEM §3.5. A profile carries an accent
 * index, an AMOLED flag, and the eleven personalize colors.
 */
data class ThemeProfile(
    val id: String,
    val name: String,
    val accentIndex: Int,
    val amoled: Boolean = false,
    val download: Long,
    val upload: Long,
    val downloadTile: Long,
    val uploadTile: Long,
    val connect: Long,
    val connected: Long,
    val tapHint: Long,
    val serverCard: Long,
    val serverActive: Long,
    val configCard: Long,
    val configCardText: Long,
) {
    /** Preview swatches (connect, download, upload), as in Zed's picker. */
    val swatches: List<Color>
        get() = listOf(Color(connect), Color(download), Color(upload))
}

val ThemeProfiles: List<ThemeProfile> = listOf(
    ThemeProfile(
        id = "aurora", name = "Aurora", accentIndex = 2,
        download = 0xFF4FE3D8, upload = 0xFFB49BFF,
        downloadTile = 0xFF0D3B37, uploadTile = 0xFF241A48,
        connect = 0xFF12A594, connected = 0xFF7C5CFF, tapHint = 0xFFA9E9E2,
        serverCard = 0xFF11302E, serverActive = 0xFF12A594,
        configCard = 0xFF12332F, configCardText = 0xFFEAFFFB,
    ),
    ThemeProfile(
        id = "ember", name = "Ember", accentIndex = 4,
        download = 0xFFFFB870, upload = 0xFFFF8A65,
        downloadTile = 0xFF3D2409, uploadTile = 0xFF43200F,
        connect = 0xFFE07A1F, connected = 0xFFC0392B, tapHint = 0xFFFFD9AE,
        serverCard = 0xFF33200C, serverActive = 0xFFE07A1F,
        configCard = 0xFF3A2410, configCardText = 0xFFFFF1E2,
    ),
    ThemeProfile(
        id = "midnight", name = "Midnight", accentIndex = 1, amoled = true,
        download = 0xFF7FB2FF, upload = 0xFF9BC4FF,
        downloadTile = 0xFF0B1730, uploadTile = 0xFF101B36,
        connect = 0xFF2563EB, connected = 0xFF1E3A8A, tapHint = 0xFFA9C6F5,
        serverCard = 0xFF0C1526, serverActive = 0xFF2563EB,
        configCard = 0xFF0D1932, configCardText = 0xFFE6EEFF,
    ),
    ThemeProfile(
        id = "sakura", name = "Sakura", accentIndex = 5,
        download = 0xFFFFA8C5, upload = 0xFFFFC2D6,
        downloadTile = 0xFF3E1226, uploadTile = 0xFF45182C,
        connect = 0xFFD9407A, connected = 0xFF8E2751, tapHint = 0xFFFFCFE0,
        serverCard = 0xFF351023, serverActive = 0xFFD9407A,
        configCard = 0xFF3B1428, configCardText = 0xFFFFF0F5,
    ),
    ThemeProfile(
        id = "citrus", name = "Citrus", accentIndex = 3,
        download = 0xFFC7F24E, upload = 0xFF8FD14F,
        downloadTile = 0xFF243007, uploadTile = 0xFF1E2A0A,
        connect = 0xFF9BC91F, connected = 0xFF3F6212, tapHint = 0xFFDDF59A,
        serverCard = 0xFF1E2708, serverActive = 0xFF9BC91F,
        configCard = 0xFF232E09, configCardText = 0xFFF2FFD6,
    ),
    ThemeProfile(
        id = "mono", name = "Mono", accentIndex = 9,
        download = 0xFFE4E4E7, upload = 0xFFB4B4BB,
        downloadTile = 0xFF1C1C1E, uploadTile = 0xFF232326,
        connect = 0xFF3F3F46, connected = 0xFF71717A, tapHint = 0xFFC7C7CC,
        serverCard = 0xFF1A1A1C, serverActive = 0xFF3F3F46,
        configCard = 0xFF1D1D20, configCardText = 0xFFF2F2F7,
    ),
    ThemeProfile(
        id = "neonlime", name = "Neon Lime", accentIndex = 3, amoled = true,
        download = 0xFFD4FF3F, upload = 0xFF9EE84A,
        downloadTile = 0xFF0A0F00, uploadTile = 0xFF0C1200,
        connect = 0xFFC7F24E, connected = 0xFF4A7C0F, tapHint = 0xFFDFFF9E,
        serverCard = 0xFF080B00, serverActive = 0xFFC7F24E,
        configCard = 0xFF0B1000, configCardText = 0xFFEBFFC2,
    ),
    ThemeProfile(
        id = "tokyo", name = "Tokyo", accentIndex = 1, amoled = true,
        download = 0xFF7AA2F7, upload = 0xFFBB9AF7,
        downloadTile = 0xFF0B0D17, uploadTile = 0xFF11101F,
        connect = 0xFF7AA2F7, connected = 0xFFBB9AF7, tapHint = 0xFF9AA5CE,
        serverCard = 0xFF090B14, serverActive = 0xFF7AA2F7,
        configCard = 0xFF0C0F1A, configCardText = 0xFFC0CAF5,
    ),
    ThemeProfile(
        id = "solar", name = "Solar", accentIndex = 4,
        download = 0xFFFFD54F, upload = 0xFFFFB300,
        downloadTile = 0xFF3A2E05, uploadTile = 0xFF33280A,
        connect = 0xFFFFC107, connected = 0xFF8D6E00, tapHint = 0xFFFFE9A8,
        serverCard = 0xFF2E2506, serverActive = 0xFFFFC107,
        configCard = 0xFF352A07, configCardText = 0xFFFFF6DA,
    ),
    ThemeProfile(
        id = "moss", name = "Moss", accentIndex = 3,
        download = 0xFF6EE7A8, upload = 0xFF34D399,
        downloadTile = 0xFF07231A, uploadTile = 0xFF0A2A20,
        connect = 0xFF10B981, connected = 0xFF065F46, tapHint = 0xFFA7F3D0,
        serverCard = 0xFF061D16, serverActive = 0xFF10B981,
        configCard = 0xFF08251C, configCardText = 0xFFE3FFF4,
    ),
    ThemeProfile(
        id = "clay", name = "Clay", accentIndex = 6,
        download = 0xFFE0A08A, upload = 0xFFD97757,
        downloadTile = 0xFF33201A, uploadTile = 0xFF3A231B,
        connect = 0xFFD97757, connected = 0xFF8C4A34, tapHint = 0xFFF0C8B6,
        serverCard = 0xFF2B1B15, serverActive = 0xFFD97757,
        configCard = 0xFF32201A, configCardText = 0xFFFFEDE3,
    ),
)

fun themeProfileOf(id: String?): ThemeProfile? =
    id?.let { wanted -> ThemeProfiles.firstOrNull { it.id == wanted } }
