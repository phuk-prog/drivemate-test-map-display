package com.drivemate.app.nav

/** Distance-based display geometry. Construct once per immutable route, not per GPS fix. */
internal class RouteGeometryIndex(private val points: List<LatLon>) {
    private val cumulative = DoubleArray(points.size).apply {
        for (i in 1 until size) this[i] = this[i - 1] + Geo.distance(points[i - 1], points[i])
    }
    val total: Double get() = cumulative.lastOrNull() ?: 0.0

    fun remaining(fraction: Double): List<LatLon> {
        if (!fraction.isFinite() || points.isEmpty()) return emptyList()
        if (fraction >= 1.0 || total <= 0.0) return listOf(points.last())
        return slice(total * fraction.coerceAtLeast(0.0), total)
    }

    fun slice(from: Double, to: Double): List<LatLon> {
        if (!from.isFinite() || !to.isFinite() || points.size < 2 || total <= 0.0) return emptyList()
        val start = from.coerceIn(0.0, total)
        val end = to.coerceIn(0.0, total)
        if (end <= start) return emptyList()
        val result = ArrayList<LatLon>()
        result += pointAt(start)
        var i = firstAfter(start)
        while (i < points.size && cumulative[i] < end) result += points[i++]
        result += pointAt(end)
        return result
    }

    private fun pointAt(distance: Double): LatLon {
        if (distance <= 0.0) return points.first()
        if (distance >= total) return points.last()
        val i = firstAfter(distance)
        val length = cumulative[i] - cumulative[i - 1]
        return Geo.interpolate(points[i - 1], points[i], (distance - cumulative[i - 1]) / length)
    }

    private fun firstAfter(distance: Double): Int {
        var low = 0
        var high = cumulative.size
        while (low < high) {
            val mid = (low + high).ushr(1)
            if (cumulative[mid] <= distance) low = mid + 1 else high = mid
        }
        return low
    }
}
