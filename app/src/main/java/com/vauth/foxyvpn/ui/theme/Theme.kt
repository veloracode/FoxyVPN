package com.vauth.foxyvpn.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import com.vauth.foxyvpn.data.SettingsStore
import com.vauth.foxyvpn.ui.AppearanceController

/**
 * FoxyVpnTheme — Phase B1 of the Zed overhaul.
 *
 * Takes the whole [AppearanceController] (the fork's state seam) plus the
 * resolved UI language. When the Bento design is active (the only design
 * the preview ships), the Material scheme is the [bentoColorScheme]
 * bridge over [BentoPalettes]; the seed comes from dynamic color's
 * primary when dynamic color is on, else from the accent preset's light
 * value (the reference rule). The seed-scheme path stays as the fallback
 * for the non-Bento designs that arrive in Phase B2.
 *
 * Color changes animate with a MediumLow spring (DESIGN-SYSTEM §8.1);
 * the UI scale multiplies density (font scale is separate, applied by
 * MainActivity), matching the reference Density rule.
 */
@Composable
fun FoxyVpnTheme(
    appearance: AppearanceController,
    languageTag: String?,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (appearance.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> systemDark
    }
    val amoled = appearance.amoledBlack || appearance.themeMode == ThemeMode.AMOLED

    val dynamicScheme: ColorScheme? = remember(appearance.dynamicColor, darkTheme) {
        if (appearance.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            null
        }
    }

    val accentIndex = appearance.accentColorIndex
        .coerceIn(0, AccentPresets.lastIndex)
    val bentoSeed = dynamicScheme?.primary ?: AccentPresets[accentIndex].light
    val bentoColors = remember(
        appearance.bento.palette, darkTheme, bentoSeed, amoled,
    ) {
        BentoPalettes.of(appearance.bento.palette, darkTheme, bentoSeed, amoled)
    }

    val targetScheme: ColorScheme = if (appearance.appDesign == SettingsStore.AppDesign.BENTO) {
        bentoColorScheme(bentoColors)
    } else {
        val base = dynamicScheme ?: seedColorScheme(
            dark = darkTheme,
            seed = accentSeed(accentIndex, darkTheme),
            style = appearance.colorStyle,
            contrast = appearance.colorContrast,
        )
        if (amoled && darkTheme) base.applyAmoled() else base
    }
    val animatedScheme = animateColorScheme(targetScheme, light = !darkTheme)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    val baseDensity = LocalDensity.current
    val motionBudget = rememberMotionBudget(appearance.reduceMotion)
    val latin = languageTag != "fa"
    CompositionLocalProvider(
        LocalBentoStyle provides BentoStyle(bentoColors, appearance.bento, latin),
        LocalAppDesign provides appearance.appDesign,
        LocalMotionBudget provides motionBudget,
        LocalDensity provides Density(
            density = baseDensity.density * appearance.uiScale,
            fontScale = baseDensity.fontScale,
        ),
    ) {
        MaterialExpressiveTheme(
            colorScheme = animatedScheme,
            typography = typographyFor(languageTag, appearance.appDesign, appearance.uiFont),
            shapes = ZedShapes,
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    }
}

/**
 * Animates every scheme role toward [target] with the reference spring
 * (DESIGN-SYSTEM §8.1). Roles the scheme does not expose animate as
 * their structural counterparts.
 */
@Composable
@Composable
private fun animateColorScheme(target: ColorScheme, light: Boolean): ColorScheme {
    @Composable
    fun anim(color: androidx.compose.ui.graphics.Color) =
        animateColorAsState(
            targetValue = color,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "scheme",
        )
    val primary by anim(target.primary)
    val onPrimary by anim(target.onPrimary)
    val primaryContainer by anim(target.primaryContainer)
    val onPrimaryContainer by anim(target.onPrimaryContainer)
    val inversePrimary by anim(target.inversePrimary)
    val secondary by anim(target.secondary)
    val onSecondary by anim(target.onSecondary)
    val secondaryContainer by anim(target.secondaryContainer)
    val onSecondaryContainer by anim(target.onSecondaryContainer)
    val tertiary by anim(target.tertiary)
    val onTertiary by anim(target.onTertiary)
    val tertiaryContainer by anim(target.tertiaryContainer)
    val onTertiaryContainer by anim(target.onTertiaryContainer)
    val background by anim(target.background)
    val onBackground by anim(target.onBackground)
    val surface by anim(target.surface)
    val onSurface by anim(target.onSurface)
    val surfaceVariant by anim(target.surfaceVariant)
    val onSurfaceVariant by anim(target.onSurfaceVariant)
    val surfaceTint by anim(target.surfaceTint)
    val inverseSurface by anim(target.inverseSurface)
    val inverseOnSurface by anim(target.inverseOnSurface)
    val error by anim(target.error)
    val onError by anim(target.onError)
    val errorContainer by anim(target.errorContainer)
    val onErrorContainer by anim(target.onErrorContainer)
    val outline by anim(target.outline)
    val outlineVariant by anim(target.outlineVariant)
    val scrim by anim(target.scrim)
    val surfaceBright by anim(target.surfaceBright)
    val surfaceDim by anim(target.surfaceDim)
    val surfaceContainer by anim(target.surfaceContainer)
    val surfaceContainerHigh by anim(target.surfaceContainerHigh)
    val surfaceContainerHighest by anim(target.surfaceContainerHighest)
    val surfaceContainerLow by anim(target.surfaceContainerLow)
    val surfaceContainerLowest by anim(target.surfaceContainerLowest)
    return remember(
        primary, onPrimary, primaryContainer, onPrimaryContainer, inversePrimary,
        secondary, onSecondary, secondaryContainer, onSecondaryContainer,
        tertiary, onTertiary, tertiaryContainer, onTertiaryContainer,
        background, onBackground, surface, onSurface, surfaceVariant, onSurfaceVariant,
        surfaceTint, inverseSurface, inverseOnSurface, error, onError,
        errorContainer, onErrorContainer, outline, outlineVariant, scrim,
        surfaceBright, surfaceDim, surfaceContainer, surfaceContainerHigh,
        surfaceContainerHighest, surfaceContainerLow, surfaceContainerLowest,
    ) {
        if (light) {
            lightColorScheme(
                primary = primary, onPrimary = onPrimary,
                primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
                inversePrimary = inversePrimary,
                secondary = secondary, onSecondary = onSecondary,
                secondaryContainer = secondaryContainer,
                onSecondaryContainer = onSecondaryContainer,
                tertiary = tertiary, onTertiary = onTertiary,
                tertiaryContainer = tertiaryContainer,
                onTertiaryContainer = onTertiaryContainer,
                background = background, onBackground = onBackground,
                surface = surface, onSurface = onSurface,
                surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
                surfaceTint = surfaceTint,
                inverseSurface = inverseSurface, inverseOnSurface = inverseOnSurface,
                error = error, onError = onError,
                errorContainer = errorContainer, onErrorContainer = onErrorContainer,
                outline = outline, outlineVariant = outlineVariant, scrim = scrim,
                surfaceBright = surfaceBright, surfaceDim = surfaceDim,
                surfaceContainer = surfaceContainer,
                surfaceContainerHigh = surfaceContainerHigh,
                surfaceContainerHighest = surfaceContainerHighest,
                surfaceContainerLow = surfaceContainerLow,
                surfaceContainerLowest = surfaceContainerLowest,
            )
        } else {
            darkColorScheme(
                primary = primary, onPrimary = onPrimary,
                primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
                inversePrimary = inversePrimary,
                secondary = secondary, onSecondary = onSecondary,
                secondaryContainer = secondaryContainer,
                onSecondaryContainer = onSecondaryContainer,
                tertiary = tertiary, onTertiary = onTertiary,
                tertiaryContainer = tertiaryContainer,
                onTertiaryContainer = onTertiaryContainer,
                background = background, onBackground = onBackground,
                surface = surface, onSurface = onSurface,
                surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
                surfaceTint = surfaceTint,
                inverseSurface = inverseSurface, inverseOnSurface = inverseOnSurface,
                error = error, onError = onError,
                errorContainer = errorContainer, onErrorContainer = onErrorContainer,
                outline = outline, outlineVariant = outlineVariant, scrim = scrim,
                surfaceBright = surfaceBright, surfaceDim = surfaceDim,
                surfaceContainer = surfaceContainer,
                surfaceContainerHigh = surfaceContainerHigh,
                surfaceContainerHighest = surfaceContainerHighest,
                surfaceContainerLow = surfaceContainerLow,
                surfaceContainerLowest = surfaceContainerLowest,
            )
        }
    }
}
