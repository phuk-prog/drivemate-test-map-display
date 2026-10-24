package com.drivemate.app.nav

/** Engineering defaults, in route metres: about 20 s preparation and 5 s final warning.
 * These are warning timing choices, not provider accuracy or a legal stopping distance.
 */
internal object CameraApproachPolicy {
    fun approachMetres(speedMps: Double?): Double = ((usableSpeed(speedMps) ?: 0.0) * 20).coerceIn(200.0, 800.0)
    fun extraMetres(speedMps: Double?): Double = ((usableSpeed(speedMps) ?: 0.0) * 5).coerceIn(80.0, 250.0)
    fun freshSpeed(speedMps: Double?, measured: Boolean, ageMs: Long?): Double? =
        usableSpeed(speedMps)?.takeIf { measured && ageMs != null && ageMs in 0..5_000 }
    fun isOver(speedMps: Double?, limitKmh: Double?): Boolean =
        usableSpeed(speedMps) != null && limitKmh != null && limitKmh.isFinite() && limitKmh > 0 &&
            speedMps!! > limitKmh / 3.6
    private fun usableSpeed(speedMps: Double?): Double? = speedMps?.takeIf { it.isFinite() && it >= 0 }
}
