package com.drivemate.app.nav

/** Stop completion uses measured on-route progress, never the animated lead-ahead position. */
object StopCompletionPolicy {
    fun passed(stopStarts: List<Double>, measuredAlong: Double, measuredOffRoute: Double, estimating: Boolean): Int {
        if (estimating || !measuredAlong.isFinite() || measuredAlong < 0.0 ||
            !measuredOffRoute.isFinite() || measuredOffRoute < 0.0 || measuredOffRoute >= 25.0) return 0
        // Only a consecutive prefix can be removed; a later stop cannot complete an earlier one.
        return stopStarts.takeWhile { it.isFinite() && it >= 0.0 && it < measuredAlong - 25.0 }.size
    }
}
