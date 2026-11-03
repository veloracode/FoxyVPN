package com.vauth.foxyvpn.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith

/**
 * Screen-transition transforms — Phase B1 of the Zed overhaul.
 *
 * [transitionTransform] is the fork's pre-overhaul helper (relocated
 * from the deleted AppearanceSettings.kt) used by the Interface
 * page's live preview. [navEnter]/[navExit] implement the
 * DESIGN-SYSTEM §8.2 numbers for the NavHost: Fade 260/180, Slide
 * ⅓-width 320 + fade, Push full-width 340, Depth scale 0.88↔1.12,
 * Elastic spring, Curtain half-height 360, Flip fade+scale (the
 * source's 3D rotationY cannot be expressed as a NavHost transition;
 * the preview shows the true flip). Reduce-motion collapses every
 * style to a 120/90 fade.
 */
fun transitionTransform(
    style: ScreenTransition,
    reduceMotion: Boolean,
    forward: Boolean = true,
    rtl: Boolean = false,
): ContentTransform {
    if (reduceMotion) {
        return fadeIn(tween(150)) togetherWith fadeOut(tween(150))
    }
    val dir = when {
        forward && !rtl -> 1
        forward && rtl -> -1
        !forward && !rtl -> -1
        else -> 1
    }
    return when (style) {
        ScreenTransition.Fade -> fadeIn(tween(300)) togetherWith fadeOut(tween(300))
        ScreenTransition.Slide -> slideInHorizontally(tween(320)) { dir * it / 3 } togetherWith
            slideOutHorizontally(tween(320)) { -dir * it / 3 }
        ScreenTransition.Push -> (fadeIn(tween(280)) + slideInHorizontally(tween(320)) { dir * it }) togetherWith
            (fadeOut(tween(280)) + slideOutHorizontally(tween(320)) { -dir * it / 4 })
        ScreenTransition.Depth -> {
            if (forward) {
                (fadeIn(tween(320)) + scaleIn(tween(320), initialScale = 1.08f)) togetherWith
                    (fadeOut(tween(320)) + scaleOut(tween(320), targetScale = 0.92f))
            } else {
                (fadeIn(tween(320)) + scaleIn(tween(320), initialScale = 0.92f)) togetherWith
                    (fadeOut(tween(320)) + scaleOut(tween(320), targetScale = 1.08f))
            }
        }
        ScreenTransition.Elastic -> (fadeIn(tween(250)) + slideInHorizontally(
            spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow),
        ) { dir * it }) togetherWith fadeOut(tween(200))
        ScreenTransition.Curtain -> {
            val enterY = if (forward) -1 else 1
            (fadeIn(tween(300)) +
                slideInVertically(tween(380)) { enterY * it / 2 }) togetherWith
                fadeOut(tween(280))
        }
        ScreenTransition.Flip -> (fadeIn(tween(200)) + scaleIn(tween(380), initialScale = 0.85f)) togetherWith
            (fadeOut(tween(200)) + scaleOut(tween(380), targetScale = 0.85f))
    }
}

private fun dirSign(forward: Boolean, rtl: Boolean): Int = when {
    forward && !rtl -> 1
    forward && rtl -> -1
    !forward && !rtl -> -1
    else -> 1
}

fun navEnter(style: ScreenTransition, reduceMotion: Boolean, forward: Boolean, rtl: Boolean): EnterTransition {
    if (reduceMotion) return fadeIn(tween(120))
    val dir = dirSign(forward, rtl)
    return when (style) {
        ScreenTransition.Fade -> fadeIn(tween(260))
        ScreenTransition.Slide -> slideInHorizontally(tween(320)) { dir * it / 3 } + fadeIn(tween(220))
        ScreenTransition.Push -> slideInHorizontally(tween(340)) { dir * it } + fadeIn(tween(120))
        ScreenTransition.Depth ->
            if (forward) {
                scaleIn(tween(300), initialScale = 0.88f) + fadeIn(tween(220))
            } else {
                scaleIn(tween(300), initialScale = 1.12f) + fadeIn(tween(220))
            }
        ScreenTransition.Elastic ->
            slideInVertically(
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            ) { it / 8 } + scaleIn(
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                initialScale = 0.9f,
            ) + fadeIn(tween(180))
        ScreenTransition.Curtain -> slideInVertically(tween(360)) { it / 2 } + fadeIn(tween(200))
        ScreenTransition.Flip -> fadeIn(tween(220, delayMillis = 90)) + scaleIn(tween(360), initialScale = 0.92f)
    }
}

fun navExit(style: ScreenTransition, reduceMotion: Boolean, forward: Boolean, rtl: Boolean): ExitTransition {
    if (reduceMotion) return fadeOut(tween(90))
    val dir = dirSign(forward, rtl)
    return when (style) {
        ScreenTransition.Fade -> fadeOut(tween(180))
        ScreenTransition.Slide -> slideOutHorizontally(tween(320)) { -dir * it / 3 } + fadeOut(tween(180))
        ScreenTransition.Push -> slideOutHorizontally(tween(340)) { -dir * it / 3 } + fadeOut(tween(340))
        ScreenTransition.Depth ->
            if (forward) {
                scaleOut(tween(300), targetScale = 1.12f) + fadeOut(tween(180))
            } else {
                scaleOut(tween(300), targetScale = 0.88f) + fadeOut(tween(180))
            }
        ScreenTransition.Elastic -> scaleOut(tween(220), targetScale = 0.96f) + fadeOut(tween(160))
        ScreenTransition.Curtain -> scaleOut(tween(360), targetScale = 0.94f) + fadeOut(tween(260))
        ScreenTransition.Flip -> fadeOut(tween(160)) + scaleOut(tween(360), targetScale = 0.92f)
    }
}
