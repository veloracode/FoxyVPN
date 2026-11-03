package com.vauth.foxyvpn.ui.theme

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vauth.foxyvpn.data.SettingsStore

/**
 * Bento theme plumbing — Phase B1 of the Zed overhaul.
 *
 * [BentoStyle] carries the resolved [BentoColors] + [BentoPrefs] through
 * the composition (the same role as the reference design's style local);
 * [bentoColorScheme] bridges Bento colors into the Material 3 scheme so
 * every non-Bento surface (dialogs, menus, the remaining screens) renders
 * in the same palette. The mapping table is DESIGN-SYSTEM §3.3.
 */

@Immutable
class BentoStyle(val colors: BentoColors, val prefs: BentoPrefs, val latin: Boolean)

val LocalBentoStyle = staticCompositionLocalOf {
    BentoStyle(BentoPalettes.BrandDark, BentoPrefs(), latin = true)
}

object Bento {
    val colors: BentoColors
        @Composable @ReadOnlyComposable get() = LocalBentoStyle.current.colors

    val prefs: BentoPrefs
        @Composable @ReadOnlyComposable get() = LocalBentoStyle.current.prefs

    val latin: Boolean
        @Composable @ReadOnlyComposable get() = LocalBentoStyle.current.latin

    val active: Boolean
        @Composable @ReadOnlyComposable get() =
            LocalAppDesign.current == SettingsStore.AppDesign.BENTO
}

/** Which home design is active (provided by FoxyVpnTheme). */
val LocalAppDesign = staticCompositionLocalOf { SettingsStore.AppDesign.BENTO }

fun bentoColorScheme(c: BentoColors): ColorScheme {
    val low = lerp(c.ground, c.surface, 0.5f)
    val highest = lerp(c.surface2, c.line, 0.25f)
    return if (c.dark) {
        darkColorScheme(
            primary = c.lime,
            onPrimary = c.onLime,
            primaryContainer = c.stage,
            onPrimaryContainer = c.onStage,
            inversePrimary = c.stage,
            secondary = c.violet.content,
            onSecondary = c.violet.container,
            secondaryContainer = c.violet.container,
            onSecondaryContainer = c.violet.content,
            tertiary = c.cyan.content,
            onTertiary = c.cyan.container,
            tertiaryContainer = c.cyan.container,
            onTertiaryContainer = c.cyan.content,
            background = c.ground,
            onBackground = c.ink,
            surface = c.ground,
            onSurface = c.ink,
            surfaceVariant = c.surface2,
            onSurfaceVariant = c.muted,
            surfaceTint = c.lime,
            inverseSurface = c.ink,
            inverseOnSurface = c.ground,
            error = c.danger,
            onError = c.onDanger,
            errorContainer = c.coral.container,
            onErrorContainer = c.coral.content,
            outline = c.muted.copy(alpha = 0.7f).compositeOver(c.ground),
            outlineVariant = c.line,
            scrim = Color.Black,
            surfaceBright = c.surface2,
            surfaceDim = c.ground,
            surfaceContainerLowest = c.ground,
            surfaceContainerLow = low,
            surfaceContainer = c.surface,
            surfaceContainerHigh = c.surface,
            surfaceContainerHighest = highest,
        )
    } else {
        lightColorScheme(
            primary = c.stage,
            onPrimary = c.onStage,
            primaryContainer = c.lime,
            onPrimaryContainer = c.onLime,
            inversePrimary = c.lime,
            secondary = c.violet.content,
            onSecondary = c.violet.container,
            secondaryContainer = c.violet.container,
            onSecondaryContainer = c.violet.content,
            tertiary = c.cyan.content,
            onTertiary = c.cyan.container,
            tertiaryContainer = c.cyan.container,
            onTertiaryContainer = c.cyan.content,
            background = c.ground,
            onBackground = c.ink,
            surface = c.ground,
            onSurface = c.ink,
            surfaceVariant = c.surface2,
            onSurfaceVariant = c.muted,
            surfaceTint = c.stage,
            inverseSurface = c.ink,
            inverseOnSurface = c.ground,
            error = c.bad,
            onError = Color.White,
            errorContainer = c.danger,
            onErrorContainer = c.onDanger,
            outline = c.muted.copy(alpha = 0.7f).compositeOver(c.ground),
            outlineVariant = c.line,
            scrim = Color.Black,
            surfaceBright = c.ground,
            surfaceDim = c.surface2,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = low,
            surfaceContainer = c.surface,
            surfaceContainerHigh = c.surface,
            surfaceContainerHighest = c.surface2,
        )
    }
}

private fun Color.compositeOver(background: Color): Color {
    val a = alpha
    return Color(
        red = red * a + background.red * (1 - a),
        green = green * a + background.green * (1 - a),
        blue = blue * a + background.blue * (1 - a),
        alpha = 1f,
    )
}

// ---------------------------------------------------------------------
// Motion budget governor (DESIGN-SYSTEM §8.1 / DESIGN-MAP §7.2 rule 1)
// ---------------------------------------------------------------------

enum class MotionBudget { Full, Throttled, Paused }

val LocalMotionBudget = staticCompositionLocalOf { MotionBudget.Full }

/**
 * Resolves the motion budget from live signals:
 *  - Paused: reduce-motion is on, the system animator scale is 0, or the
 *    lifecycle is not RESUMED (nothing visible should be looping).
 *  - Throttled: battery saver is on, thermal status is moderate or
 *    worse, or the animator scale is between 0 and 1.
 *  - Full otherwise.
 * Ambient loops consult it: Full = run; Throttled = run only while
 * connecting/reconnecting; Paused = off.
 */
@Composable
fun rememberMotionBudget(reduceMotion: Boolean): MotionBudget {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow
        .collectAsStateWithLifecycle()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    return remember(context, reduceMotion, resumed) {
        motionBudgetFor(
            reduceMotion = reduceMotion,
            resumed = resumed,
            animatorScale = readAnimatorScale(context),
            powerSave = readPowerSave(context),
            thermalStatus = readThermalStatus(context),
        )
    }
}

fun motionBudgetFor(
    reduceMotion: Boolean,
    resumed: Boolean,
    animatorScale: Float,
    powerSave: Boolean,
    thermalStatus: Int,
): MotionBudget = when {
    reduceMotion || !resumed || animatorScale == 0f -> MotionBudget.Paused
    powerSave || thermalStatus >= THERMAL_MODERATE || animatorScale < 1f -> MotionBudget.Throttled
    else -> MotionBudget.Full
}

/** PowerManager.THERMAL_STATUS_MODERATE (API 29) without the API dependency. */
private const val THERMAL_MODERATE = 2

private fun readAnimatorScale(context: Context): Float =
    runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
    }.getOrDefault(1f)

private fun readPowerSave(context: Context): Boolean =
    (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
        ?.isPowerSaveMode == true

private fun readThermalStatus(context: Context): Int {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        ?: return 0
    return runCatching { pm.currentThermalStatus }.getOrDefault(0)
}
