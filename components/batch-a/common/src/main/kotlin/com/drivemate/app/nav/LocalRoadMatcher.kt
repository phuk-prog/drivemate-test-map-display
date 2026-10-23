package com.drivemate.app.nav

/** Conservative free-driving matching to downloaded OSM geometry, independent of the map view. */
class LocalRoadMatcher {
    data class Segment(val id: String, val a: LatLon, val b: LatLon, val name: String = "", val oneWay: Int = 0)
    data class Fix(val at: LatLon, val heading: Double?, val speedMps: Double, val accuracyM: Double, val elapsedMs: Long)
    data class Match(val roadId: String, val name: String, val position: LatLon, val heading: Double, val segments: List<Pair<LatLon, LatLon>>, val confidence: Double) {
        /**
         * The same match moved [metres] further along its road in the direction of travel (to
         * allow for GPS delay). Follows the road's own pieces while they bend less than 30°, and
         * stops at the road's end: it never guesses which road you take at a junction.
         */
        fun advance(metres: Double): Match {
            if (!metres.isFinite() || metres < 0.5) return this
            var left = metres.coerceAtMost(45.0)
            var at = position
            var dir = heading
            val used = HashSet<Int>()
            while (left > 0.5) {
                val index = segments.indices.firstOrNull { i ->
                    i !in used && Geo.project(at, segments[i].first, segments[i].second).distance < 1.5 &&
                        listOf(segments[i].first, segments[i].second).any { end ->
                            Geo.distance(at, end) > 0.5 && Geo.angleDiff(Geo.bearing(at, end), dir) < 30
                        }
                } ?: break
                used += index
                val (a, b) = segments[index]
                val end = if (Geo.distance(at, b) > 0.5 && Geo.angleDiff(Geo.bearing(at, b), dir) < 30) b else a
                val d = Geo.distance(at, end)
                if (d >= left) {
                    at = Geo.interpolate(at, end, left / d)
                    left = 0.0
                } else {
                    dir = Geo.bearing(at, end)
                    at = end
                    left -= d
                }
            }
            return if (at == position) this else copy(position = at, heading = if (left > 0.5) heading else dir)
        }
    }
    private data class Candidate(val segment: Segment, val position: LatLon, val heading: Double, val score: Double)
    private var previous: Match? = null
    private var previousFix: Fix? = null

    fun reset() { previous = null; previousFix = null }

    companion object {
        /** How far back a junction may be for [branchedBehind] (metres). */
        private const val BRANCH_BEHIND = 300.0
        private val NOT_FOR_CARS = setOf("footway", "cycleway", "path", "pedestrian", "steps", "bridleway", "corridor", "platform", "construction", "proposed", "bus_guideway", "busway")

        /** False for OSM ways a car may not legally drive (footpaths, cycle tracks, bus-only, no motor vehicles). */
        fun drivable(tags: Map<String, String>): Boolean {
            if (tags["highway"] in NOT_FOR_CARS) return false
            if (tags["area"] == "yes") return false
            val access = listOf("motorcar", "motor_vehicle", "vehicle", "access").firstNotNullOfOrNull { tags[it] }
            return access !in setOf("no", "agricultural", "forestry", "delivery", "bus", "psv", "emergency")
        }

        /** 1 one-way along the drawing, -1 against it, 0 both ways. */
        fun oneWay(tags: Map<String, String>): Int = when {
            tags["oneway"] == "-1" -> -1
            tags["oneway"] in setOf("yes", "1", "true") || tags["junction"] in setOf("roundabout", "circular") ||
                (tags["highway"] in setOf("motorway", "motorway_link") && tags["oneway"] != "no") -> 1
            else -> 0
        }
    }

    private fun valid(point: LatLon) = point.lat.isFinite() && point.lat in -90.0..90.0 && point.lon.isFinite() && point.lon in -180.0..180.0

    /**
     * 8.4: a slip road that leaves gradually (UK diverge lanes run beside the main carriageway for
     * 100–250 m) is only told apart once the junction is behind the car. A road joined to the last
     * one at a junction up to [BRANCH_BEHIND] metres back may then be taken if the new position is
     * about as far from that junction as the last one was: the car took the branch there instead of
     * carrying on. Crossings and parallel roads with no shared junction still never qualify.
     */
    private fun branchedBehind(last: Match, heading: Double, junction: LatLon, position: LatLon, allowance: Double): Boolean {
        val back = Geo.distance(last.position, junction)
        if (back > BRANCH_BEHIND) return false
        if (Geo.angleDiff(heading, Geo.bearing(last.position, junction)) < 100) return false // the junction must be behind
        return Geo.distance(junction, position) <= back + allowance
    }

    fun match(fix: Fix, segments: List<Segment>): Match? {
        val close = minOf(30.0, maxOf(8.0, fix.accuracyM))
        val m = attempt(fix, segments, close) ?: run {
            // 8.S (audit stage 5, 20 Oct 2026): with an 8 m accuracy, a third of the fixes that matched nothing lay
            // 8-12 m from the road. Look 1.5 x the accuracy out, but only where that road is the one road of any
            // kind within 3 m more than that: next to a slip road or a parallel road the strict rule above stands
            // (a wider look in every case took the wrong road 9 times in PublishedSnapshotRoadMatchingTest).
            val wide = minOf(30.0, maxOf(10.0, fix.accuracyM * 1.5))
            val alone = segments.filter { Geo.project(fix.at, it.a, it.b).distance <= wide + 3 }.map { it.id }.distinct().size == 1
            if (alone && wide > close) attempt(fix, segments, wide) else null
        }
        if (m != null) { previous = m; previousFix = fix }
        return m
    }

    private fun attempt(fix: Fix, segments: List<Segment>, reach: Double): Match? {
        if (!fix.at.lat.isFinite() || fix.at.lat !in -90.0..90.0 || !fix.at.lon.isFinite() || fix.at.lon !in -180.0..180.0 ||
            !fix.accuracyM.isFinite() || fix.accuracyM !in 0.1..35.0 || !fix.speedMps.isFinite() || fix.speedMps !in 2.0..100.0 ||
            fix.heading == null || !fix.heading.isFinite() || fix.heading !in 0.0..360.0 || fix.elapsedMs <= 0) return null
        val lastFix = previousFix
        if (lastFix != null && fix.elapsedMs <= lastFix.elapsedMs) return null
        val dt = lastFix?.let { (fix.elapsedMs - it.elapsedMs) / 1_000.0 }
        val last = previous.takeIf { dt != null && dt <= 10 }
        val roadNodes = HashMap<String, List<LatLon>>()
        val candidates = segments.mapNotNull { segment ->
            if (segment.id.isBlank() || !valid(segment.a) || !valid(segment.b) || Geo.distance(segment.a, segment.b) < 1) return@mapNotNull null
            val projection = Geo.project(fix.at, segment.a, segment.b)
            if (projection.distance > reach) return@mapNotNull null
            val forward = Geo.bearing(segment.a, segment.b)
            val reverse = (forward + 180) % 360
            val heading = when (segment.oneWay) {
                1 -> forward
                -1 -> reverse
                else -> if (Geo.angleDiff(fix.heading, forward) <= Geo.angleDiff(fix.heading, reverse)) forward else reverse
            }
            val difference = Geo.angleDiff(fix.heading, heading)
            if (difference > 40) return@mapNotNull null
            val position = Geo.interpolate(segment.a, segment.b, projection.t)
            var continuityPenalty = 0.0
            if (last != null && lastFix != null && dt != null) {
                val allowance = maxOf(lastFix.speedMps, fix.speedMps) * dt * 1.8 + maxOf(15.0, fix.accuracyM)
                val travelled = Geo.distance(last.position, position)
                if (travelled > allowance) return@mapNotNull null
                if (segment.id != last.roadId) {
                    // A crossing, parallel carriageway or flyover is not a connection. Roads may
                    // change only through a node the two roads share in the downloaded geometry,
                    // and only if driving last position → that node → here fits the time since.
                    val nodes = roadNodes.getOrPut(segment.id) {
                        segments.filter { it.id == segment.id }.flatMap { listOf(it.a, it.b) }
                    }
                    // The last road's pieces near the last match and near this fix: after a missed fix or two
                    // the junction can be well past where the last match was (8.S audit stage 5, 20 Oct 2026;
                    // this also ended the one slip-road mix-up left in PublishedSnapshotRoadMatchingTest).
                    val lastRoad = last.segments + segments.filter { it.id == last.roadId }.map { it.a to it.b }
                    val connected = lastRoad.any { (a, b) ->
                        listOf(a, b).any { old -> nodes.any { next ->
                            Geo.distance(old, next) <= 3 && (
                                Geo.distance(last.position, next) + Geo.distance(next, position) <= allowance ||
                                    branchedBehind(last, fix.heading, next, position, allowance))
                        } }
                    }
                    if (!connected) return@mapNotNull null
                    continuityPenalty = 4.0
                }
            }
            Candidate(segment, position, heading, projection.distance + difference * 0.15 + continuityPenalty)
        }.groupBy { it.segment.id }.map { (_, group) -> group.minBy { it.score } }.sortedBy { it.score }
        val best = candidates.firstOrNull() ?: return null
        val second = candidates.getOrNull(1)
        val gap = second?.let { it.score - best.score } ?: reach
        // Ambiguous parallel roads remain unknown. Existing continuity never justifies a
        // blind initial choice, but a connected junction can retain an established road.
        if (gap < maxOf(3.0, fix.accuracyM * 0.3)) return null
        val confidence = ((1.0 - best.score / (reach + 8)) * minOf(1.0, gap / 10.0)).coerceIn(0.0, 1.0)
        // 8.5 (audit stage 5, 20 Oct 2026): carrying on along the road already followed, with no other road in reach,
        // is not a choice between roads; a few metres of wobble dropped such fixes one at a time (conf 0.42-0.44).
        val carryingOn = second == null && last != null && best.segment.id == last.roadId
        if (confidence < 0.45 && !carryingOn) return null
        val roadSegments = segments.filter { it.id == best.segment.id }.map { it.a to it.b }
        return Match(best.segment.id, best.segment.name, best.position, best.heading, roadSegments, confidence)
    }
}
