package com.drivemate.app.nav

import kotlin.math.max
import kotlin.math.min

/**
 * MG09: one timestamped route progress per GPS fix. The map camera, the junction scene, the
 * overview, the instruction card and the voice all read this same snapshot (and ask it where the
 * car is "now" with [distanceAt]), so they can never disagree about how far the junction is.
 *
 * Times are in one clock, elapsed-realtime milliseconds. [receivedMs] is when the app took the fix;
 * [fixAgeMs] is how old the fix already was then (0 when the phone does not say).
 */
data class SharedProgress(
    val receivedMs: Long,
    val fixAgeMs: Long,
    val along: Double,
    val distanceToNext: Double,
    val nextStep: Int,
    val speedMps: Double,
    /** GPS accuracy in metres, or NaN when unknown. */
    val accuracyM: Double,
    /** Metres from the route line. */
    val offRouteM: Double,
    val onRoundabout: Boolean,
) {
    /** Milliseconds since the fix was taken, as of [nowMs]. */
    fun ageMs(nowMs: Long): Long = max(0L, nowMs - receivedMs) + fixAgeMs

    private fun coveredAt(nowMs: Long) = speedMps.coerceAtLeast(0.0) * min(ageMs(nowMs), MAX_EXTRAPOLATE_MS) / 1000.0

    /**
     * Metres to the next manoeuvre as of [nowMs]: the fix's distance less what the car has most
     * likely covered since, at the speed it was doing. Extrapolation stops after [MAX_EXTRAPOLATE_MS]
     * so a stale fix never keeps counting down, and it never passes the manoeuvre. Because it
     * is worked out from the fix time, one fix a second and five a second give the same answer.
     */
    fun distanceAt(nowMs: Long): Double =
        if (onRoundabout) distanceToNext else max(0.0, distanceToNext - coveredAt(nowMs))

    /** Metres along the route as of [nowMs] (same rules as [distanceAt]). */
    fun alongAt(nowMs: Long): Double = along + coveredAt(nowMs)

    companion object {
        /** About two seconds is a fair guess at the car's progress; beyond it we just hold. */
        const val MAX_EXTRAPOLATE_MS = 2_500L
        /** Fixes older than this are stale (tunnel, lost signal). */
        const val STALE_MS = 5_000L
        /** A reported fix age beyond this is a replayed or odd clock, so it is taken as unknown. */
        const val MAX_PLAUSIBLE_AGE_MS = 60_000L

        fun of(p: Progress, receivedMs: Long, fixAgeMs: Long, speedMps: Double, accuracyM: Double): SharedProgress =
            SharedProgress(receivedMs, if (fixAgeMs in 0..MAX_PLAUSIBLE_AGE_MS) fixAgeMs else 0L,
                p.along, p.distanceToNext, p.nextStep, if (speedMps.isFinite()) speedMps else 0.0,
                accuracyM, p.offRoute, p.onRoundabout)
    }
}

/** How much guidance detail the current position can honestly support (MG13), best first. */
enum class GuidanceLevel {
    /** Junction picture with the lanes to use. */
    LANE_DETAIL,
    /** Junction picture with directions only; no lane is claimed. */
    DIRECTION_ONLY,
    /** The ordinary map with a plain turn-arrow card: position is too uncertain for more. */
    PLAIN_ARROW,
    /** No junction coming that needs a picture: the ordinary map. */
    ORDINARY_MAP;

    val showsScene get() = this == LANE_DETAIL || this == DIRECTION_ONLY
}

/**
 * MG13 confidence-based fallback. Falls to simpler guidance at once when the position gets worse,
 * but climbs back only after the position has been better for [RECOVER_MS], so it never flickers.
 */
class GuidanceFallback {
    private enum class Confidence { GOOD, FAIR, LOW }
    private var confidence = Confidence.GOOD
    private var betterSinceMs: Long? = null

    var level: GuidanceLevel = GuidanceLevel.ORDINARY_MAP
        private set

    /** Lanes may be shown or spoken only while the position is good. */
    val lanesAllowed get() = confidence == Confidence.GOOD

    /**
     * [estimated]: the position is the app's own estimate (no GPS, e.g. in a tunnel), however
     * precise it looks: never better than directions only, so no lane is claimed from it (8.1).
     */
    fun update(nowMs: Long, p: SharedProgress, hasPlan: Boolean, hasLanes: Boolean, estimated: Boolean = false): GuidanceLevel {
        val raw = rate(nowMs, p).let { if (estimated && it == Confidence.GOOD) Confidence.FAIR else it }
        if (raw.ordinal >= confidence.ordinal) {
            confidence = raw; betterSinceMs = null
        } else {
            val since = betterSinceMs ?: nowMs.also { betterSinceMs = it }
            if (nowMs - since >= RECOVER_MS) { confidence = raw; betterSinceMs = null }
        }
        level = when {
            confidence == Confidence.LOW -> GuidanceLevel.PLAIN_ARROW
            !hasPlan -> GuidanceLevel.ORDINARY_MAP
            confidence == Confidence.GOOD && hasLanes -> GuidanceLevel.LANE_DETAIL
            else -> GuidanceLevel.DIRECTION_ONLY
        }
        return level
    }

    /** Invalidate cached visual and spoken lane claims even when no new fix arrives. */
    fun invalidate() {
        confidence = Confidence.LOW
        betterSinceMs = null
        level = GuidanceLevel.PLAIN_ARROW
    }

    fun reset() { confidence = Confidence.GOOD; betterSinceMs = null; level = GuidanceLevel.ORDINARY_MAP }

    private fun rate(nowMs: Long, p: SharedProgress): Confidence {
        val age = p.ageMs(nowMs)
        if (!p.accuracyM.isFinite() || p.accuracyM <= 0.0 || !p.offRouteM.isFinite() || p.offRouteM < 0.0 ||
            p.fixAgeMs < 0 || nowMs < p.receivedMs) return Confidence.LOW
        val accuracy = p.accuracyM
        return when {
            age > SharedProgress.STALE_MS || accuracy > FAIR_ACCURACY_M || p.offRouteM > FAIR_OFF_ROUTE_M -> Confidence.LOW
            age > GOOD_AGE_MS || accuracy > GOOD_ACCURACY_M || p.offRouteM > GOOD_OFF_ROUTE_M -> Confidence.FAIR
            else -> Confidence.GOOD
        }
    }

    companion object {
        const val GOOD_ACCURACY_M = 25.0
        const val FAIR_ACCURACY_M = 50.0
        const val GOOD_OFF_ROUTE_M = 30.0
        const val FAIR_OFF_ROUTE_M = 60.0
        const val GOOD_AGE_MS = 3_000L
        const val RECOVER_MS = 3_000L
    }
}
