package com.drivemate.app.nav

import com.drivemate.app.data.RouteStep

/** Arrival side is explicit provider evidence; composite or malformed modifiers are unknown. */
internal fun explicitArrivalSide(modifier: String?): String? =
    modifier?.takeIf { it == "left" || it == "right" }

/** When the trip counts as arrived, and which side of the road the destination is on. */
object ArrivalPolicy {
    /**
     * Arrived only at the end of the route (measured position, on the route) or at the
     * destination point itself near the end of the route. Never from an estimated position.
     */
    fun arrived(measuredRemaining: Double, measuredOffRoute: Double, metresFromEndPoint: Double, estimating: Boolean): Boolean {
        if (estimating || listOf(measuredRemaining, measuredOffRoute, metresFromEndPoint).any { !it.isFinite() || it < 0.0 }) return false
        val atRouteEnd = measuredRemaining < 30 && measuredOffRoute < 25
        // Near the destination point AND near the end of the route (not passing it on the other
        // side of a dual carriageway before the turn-round).
        val atDestination = metresFromEndPoint < 20 && measuredRemaining < 150 && measuredOffRoute < 25
        return atRouteEnd || atDestination
    }

    /**
     * The side from the route's own arrival step ("arrive", right/left), never from whichever
     * move happens to be shown: near a corner the last turn's "left" is not the destination's side.
     * Null when the route data doesn't say.
     */
    fun side(steps: List<RouteStep>): String? {
        val arrive = steps.lastOrNull()?.takeIf { it.type == "arrive" } ?: return null
        return explicitArrivalSide(arrive.modifier)
    }
}
