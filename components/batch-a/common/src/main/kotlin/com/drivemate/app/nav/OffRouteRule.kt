package com.drivemate.app.nav

import kotlin.math.max

/**
 * When the app decides you've left the route (used while driving, and by the tests that drive
 * every test route with bad GPS and with wrong turns, so both check the same rule).
 */
object OffRouteRule {
    /**
     * Clearly away from the route: far off it, and not still heading the route's way. Going the
     * route's way but well off it is a parallel road: in towns streets run 60–100 m apart, so
     * there 75 m is enough; on fast roads (over ~45 mph) 120 m, as carriageways can be far apart.
     */
    fun clearlyOff(offRoute: Double, accuracy: Double, headingAgrees: Boolean, nearEnd: Boolean, speed: Double = 0.0): Boolean =
        if (nearEnd) offRoute > max(40.0, accuracy) && (!headingAgrees || offRoute > 80)
        else offRoute > max(55.0, accuracy * 1.5) && (!headingAgrees || offRoute > (if (speed > 20) 120.0 else 75.0))

    /** On another road when the map can't say which one: away from the route and going another way. */
    fun turnedOffWithoutMap(offRoute: Double, accuracy: Double, speed: Double, headingAgrees: Boolean): Boolean =
        offRoute > max(20.0, accuracy) && speed > 2 && !headingAgrees && offRoute > 35

    /** How many fixes in a row it takes before rerouting. */
    fun fixesNeeded(wrongTurn: Boolean, turnedOff: Boolean, nearEnd: Boolean): Int = when {
        wrongTurn -> 1
        turnedOff -> 2
        nearEnd -> 3
        else -> 4
    }

    /**
     * [fixes] off-route fixes must also span about the time they take at one fix a second
     * ([seconds] since the first), so twice-a-second fixes never halve the time before rerouting.
     */
    fun lastedLongEnough(fixes: Int, seconds: Double): Boolean = fixes <= 1 || seconds >= (fixes - 1) * 0.9

    /**
     * A wrong turn seen by the gyroscope: moving, well off the route, and facing more than 55°
     * away from the route both where the car was matched and over the next stretch of it.
     * [routeBearings] are the route's directions there (at the match and just ahead). At a corner
     * a fix that lags or drifts is matched just before the bend while the car already faces the
     * new road; checking only the stretch before the corner made that look like a wrong turn
     * (an instant "Rerouting" while still on the route).
     */
    fun wrongTurn(speed: Double, onRoundabout: Boolean, offRoute: Double, accuracy: Double, sensedHeading: Double?, routeBearings: List<Double>): Boolean =
        speed > 2 && !onRoundabout && offRoute > max(15.0, accuracy * 0.8) && sensedHeading != null &&
            routeBearings.isNotEmpty() && routeBearings.all { Geo.angleDiff(sensedHeading, it) > 55 }

    /** Heading agrees with the route when within 35° of its direction (moving, with a heading). */
    fun headingAgrees(heading: Double?, speed: Double, routeBearing: Double): Boolean =
        heading != null && speed > 3 && Geo.angleDiff(heading, routeBearing) < 35
}
