package com.drivemate.app.nav

/** Visual uncertainty is independent of the one-minute spoken GPS-loss alert. */
class PositionUncertainty {
    private var uncertainSince: Long? = null
    private var lastNow: Long? = null

    fun update(nowMs: Long, reliable: Boolean): Boolean {
        if (nowMs < 0) { reset(); return false }
        if (lastNow?.let { nowMs < it } == true) uncertainSince = null
        lastNow = nowMs
        if (reliable) { uncertainSince = null; return false }
        val since = uncertainSince ?: nowMs.also { uncertainSince = it }
        return nowMs - since >= DELAY_MS
    }

    fun reset() { uncertainSince = null; lastNow = null }

    companion object {
        const val DELAY_MS = 3_000L
        const val MAX_LANE_FIX_AGE_MS = 2_500L
        /** Sensors support heading; they never make an untrusted GPS fix reliable. */
        fun reliable(authoritative: Boolean, measured: Boolean, accuracyM: Double?, ageMs: Long?): Boolean =
            authoritative && measured && accuracyM != null && accuracyM.isFinite() && accuracyM in 0.1..25.0 &&
                ageMs != null && ageMs in 0..MAX_LANE_FIX_AGE_MS
    }
}
