package com.drivemate.app.data

import com.drivemate.app.nav.LatLon

data class Place(
    val name: String,
    val address: String,
    val pos: LatLon,
    /** Extra words the place can be found by (its type, area…); not shown. */
    val keywords: String = "",
    /**
     * What the search source says this result is (see [SearchMatch.Kind]): "house", "house-range",
     * "street", "locality", "postcode", "postcode-area" or "place". Blank = not known (worked out
     * from the name). Not used for ranking, only to label results.
     */
    val kind: String = "",
    /** Private saved-record metadata; never substitutes for source geography. */
    val savedId: String? = null,
    val personalName: String? = null,
    val group: String? = null,
) {
    val displayName: String get() = personalName?.takeIf { it.isNotBlank() } ?: name
}

/**
 * One lane at a junction: which way it goes and whether it's a lane you can use.
 * [preferred]: of the usable lanes, the best one(s) — e.g. the leftmost when you turn left
 * again straight after.
 */
data class Lane(val indications: List<String>, val valid: Boolean, val preferred: Boolean = true, /** A bus lane: never yours, shown as BUS. */ val bus: Boolean = false)

/** Lane markings at a junction part-way along a step, where you carry on (e.g. a lane peeling off a motorway). */
data class LaneSpot(val location: LatLon, val lanes: List<Lane>)

data class RouteStep(
    val instruction: String,
    val type: String,
    val modifier: String,
    val name: String,
    /** Length of this step in metres (from its maneuver to the next one). */
    val distance: Double,
    val location: LatLon,
    /** Roundabout exit number, 0 if not a roundabout. */
    val exit: Int = 0,
    val bearingBefore: Int = 0,
    val bearingAfter: Int = 0,
    /** True where traffic drives on the left (UK, Ireland, Australia…). */
    val leftDriving: Boolean = false,
    val lanes: List<Lane> = emptyList(),
    /** Expected time for this step in seconds (from the route server). */
    val duration: Double = 0.0,
    /** Motorway junction / exit number from the road sign, e.g. "12". */
    val exitNumber: String = "",
    /** The road's number (e.g. "A6", "B5465"), when the route data gives it. */
    val ref: String = "",
    /** Where the road sign says this way goes, e.g. "A34 Oxford". */
    val towards: String = "",
    /** Junctions with lane markings between this maneuver and the next, where you keep going. */
    val laneSpots: List<LaneSpot> = emptyList(),
    /**
     * Junctions on this step where a road leaves that you could take but the route carries on
     * past (another exit). Only meaningful when [junctionsKnown] is true: route data without
     * junction details (TomTom, Valhalla's own format) can't say whether an exit is the next one.
     */
    val sideExits: List<LatLon> = emptyList(),
    val junctionsKnown: Boolean = false,
    /**
     * 11.7: a roundabout's other exits, as degrees travelled round the ring from where you enter
     * (the way traffic goes round), for the picture; empty when unknown.
     */
    val roundaboutArms: List<Int> = emptyList(),
    /** 11.7: your exit, as degrees travelled round the ring from the entry; -1 when unknown. */
    val roundaboutExitAt: Int = -1,
) {
    val isRoundabout get() = type == "roundabout" || type == "rotary"
}
