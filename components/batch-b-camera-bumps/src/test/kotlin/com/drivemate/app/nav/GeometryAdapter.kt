package com.drivemate.app.nav
/** Synthetic metre coordinates only. Never evidence of geographic matching. */
internal data class LatLon(val lat: Double, val lon: Double)
internal object Geo {
    data class Projection(val distance: Double)
    fun distance(a: LatLon,b: LatLon) = kotlin.math.hypot(a.lat-b.lat,a.lon-b.lon)
    fun project(p: LatLon,a: LatLon,b: LatLon) = Projection(distance(p,a))
    fun angleDiff(a: Double,b: Double) = 0.0
    fun bearing(a: LatLon,b: LatLon) = 0.0
}
