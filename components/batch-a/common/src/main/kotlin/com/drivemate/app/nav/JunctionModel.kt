package com.drivemate.app.nav

import kotlin.math.cos
import kotlin.math.sin

/** Where a fact in the junction model came from. NONE = unknown (nothing is drawn from it). */
enum class DataSource { NONE, ROUTE_GEOMETRY, ROUTE_STEP, MAP_OSM, LANE_STRIP }

/** A value with where it came from and how far to trust it (0..1). A null value is "unknown". */
data class Sourced<out T>(val value: T?, val source: DataSource, val confidence: Double) {
    val known get() = value != null

    companion object {
        val UNKNOWN = Sourced(null, DataSource.NONE, 0.0)
    }
}

/** One loaded road piece (OSM way segment) with its tags, as read from the lane database. */
data class MapRoad(val id: String, val a: LatLon, val b: LatLon, val tags: Map<String, String>)

enum class ArmRole { ENTRY, EXIT, OTHER }

/**
 * A road joined to the junction (it shares a node with it in the map data).
 * [relative] is degrees clockwise from the way you enter (0 = straight ahead, 180 = back where you came from).
 * [leaving] / [entering] say which way traffic may legally use the arm (from the oneway tags only).
 */
data class JunctionArm(
    val bearing: Double,
    val relative: Int,
    val roadName: String,
    val level: Sourced<Int>,
    val levelExplicit: Boolean,
    val leaving: Boolean,
    val entering: Boolean,
    val role: ArmRole,
    val source: DataSource,
    val confidence: Double,
)

/** A road that passes the junction without joining it (a bridge over it, an underpass, an unconnected road). Never a turn. */
data class Crossing(val id: String, val name: String, val a: LatLon, val b: LatLon, val level: Sourced<Int>, val levelExplicit: Boolean)

/** The roundabout ring as measured from its mapped vertices. */
data class RingInfo(val centre: LatLon, val radiusM: Double)

/** Lanes for the approach. [recommended] are the lanes to use (from lane data only); this is never where the car is. */
data class JunctionLanes(val count: Int?, val recommended: List<Int>, val source: DataSource) {
    val known get() = source != DataSource.NONE && recommended.isNotEmpty()
}

/**
 * How well the phone knows where the car is sideways. Used to degrade the picture, never to
 * claim a lane (see [LaneClaims]).
 */
data class PositionEvidence(val accuracyM: Double?, val ageMs: Long?, val parallelRoadNear: Boolean) {
    enum class Quality { GOOD, APPROXIMATE, POOR }

    val quality: Quality get() = when {
        accuracyM == null || ageMs == null || ageMs > 5_000 || accuracyM > 25.0 -> Quality.POOR
        accuracyM > 8.0 || parallelRoadNear -> Quality.APPROXIMATE
        else -> Quality.GOOD
    }
}

/**
 * The one geographical junction model (MG08) that the drawn junction scene, the lower overview
 * and the live map read, so they agree. Built only from data the app already has: route
 * geometry, the route step and the loaded map roads. Every element has a source and a
 * confidence, and unknown stays unknown: nothing here is inferred from an exit number.
 */
data class JunctionModel(
    val kind: JunctionKind,
    val datasetId: String,
    val centre: Sourced<LatLon>,
    val entry: Sourced<LatLon>,
    val exit: Sourced<LatLon>,
    val ring: Sourced<RingInfo>,
    val arms: List<JunctionArm>,
    val crossings: List<Crossing>,
    val routeLevel: Sourced<Int>,
    val lanes: JunctionLanes,
    /** Does the exit number the route gave match the exit counted from the real arms? Null = could not check. */
    val exitNumberAgrees: Boolean?,
    val parallelRoadNearEntry: Boolean,
    val position: PositionEvidence? = null,
    /** RV01: the route's own centre line for the approach, ending at the junction (null value = unknown: draw it straight). */
    val approach: Sourced<List<LatLon>> = Sourced.UNKNOWN,
    /** MG11: verified lane-to-lane connections (spiral or a required change). Unknown unless a data source supplied them. */
    val connectivity: LaneConnectivity = LaneConnectivity.UNKNOWN,
) {
    val entryArm get() = arms.firstOrNull { it.role == ArmRole.ENTRY }
    val exitArm get() = arms.firstOrNull { it.role == ArmRole.EXIT }

    /**
     * True when the real arms may be drawn: the route's own exit arm was found in the map and, for a
     * roundabout, the ring was measured and the exit number agrees with the arms counted. Otherwise
     * the existing schematic is used.
     */
    val armsTrusted: Boolean get() {
        if (exitArm == null || entryArm == null || !centre.known) return false
        if (kind == JunctionKind.ROUNDABOUT && (!ring.known || exitNumberAgrees == false)) return false
        return exitNumberAgrees != false
    }

    /** Words for the strip under the upper view. Lane connections are called checked only when verified data gave them. */
    fun caption(): String {
        val lanes = if (connectivity.verified) "lane connections from verified data" else "lane changes not checked"
        return if (armsTrusted) "Drawn from map data · $lanes" else "Drawn junction · $lanes"
    }

    /** Roads of any level (arms and crossings) sorted for drawing: lowest level first, highest last. */
    fun crossingsInDrawOrder(): List<Crossing> = LevelOrder.sorted(crossings) { it.level }
}

/** MG15: reading road level from OSM tags, and drawing order. */
object LevelOrder {
    private val NO = setOf("no", "false", "0")

    /** Level from layer / bridge / tunnel / level tags; (level, explicit). No tags = ground, not explicit. */
    fun of(tags: Map<String, String>): Pair<Sourced<Int>, Boolean> {
        tags["layer"]?.trim()?.toIntOrNull()?.let { return Sourced(it, DataSource.MAP_OSM, 0.9) to true }
        val bridge = tags["bridge"]?.lowercase()
        if (bridge != null && bridge !in NO) return Sourced(1, DataSource.MAP_OSM, 0.8) to true
        val tunnel = tags["tunnel"]?.lowercase()
        if (tunnel != null && tunnel !in NO) {
            val conf = if (tunnel == "building_passage" || tunnel == "culvert") 0.5 else 0.8
            return Sourced(-1, DataSource.MAP_OSM, conf) to true
        }
        tags["level"]?.let { raw -> Regex("-?\\d+").find(raw)?.value?.toIntOrNull()?.let { return Sourced(it, DataSource.MAP_OSM, 0.6) to true } }
        return Sourced(0, DataSource.MAP_OSM, 0.4) to false
    }

    /** [items] with the lowest level first (drawn first, so higher levels end up on top). Stable. */
    fun <T> sorted(items: List<T>, level: (T) -> Sourced<Int>): List<T> = items.sortedBy { level(it).value ?: 0 }

    /** True when [other] runs on a different level from the route, so it must not be offered as a turn. */
    fun separatedFromRoute(route: Sourced<Int>, other: Sourced<Int>, otherExplicit: Boolean): Boolean {
        val r = route.value ?: return false
        val o = other.value ?: return false
        return otherExplicit && o != r
    }
}

/**
 * MG10: recommended lanes are a different thing from where the car is. Phone GPS cannot say
 * which lane the car is in, so an "occupied lane" is only ever claimed when the evidence is
 * stronger than a lane's width, which a phone fix practically never is.
 */
object LaneClaims {
    /** Best accuracy (metres, 1 sigma) at which a lane could even be claimed: a quarter of a UK lane. */
    const val MAX_ACCURACY_M = JunctionScene.LANE_M / 4.0
    const val MAX_AGE_MS = 2_000L

    sealed class Occupancy {
        data class Lane(val index: Int) : Occupancy()
        data class Unknown(val reason: String) : Occupancy()
    }

    /**
     * The lane the car occupies, or [Occupancy.Unknown] with the reason. [lateralOffsetM] is how far
     * right of the road's centre line the fix is (the road's centre line comes from map data).
     */
    fun occupied(evidence: PositionEvidence?, lateralOffsetM: Double?, laneCount: Int?): Occupancy {
        if (evidence == null || evidence.accuracyM == null || lateralOffsetM == null) return Occupancy.Unknown("no position")
        if ((evidence.ageMs ?: Long.MAX_VALUE) > MAX_AGE_MS) return Occupancy.Unknown("old position")
        if (evidence.parallelRoadNear) return Occupancy.Unknown("another road runs alongside")
        if (laneCount == null || laneCount < 1) return Occupancy.Unknown("lane count not known")
        if (evidence.accuracyM > MAX_ACCURACY_M) return Occupancy.Unknown("position not precise enough for a lane")
        val w = JunctionScene.LANE_M
        val fromLeft = lateralOffsetM + laneCount * w / 2
        val idx = Math.floor(fromLeft / w).toInt()
        if (idx !in 0 until laneCount) return Occupancy.Unknown("outside the road")
        val toBoundary = minOf(fromLeft - idx * w, (idx + 1) * w - fromLeft)
        if (toBoundary < evidence.accuracyM) return Occupancy.Unknown("on a lane line")
        return Occupancy.Lane(idx)
    }

    /**
     * May a position be pulled onto a mapped road? Never across to a road that is not joined to
     * the road we were on (a parallel road, a bridge or tunnel at another level).
     */
    fun maySnap(connectedToCurrentRoad: Boolean, sameLevel: Boolean): Boolean = connectedToCurrentRoad && sameLevel
}

/** Builds the [JunctionModel] from the route step and the loaded map roads. */
object JunctionModelBuilder {
    private const val SEARCH_M = 120.0
    private const val NODE_TOL_M = 5.0
    private const val ARM_LEN_M = 45.0
    private const val ROLE_TOL_DEG = 30.0

    private data class Node(val at: LatLon)
    private data class RawArm(val road: MapRoad, val bearing: Double, val len: Double, val leaving: Boolean, val entering: Boolean)

    private fun dist(p: LatLon, r: MapRoad) = Geo.project(p, r.a, r.b).distance

    private fun legal(tags: Map<String, String>, nodeIsA: Boolean): Pair<Boolean, Boolean> {
        val ow = LocalRoadMatcher.oneWay(tags)
        val leaving = ow == 0 || (ow == 1 && nodeIsA) || (ow == -1 && !nodeIsA)
        val entering = ow == 0 || (ow == 1 && !nodeIsA) || (ow == -1 && nodeIsA)
        return leaving to entering
    }

    /** Measures a full ring from its mapped vertices; null when it is partial or not round. */
    internal fun measureRing(ringRoads: List<MapRoad>): RingInfo? {
        val pts = ringRoads.flatMap { listOf(it.a, it.b) }.distinct()
        if (pts.size < 6) return null
        val c = LatLon(pts.map { it.lat }.average(), pts.map { it.lon }.average())
        val rs = pts.map { Geo.distance(c, it) }
        val mean = rs.average()
        if (mean !in 5.0..70.0 || (rs.max() - rs.min()) / mean > 0.35) return null
        val bearings = pts.map { Geo.bearing(c, it) }.sorted()
        val gaps = bearings.zipWithNext { x, y -> y - x } + (360 - bearings.last() + bearings.first())
        if (gaps.max() > 100.0) return null
        return RingInfo(c, mean)
    }

    fun build(
        plan: JunctionPlan,
        location: LatLon,
        entryBearing: Double,
        exitBearing: Double,
        roads: List<MapRoad>,
        datasetId: String,
        entryPoint: LatLon? = null,
        exitPoint: LatLon? = null,
        approachGeometry: List<LatLon>? = null,
        connectivity: LaneConnectivity = LaneConnectivity.UNKNOWN,
    ): JunctionModel {
        val nearby = roads.filter { LocalRoadMatcher.drivable(it.tags) && dist(location, it) <= SEARCH_M }
        val ringRoads = if (plan.kind == JunctionKind.ROUNDABOUT)
            nearby.filter { it.tags["junction"] in setOf("roundabout", "circular") && dist(location, it) <= 90.0 } else emptyList()
        val ring = if (ringRoads.isNotEmpty()) measureRing(ringRoads) else null
        val ringIds = ringRoads.map { it.id }.toSet()
        val others = nearby.filter { it.id !in ringIds }

        // The nodes arms attach to: the ring's vertices, or the junction point itself.
        val nodes: List<Node> = when {
            plan.kind == JunctionKind.ROUNDABOUT -> if (ring != null) ringRoads.flatMap { listOf(it.a, it.b) }.distinct().map(::Node) else emptyList()
            else -> others.flatMap { listOf(it.a, it.b) }.filter { Geo.distance(location, it) <= 12.0 }.map(::Node).take(1)
                .ifEmpty { emptyList() }
        }
        val centrePoint = if (plan.kind == JunctionKind.ROUNDABOUT) ring?.centre else if (nodes.isNotEmpty()) location else null

        val raw = ArrayList<RawArm>()
        for (road in others) for (end in listOf(true, false)) {
            val nodePt = if (end) road.a else road.b
            val node = nodes.firstOrNull { Geo.distance(it.at, nodePt) <= NODE_TOL_M } ?: continue
            var far = if (end) road.b else road.a
            var prev = nodePt
            var seg = road
            var guard = 0
            while (Geo.distance(node.at, far) < ARM_LEN_M && guard++ < 12) {
                val next = others.firstOrNull { o ->
                    o !== seg && o.id == seg.id &&
                        (Geo.distance(o.a, far) < 0.5 && Geo.distance(o.b, prev) > 0.5 || Geo.distance(o.b, far) < 0.5 && Geo.distance(o.a, prev) > 0.5)
                } ?: break
                val nf = if (Geo.distance(next.a, far) < 0.5) next.b else next.a
                prev = far; far = nf; seg = next
            }
            val len = Geo.distance(node.at, far)
            if (len < 2.0) continue
            // Skip pieces that only run inside the ring (not real arms).
            if (ring != null && Geo.distance(ring.centre, far) < ring.radiusM * 0.95) continue
            val (leaving, entering) = legal(road.tags, end)
            raw += RawArm(road, Geo.bearing(node.at, far), len, leaving, entering)
        }

        // Merge duplicates and the two halves of a dual-carriageway arm (within 20 degrees).
        val merged = ArrayList<MutableList<RawArm>>()
        for (a in raw.sortedBy { it.bearing }) {
            val g = merged.firstOrNull { grp -> Geo.angleDiff(grp.first().bearing, a.bearing) < 20.0 }
            if (g != null) g += a else merged += mutableListOf(a)
        }
        val routeLevelProbe = ring?.let { ringRoads.firstOrNull()?.let { r -> LevelOrder.of(r.tags) } }

        fun relative(b: Double) = (((b - entryBearing) % 360 + 360) % 360).let { Math.round(it).toInt() % 360 }
        val entryIdx = merged.indices.minByOrNull { Geo.angleDiff(merged[it].first().bearing, entryBearing + 180) }
            ?.takeIf { Geo.angleDiff(merged[it].first().bearing, entryBearing + 180) <= ROLE_TOL_DEG && merged[it].any { a -> a.entering } }
        val exitIdx = merged.indices.filter { merged[it].any { a -> a.leaving } }
            .minByOrNull { Geo.angleDiff(merged[it].first().bearing, exitBearing) }
            ?.takeIf { Geo.angleDiff(merged[it].first().bearing, exitBearing) <= ROLE_TOL_DEG }

        var arms = merged.mapIndexed { i, grp ->
            val best = grp.maxBy { it.len }
            val (lvl, explicit) = LevelOrder.of(best.road.tags)
            JunctionArm(
                bearing = best.bearing, relative = relative(best.bearing),
                roadName = best.road.tags["name"] ?: best.road.tags["ref"].orEmpty(),
                level = lvl, levelExplicit = explicit,
                leaving = grp.any { it.leaving }, entering = grp.any { it.entering },
                role = when (i) { exitIdx -> ArmRole.EXIT; entryIdx -> ArmRole.ENTRY; else -> ArmRole.OTHER },
                source = DataSource.MAP_OSM,
                confidence = if (best.len >= 15.0) 0.85 else 0.5,
            )
        }.sortedBy { it.relative }
        // A u-turn exit is also the entry arm: keep EXIT role for it.
        if (exitIdx != null && exitIdx == entryIdx) arms = arms.map { if (it.role == ArmRole.ENTRY) it.copy(role = ArmRole.EXIT) else it }

        // Route level: the ring's own, else the road we enter on.
        val routeLevel: Sourced<Int> = routeLevelProbe?.first
            ?: arms.firstOrNull { it.role == ArmRole.ENTRY }?.level
            ?: Sourced.UNKNOWN

        // Exit number cross-check (UK: clockwise from where you came in).
        var agrees: Boolean? = null
        if (plan.kind == JunctionKind.ROUNDABOUT && plan.roundaboutExit > 0 && ring != null && arms.any { it.role == ArmRole.EXIT }) {
            fun key(a: JunctionArm): Int {
                val k = if (plan.leftDriving) ((a.relative - 180 + 360) % 360) else ((180 - a.relative + 360) % 360)
                return if (k == 0) 360 else k
            }
            val routeArm = arms.first { it.role == ArmRole.EXIT }
            val counted = arms.count { it.leaving && key(it) <= key(routeArm) && !LevelOrder.separatedFromRoute(routeLevel, it.level, it.levelExplicit) }
            agrees = counted == plan.roundaboutExit
        }

        // Roads that pass without joining: never turns.
        val armWayIds = raw.map { it.road.id }.toSet()
        val reach = ring?.let { it.radiusM + 25.0 } ?: 40.0
        val crossings = others.filter { r -> r.id !in armWayIds && centrePoint != null && Geo.project(centrePoint, r.a, r.b).distance <= reach }
            .map { r -> val (l, e) = LevelOrder.of(r.tags); Crossing(r.id, r.tags["name"] ?: r.tags["ref"].orEmpty(), r.a, r.b, l, e) }

        // A road running alongside the approach (what makes sideways position ambiguous).
        val entryRef = entryPoint ?: Geo.offset(location, (entryBearing + 180) % 360, 30.0)
        val entryWays = raw.filter { Geo.angleDiff(it.bearing, entryBearing + 180) <= ROLE_TOL_DEG }.map { it.road.id }.toSet()
        val parallel = nearby.any { r ->
            r.id !in entryWays && r.id !in ringIds && dist(entryRef, r) in 1.0..25.0 &&
                Geo.bearing(r.a, r.b).let { b -> Geo.angleDiff(b, entryBearing) < 20.0 || Geo.angleDiff(b, entryBearing) > 160.0 }
        }

        val laneSource = if (plan.source == RibbonSource.ARROWS) DataSource.LANE_STRIP else DataSource.NONE
        return JunctionModel(
            kind = plan.kind,
            datasetId = datasetId,
            centre = if (centrePoint != null) Sourced(centrePoint, DataSource.MAP_OSM, if (ring != null) 0.8 else 0.7) else Sourced.UNKNOWN,
            entry = entryPoint?.let { Sourced(it, DataSource.ROUTE_GEOMETRY, 0.9) } ?: Sourced.UNKNOWN,
            exit = exitPoint?.let { Sourced(it, DataSource.ROUTE_GEOMETRY, 0.9) } ?: Sourced.UNKNOWN,
            ring = ring?.let { Sourced(it, DataSource.MAP_OSM, 0.8) } ?: Sourced.UNKNOWN,
            arms = arms, crossings = crossings, routeLevel = routeLevel,
            lanes = JunctionLanes(plan.lanes, if (laneSource == DataSource.NONE) emptyList() else plan.highlighted, laneSource),
            exitNumberAgrees = agrees, parallelRoadNearEntry = parallel,
            approach = approachGeometry?.takeIf { it.size >= 3 }?.let { Sourced(it, DataSource.ROUTE_GEOMETRY, 0.9) } ?: Sourced.UNKNOWN,
            connectivity = connectivity,
        )
    }

    /** Metres east/north of [origin] (for drawing). */
    internal fun local(origin: LatLon, p: LatLon): Pair<Double, Double> {
        val d = Geo.distance(origin, p)
        val b = Math.toRadians(Geo.bearing(origin, p))
        return d * sin(b) to d * cos(b)
    }

    /** [local] rotated so that [heading] points up the picture (x right, y up). */
    internal fun rotated(origin: LatLon, p: LatLon, heading: Double): Pair<Double, Double> {
        val (e, n) = local(origin, p)
        val h = Math.toRadians(heading)
        val x = e * cos(h) - n * sin(h)
        val y = e * sin(h) + n * cos(h)
        return x to y
    }
}
