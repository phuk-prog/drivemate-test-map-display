package com.drivemate.app.nav
import java.nio.ByteBuffer
import java.security.MessageDigest

object RouteShapeKey {
    /** Stable private in-memory geometry identity; no coordinates are logged or published. */
    fun of(points:List<Pair<Double,Double>>):String? {
        if(points.size<2 || points.any{!it.first.isFinite() || it.first !in -90.0..90.0 || !it.second.isFinite() || it.second !in -180.0..180.0})return null
        val digest=MessageDigest.getInstance("SHA-256");val b=ByteBuffer.allocate(16)
        for((lat,lon) in points){b.clear();b.putDouble(lat);b.putDouble(lon);digest.update(b.array())}
        return digest.digest().joinToString(""){"%02x".format(it)}
    }
}
