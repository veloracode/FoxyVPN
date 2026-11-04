package com.vauth.foxyvpn.ui.theme

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression gate for the v1.1 "words merged into each other" verdict
 * (Persian settings pages): the emphasized variants used by every
 * settings row must never carry negative letter spacing, and the
 * Persian scale (base tracking 0 everywhere) must pass through
 * untouched — Arabic-script glyphs join, so any non-zero tracking
 * breaks the shaping and pulls words together.
 */
class TypographyTrackingTest {

    @Test
    fun zeroTrackingStaysZero() {
        // Every Persian-scale style ships with 0 tracking.
        assertEquals(0.sp, emphasizedTracking(0.sp))
    }

    @Test
    fun unspecifiedStaysUnspecified() {
        assertEquals(TextUnit.Unspecified, emphasizedTracking(TextUnit.Unspecified))
    }

    @Test
    fun positiveTrackingTightensButNeverBelowZero() {
        assertEquals(0.3f, emphasizedTracking(0.5.sp).value, 0.001f)
        // Latin title styles carry 0.1sp; tightening must floor at 0.
        assertEquals(0f, emphasizedTracking(0.1.sp).value, 0.001f)
        assertEquals(0f, emphasizedTracking(0.2.sp).value, 0.001f)
    }

    @Test
    fun noInputProducesNegativeTracking() {
        for (base in listOf(0.sp, 0.05.sp, 0.1.sp, 0.25.sp, 0.4.sp, 0.5.sp, 0.9.sp)) {
            assertTrue(
                "emphasizedTracking($base) went negative",
                emphasizedTracking(base).value >= 0f,
            )
        }
    }
}
