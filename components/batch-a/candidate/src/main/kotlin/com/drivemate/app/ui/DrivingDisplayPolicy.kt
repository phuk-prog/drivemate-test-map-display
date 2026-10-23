package com.drivemate.app.ui

/** A stationary fresh measured fix is necessary, but never proof that the vehicle is parked. */
internal fun canRequestParkedConfirmation(speed: Float?, fixAgeMs: Long): Boolean =
    speed != null && speed.isFinite() && speed >= 0f && speed < 0.5f && fixAgeMs in 0..5_000

internal enum class DrivingNotice { REROUTING, GPS_ESTIMATE, ROAD_SIGN, NONE }

/** Keep secondary notices out of the turn approach and the whole roundabout passage. */
internal fun imminentManeuver(arrived: Boolean, distance: Double, onRoundabout: Boolean, speed: Float?): Boolean {
    if (arrived) return false
    if (onRoundabout) return true
    val measured = speed?.takeIf { it.isFinite() && it >= 0f } ?: 0f
    return distance.isFinite() && distance >= 0.0 && distance < maxOf(45.0, measured.toDouble() * 5.0)
}

/** Unclassified supplied signs may be safety messages; only known secondary notices hide. */
internal fun drivingNotice(rerouting: Boolean, sustainedGpsLoss: Boolean, hasSign: Boolean, imminentTurn: Boolean,
                          secondary: Boolean = false): DrivingNotice = when {
    sustainedGpsLoss -> DrivingNotice.GPS_ESTIMATE
    rerouting -> DrivingNotice.REROUTING
    hasSign && (!imminentTurn || !secondary) -> DrivingNotice.ROAD_SIGN
    else -> DrivingNotice.NONE
}

/** Missing, non-finite and stale speed must not be presented as a measured zero. */
internal fun measuredSpeed(speed: Float?, fixAgeMs: Long): Float? =
    speed?.takeIf { it.isFinite() && it >= 0f && fixAgeMs in 0..5_000 }

/**
 * 8.1: what kind of position the app is drawing. An [ESTIMATED] one (no GPS: carried along the
 * route in a tunnel) never supports a measured speed, a speeding warning or lane advice.
 */
enum class PositionKind {
    MEASURED, ESTIMATED;
    companion object {
        /** The session's own estimates carry this provider name. */
        const val ESTIMATE_PROVIDER = "estimate"
        fun of(provider: String?): PositionKind = if (provider == ESTIMATE_PROVIDER) ESTIMATED else MEASURED
    }
}

/** The speed to show: never one the app only estimated (the car's own speed from Android Auto is measured). */
internal fun shownMeasuredSpeed(provider: String?, speed: Float?, fixAgeMs: Long): Float? =
    if (PositionKind.of(provider) == PositionKind.ESTIMATED) null else measuredSpeed(speed, fixAgeMs)

/** The highlighted cue uses only a real, recent movement estimate and a valid route distance. */
internal fun turnBannerNow(arrived: Boolean, stepType: String?, distance: Double, speed: Float?, fixAgeMs: Long): Boolean =
    !arrived && stepType != null && stepType !in setOf("arrive", "stop") &&
        distance.isFinite() && distance >= 0.0 &&
        distance <= maxOf(45.0, (measuredSpeed(speed, fixAgeMs) ?: 0f).toDouble() * 5.0)

/** Presentation only: route manoeuvre evidence can reduce clutter without claiming lane geometry. */
internal fun shouldFocusJunction(driving: Boolean, arrived: Boolean, type: String?, laneCount: Int, distance: Double): Boolean =
    driving && !arrived && distance.isFinite() && distance in 0.0..600.0 &&
        (type in setOf("roundabout", "rotary", "off ramp", "on ramp", "fork", "merge") || laneCount >= 3)
