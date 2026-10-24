package com.drivemate.app.nav

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

/** Result of snapping a point onto a line segment. */
data class Projection(val t: Double, val distance: Double)

object Geo {
    private const val EARTH_RADIUS = 6_371_000.0

    fun distance(a: LatLon, b: LatLon): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS * atan2(sqrt(h), sqrt(1 - h))
    }

    /** Initial compass bearing from a to b, 0..360 degrees. */
    fun bearing(a: LatLon, b: LatLon): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    fun angleDiff(a: Double, b: Double): Double {
        val d = abs(a - b) % 360.0
        return if (d > 180) 360 - d else d
    }

    /** Projects p onto segment a-b using a local flat approximation (fine for short segments). */
    fun project(p: LatLon, a: LatLon, b: LatLon): Projection {
        val mPerLon = 111_320.0 * cos(Math.toRadians(p.lat))
        val mPerLat = 110_540.0
        val ax = (a.lon - p.lon) * mPerLon
        val ay = (a.lat - p.lat) * mPerLat
        val bx = (b.lon - p.lon) * mPerLon
        val by = (b.lat - p.lat) * mPerLat
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else ((-ax * dx - ay * dy) / len2).coerceIn(0.0, 1.0)
        val cx = ax + t * dx
        val cy = ay + t * dy
        return Projection(t, sqrt(cx * cx + cy * cy))
    }

    fun interpolate(a: LatLon, b: LatLon, t: Double) =
        LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)

    /** The point [metres] from [p] in compass direction [bearing]. */
    fun offset(p: LatLon, bearing: Double, metres: Double): LatLon {
        val r = Math.toRadians(bearing)
        val dLat = metres * Math.cos(r) / 111_320.0
        val dLon = metres * Math.sin(r) / (111_320.0 * Math.cos(Math.toRadians(p.lat)))
        return LatLon(p.lat + dLat, p.lon + dLon)
    }

}
