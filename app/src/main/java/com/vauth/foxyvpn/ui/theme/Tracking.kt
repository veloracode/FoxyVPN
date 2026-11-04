package com.vauth.foxyvpn.ui.theme

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * Letter-spacing arithmetic for the emphasized type variants.
 *
 * Arabic-script text (Persian) must keep letterSpacing at exactly 0:
 * cursive glyphs join, and ANY non-zero tracking — negative included —
 * makes the shaping engine pull glyphs into each other, which reads
 * on device as words merged/overlapping (user verdict on v1.1,
 * 2026-11-03). The pre-rescue [Type] emphasized() subtracted 0.2sp
 * unconditionally, so every Persian style (base tracking 0) shipped
 * at -0.2sp.
 *
 * Rule: tracking is only ever tightened when the base style already
 * carries positive tracking (the Latin display/body styles), and the
 * result is floored at 0; a zero or unspecified base passes through
 * untouched. The result can therefore never be negative.
 */
internal fun emphasizedTracking(base: TextUnit): TextUnit =
    if (base.isSpecified && base.value > 0f) {
        (base.value - 0.2f).coerceAtLeast(0f).sp
    } else {
        base
    }
