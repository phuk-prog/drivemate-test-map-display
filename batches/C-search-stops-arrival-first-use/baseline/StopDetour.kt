package com.drivemate.app.data

import com.drivemate.app.nav.Geo
import com.drivemate.app.nav.LatLon

/**
 * Detour time for places along the route (MX03): how much longer the trip gets if you call there,
 * worked out by road, not by straight-line distance. A services area beside the motorway with no
 * way in from your carriageway gets the real detour (off at the junction and back), not "50 m".
 *
 * For each place: leave the route [LEAD] metres before it (A), drive to the place (P), rejoin
 * [LEAD] metres after it (B). Detour = A→P→B − A→B, worked out on the phone by DriveMate's own route
 * finder (1.14: no route points are sent anywhere; it was a public OSRM table request). Where the
 * phone has no road map for it, detours stay unknown — never guessed.
 */
object StopDetour {
    const val LEAD = 2_000.0
    /** Places needing more than this extra time are dropped as not really "along the route". */
    const val MAX_DETOUR_S = 20 * 60.0

    /** Distance along [line] for each point. */
    fun cumulative(line: List<LatLon>): DoubleArray {
        val cum = DoubleArray(line.size)
        for (i in 1 until line.size) cum[i] = cum[i - 1] + Geo.distance(line[i - 1], line[i])
        return cum
    }

    /** The point [at] metres along [line] (clamped to its ends), and the road's heading there. */
    fun pointAt(line: List<LatLon>, cum: DoubleArray, at: Double): Pair<LatLon, Double> {
        val d = at.coerceIn(0.0, cum.last())
        var i = 0
        while (i < line.size - 2 && cum[i + 1] < d) i++
        val seg = cum[i + 1] - cum[i]
        val t = if (seg <= 0) 0.0 else (d - cum[i]) / seg
        return Geo.interpolate(line[i], line[i + 1], t) to Geo.bearing(line[i], line[i + 1])
    }

    /** Extra seconds to call at the place, never negative; null when any leg is unknown. */
    fun detour(aToP: Double?, pToB: Double?, aToB: Double?): Double? {
        if (aToP == null || pToB == null || aToB == null) return null
        return (aToP + pToB - aToB).coerceAtLeast(0.0)
    }

    /**
     * Smallest detour first (ties: nearer ahead first); places whose detour couldn't be worked out
     * follow in order along the route; places with a detour over [MAX_DETOUR_S] are dropped.
     */
    fun rank(list: List<RoutePlace>): List<RoutePlace> {
        val known = list.filter { it.detourSeconds != null && it.detourSeconds <= MAX_DETOUR_S }
            .sortedWith(compareBy({ Math.round(it.detourSeconds!! / 60.0) }, { it.ahead }))
        val unknown = list.filter { it.detourSeconds == null }.sortedBy { it.ahead }
        return known + unknown
    }

    /** "about 3 min detour", "under 1 min detour" or "detour not checked". */
    fun label(detourSeconds: Double?): String = when {
        detourSeconds == null -> "detour not checked"
        detourSeconds < 60 -> "under 1 min detour"
        else -> "about ${Math.round(detourSeconds / 60.0)} min detour"
    }

    /** What the map data can't tell us about this kind of stop, said plainly. */
    fun unknowns(kind: PlaceKind): String = when (kind) {
        PlaceKind.EV -> "Charger availability and opening times not known"
        PlaceKind.PARKING -> "Opening times and prices may not be known"
        else -> "Opening times not checked"
    }

    /**
     * Adds a road detour estimate to each place and ranks them (see [rank]). [line] is the same
     * route stretch the places were found along. Network failure leaves detours unknown.
     */
    fun withDetours(
        list: List<RoutePlace>, line: List<LatLon>,
        seconds: (List<LatLon>, Float) -> Double? = { points, heading ->
            com.drivemate.app.dmn.Dmn.route(points, heading).firstOrNull()?.duration
        },
    ): List<RoutePlace> {
        if (list.isEmpty() || line.size < 2) return list
        val cum = cumulative(line)
        val detours = list.map { r ->
            val (a, heading) = pointAt(line, cum, r.ahead - LEAD)
            val (b, _) = pointAt(line, cum, r.ahead + LEAD)
            runCatching { detour(seconds(listOf(a, r.place.pos, b), heading.toFloat()), 0.0, seconds(listOf(a, b), heading.toFloat())) }.getOrNull()
        }
        return rank(list.mapIndexed { i, r -> RoutePlace(r.place, r.kind, r.ahead, r.offRoute, r.prices, detours[i]) })
    }
}
