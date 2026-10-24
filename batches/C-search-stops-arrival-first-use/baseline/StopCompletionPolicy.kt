package com.drivemate.app.nav

/** Test adapter for the baseline NavigationSession stop-count expression. Baseline session supplies predicted progress here; this adapter only exposes the count expression. */
object StopCompletionPolicy {
    fun passed(stopStarts: List<Double>, measuredAlong: Double, measuredOffRoute: Double, estimating: Boolean): Int {
        return stopStarts.count { it < measuredAlong - 25.0 }
    }
}
