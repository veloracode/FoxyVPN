package com.vauth.foxyvpn.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.vauth.foxyvpn.R
import com.vauth.foxyvpn.data.SettingsStore

/**
 * Typography — Phase B1 of the Zed overhaul.
 *
 * Bundled font families (all SIL OFL 1.1, see res/font/FONT-LICENSES.txt)
 * and the two type scales from DESIGN-SYSTEM §4: the Latin scale
 * (Inter Display for display/headline, Inter for the rest) and the
 * Persian scale (Estedad). Selection rule: an explicit [UiFont] wins;
 * otherwise Persian -> Estedad scale; Bento/Pixel design + non-Persian
 * -> Google Sans Flex on the Latin scale; else Inter Display + Inter.
 */

@Composable
fun interFamily() = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

@Composable
fun interDisplayFamily() = FontFamily(
    Font(R.font.interdisplay_semibold, FontWeight.SemiBold),
    Font(R.font.interdisplay_bold, FontWeight.Bold),
)

@Composable
fun googleSansFamily() = FontFamily(
    Font(R.font.googlesans_regular, FontWeight.Normal),
    Font(R.font.googlesans_medium, FontWeight.Medium),
    Font(R.font.googlesans_semibold, FontWeight.SemiBold),
    Font(R.font.googlesans_bold, FontWeight.Bold),
)

@Composable
fun estedadFamily() = FontFamily(
    Font(R.font.estedad_regular, FontWeight.Normal),
    Font(R.font.estedad_medium, FontWeight.Medium),
    Font(R.font.estedad_semibold, FontWeight.SemiBold),
    Font(R.font.estedad_bold, FontWeight.Bold),
)

@Composable
fun vazirmatnFamily() = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

@Composable
fun nunitoFamily() = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_medium, FontWeight.Medium),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
)

@Composable
fun fontFamilyOf(font: UiFont): FontFamily? = when (font) {
    UiFont.Auto -> null
    UiFont.GoogleSans -> googleSansFamily()
    UiFont.Inter -> interFamily()
    UiFont.Vazirmatn -> vazirmatnFamily()
    UiFont.Estedad -> estedadFamily()
    UiFont.Nunito -> nunitoFamily()
    UiFont.System -> FontFamily.Default
}

private val lineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun style(
    family: FontFamily,
    weight: FontWeight,
    size: TextUnit,
    lineHeight: TextUnit,
    tracking: TextUnit = 0.sp,
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size,
    lineHeight = lineHeight,
    letterSpacing = tracking,
    lineHeightStyle = lineHeightStyle,
)

private fun TextStyle.emphasized(): TextStyle {
    val heavier = when (fontWeight) {
        FontWeight.Normal -> FontWeight.Medium
        FontWeight.Medium -> FontWeight.SemiBold
        FontWeight.SemiBold -> FontWeight.Bold
        else -> FontWeight.Bold
    }
    return copy(fontWeight = heavier, letterSpacing = emphasizedTracking(letterSpacing))
}

private fun expressiveTypography(
    displayLarge: TextStyle,
    displayMedium: TextStyle,
    displaySmall: TextStyle,
    headlineLarge: TextStyle,
    headlineMedium: TextStyle,
    headlineSmall: TextStyle,
    titleLarge: TextStyle,
    titleMedium: TextStyle,
    titleSmall: TextStyle,
    bodyLarge: TextStyle,
    bodyMedium: TextStyle,
    bodySmall: TextStyle,
    labelLarge: TextStyle,
    labelMedium: TextStyle,
    labelSmall: TextStyle,
) = Typography(
    displayLarge = displayLarge,
    displayMedium = displayMedium,
    displaySmall = displaySmall,
    headlineLarge = headlineLarge,
    headlineMedium = headlineMedium,
    headlineSmall = headlineSmall,
    titleLarge = titleLarge,
    titleMedium = titleMedium,
    titleSmall = titleSmall,
    bodyLarge = bodyLarge,
    bodyMedium = bodyMedium,
    bodySmall = bodySmall,
    labelLarge = labelLarge,
    labelMedium = labelMedium,
    labelSmall = labelSmall,
    displayLargeEmphasized = displayLarge.emphasized(),
    displayMediumEmphasized = displayMedium.emphasized(),
    displaySmallEmphasized = displaySmall.emphasized(),
    headlineLargeEmphasized = headlineLarge.emphasized(),
    headlineMediumEmphasized = headlineMedium.emphasized(),
    headlineSmallEmphasized = headlineSmall.emphasized(),
    titleLargeEmphasized = titleLarge.emphasized(),
    titleMediumEmphasized = titleMedium.emphasized(),
    titleSmallEmphasized = titleSmall.emphasized(),
    bodyLargeEmphasized = bodyLarge.emphasized(),
    bodyMediumEmphasized = bodyMedium.emphasized(),
    bodySmallEmphasized = bodySmall.emphasized(),
    labelLargeEmphasized = labelLarge.emphasized(),
    labelMediumEmphasized = labelMedium.emphasized(),
    labelSmallEmphasized = labelSmall.emphasized(),
)

@Composable
private fun latinTypography(
    display: FontFamily = interDisplayFamily(),
    body: FontFamily = interFamily(),
): Typography = expressiveTypography(
    displayLarge = style(display, FontWeight.Bold, 57.sp, 64.sp, (-0.5).sp),
    displayMedium = style(display, FontWeight.Bold, 45.sp, 52.sp, (-0.25).sp),
    displaySmall = style(display, FontWeight.SemiBold, 36.sp, 44.sp),
    headlineLarge = style(display, FontWeight.SemiBold, 32.sp, 40.sp),
    headlineMedium = style(display, FontWeight.SemiBold, 28.sp, 36.sp),
    headlineSmall = style(display, FontWeight.SemiBold, 24.sp, 32.sp),
    titleLarge = style(body, FontWeight.SemiBold, 22.sp, 28.sp),
    titleMedium = style(body, FontWeight.SemiBold, 16.sp, 24.sp, 0.1.sp),
    titleSmall = style(body, FontWeight.Medium, 14.sp, 20.sp, 0.1.sp),
    bodyLarge = style(body, FontWeight.Normal, 16.sp, 24.sp, 0.5.sp),
    bodyMedium = style(body, FontWeight.Normal, 14.sp, 20.sp, 0.25.sp),
    bodySmall = style(body, FontWeight.Normal, 12.sp, 16.sp, 0.4.sp),
    labelLarge = style(body, FontWeight.SemiBold, 14.sp, 20.sp, 0.1.sp),
    labelMedium = style(body, FontWeight.Medium, 12.sp, 16.sp, 0.5.sp),
    labelSmall = style(body, FontWeight.Medium, 11.sp, 16.sp, 0.5.sp),
)

@Composable
private fun persianTypography(family: FontFamily = estedadFamily()): Typography =
    expressiveTypography(
        displayLarge = style(family, FontWeight.Bold, 54.sp, 68.sp),
        displayMedium = style(family, FontWeight.Bold, 43.sp, 56.sp),
        displaySmall = style(family, FontWeight.SemiBold, 35.sp, 46.sp),
        headlineLarge = style(family, FontWeight.SemiBold, 31.sp, 42.sp),
        headlineMedium = style(family, FontWeight.SemiBold, 27.sp, 38.sp),
        headlineSmall = style(family, FontWeight.SemiBold, 23.sp, 34.sp),
        titleLarge = style(family, FontWeight.SemiBold, 21.sp, 30.sp),
        titleMedium = style(family, FontWeight.SemiBold, 16.sp, 26.sp),
        titleSmall = style(family, FontWeight.Medium, 14.sp, 22.sp),
        bodyLarge = style(family, FontWeight.Normal, 16.sp, 28.sp),
        bodyMedium = style(family, FontWeight.Normal, 14.sp, 24.sp),
        bodySmall = style(family, FontWeight.Normal, 12.sp, 20.sp),
        labelLarge = style(family, FontWeight.SemiBold, 14.sp, 22.sp),
        labelMedium = style(family, FontWeight.Medium, 12.sp, 18.sp),
        labelSmall = style(family, FontWeight.Medium, 11.sp, 18.sp),
    )

/**
 * The theme's typography for the current language/design/font choice.
 * [languageTag] is the resolved UI language ("fa" for Persian).
 */
@Composable
fun typographyFor(
    languageTag: String?,
    design: SettingsStore.AppDesign,
    font: UiFont,
): Typography {
    fontFamilyOf(font)?.let { family ->
        return if (languageTag == "fa") persianTypography(family) else latinTypography(family, family)
    }
    return when {
        languageTag == "fa" -> persianTypography()
        design == SettingsStore.AppDesign.PIXEL || design == SettingsStore.AppDesign.BENTO ->
            googleSansFamily().let { latinTypography(it, it) }
        else -> latinTypography()
    }
}
