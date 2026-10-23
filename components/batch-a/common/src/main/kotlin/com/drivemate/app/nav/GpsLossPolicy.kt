package com.drivemate.app.nav

/** Driver-requested threshold; brief interruptions remain quiet. Monotonic timestamps only. */
object GpsLossPolicy {
    const val WARNING_DELAY_MS = 60_000L
    fun shouldWarn(lastReliableFixMs: Long, nowMs: Long): Boolean =
        lastReliableFixMs > 0 && nowMs >= lastReliableFixMs && nowMs - lastReliableFixMs >= WARNING_DELAY_MS
}
