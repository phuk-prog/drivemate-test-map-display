package com.drivemate.app.nav

/**
 * Measures route geometry against *verified* road-centreline candidates.
 * It deliberately does not snap or modify routes: a geometrically close
 * parallel road, slip road, bridge or opposite carriageway may be illegal.
 */
data class RoadCentreline(val id: String, val points: List<LatLon>, val verified: Boolean)

data class GeometryDistances(
    val samples: Int,
    val medianMetres: Double?,
    val p95Metres: Double?,
    val maxMetres: Double?,
    val unmatchedSamples: Int,
)

/** A pure, deterministic diagnostic that can be used before and after a separately validated match. */
object RouteGeometryAudit {
    fun measure(route: List<LatLon>, roads: List<RoadCentreline>, sampleEveryMetres: Double = 10.0): GeometryDistances {
        require(sampleEveryMetres.isFinite() && sampleEveryMetres > 0)
        if (route.isEmpty()) return GeometryDistances(0, null, null, null, 0)
        val segments = roads.filter { it.verified }.flatMap { it.points.zipWithNext() }
        val sampled = ArrayList<LatLon>()
        sampled += route.first()
        for ((a, b) in route.zipWithNext()) {
            val metres = Geo.distance(a, b)
            if (!metres.isFinite() || metres > 100_000) continue
            val n = kotlin.math.ceil(metres / sampleEveryMetres).toInt().coerceAtLeast(1)
            for (i in 1..n) sampled += Geo.interpolate(a, b, i.toDouble() / n)
        }
        val distances = sampled.mapNotNull { p ->
            segments.minOfOrNull { (a, b) -> Geo.project(p, a, b).distance }
        }.sorted()
        val missing = if (segments.isEmpty()) sampled.size else 0
        fun percentile(p: Double): Double? = distances.takeIf { it.isNotEmpty() }
            ?.let { it[((it.size - 1) * p).toInt()] }
        return GeometryDistances(sampled.size, percentile(0.5), percentile(0.95), distances.lastOrNull(), missing)
    }
}
