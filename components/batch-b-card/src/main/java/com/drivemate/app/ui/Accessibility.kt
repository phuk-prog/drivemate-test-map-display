package com.drivemate.app.ui

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Accessibility helpers that need no Android: colour contrast (WCAG 2.x) and how far the phone's
 * "font size" setting may grow the essential driving text before it would no longer fit.
 */
internal object Accessibility {
    /** WCAG minimum for normal-size text. */
    const val MIN_TEXT_CONTRAST = 4.5

    /** Relative luminance of an 0xAARRGGBB colour (alpha ignored: text is drawn opaque). */
    fun luminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF).toDouble() / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    /** Contrast ratio between two colours, 1.0 (none) to 21.0 (black on white). */
    fun contrast(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /**
     * The sp value to pass so the text grows with the phone's font size but never beyond
     * [maxShownSp] on screen (and never below [baseSp]). Larger text is honoured up to the point
     * where the banner, speed and arrival time would clip. Compose multiplies the returned sp by
     * [fontScale] again, so it is divided here. (Android 14's non-linear scaling only makes large
     * text grow less, so the cap still holds.)
     */
    fun cappedSp(baseSp: Float, fontScale: Float, maxShownSp: Float): Float {
        val scale = if (fontScale.isFinite() && fontScale > 0f) fontScale else 1f
        val shown = (baseSp * scale).coerceAtMost(max(baseSp, maxShownSp))
        return shown / scale
    }

    /** What actually appears on screen, in sp, for [cappedSp]. */
    fun shownSp(baseSp: Float, fontScale: Float, maxShownSp: Float): Float =
        cappedSp(baseSp, fontScale, maxShownSp) * (if (fontScale.isFinite() && fontScale > 0f) fontScale else 1f)

    /** Generous width estimate of one line of bold text at [shownSp] (0.62 em per character). */
    fun estimatedTextWidthDp(text: String, shownSp: Float): Float = text.length * shownSp * 0.62f
}

/** Caps for the essential driving text (on-screen sp, after the phone's font size is applied). */
internal object DrivingTextCaps {
    const val DISTANCE = 48f // "Now" / "400 yd" / "0.8 mi" in the turn banner: one line
    const val ACTION = 30f // "Turn left": may wrap
    const val ROAD = 26f // road name: two lines at most
    const val THEN = 24f
    const val SPEED = 34f // inside the 72 dp speed circle
    const val SPEED_UNIT = 14f
    const val ETA = 44f
    const val ETA_DETAIL = 22f
}
