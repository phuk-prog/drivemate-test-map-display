package com.drivemate.app.nav
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

class HeadingEstimator {
    /** Best guess of the direction of travel (degrees clockwise from north), or null until GPS has given one. */
    var heading: Double? = null
        private set
    private var gx = 0.0
    private var gy = 0.0
    private var gz = 0.0
    private var lastGyroNanos = 0L
    private var lastFixMs: Long? = null

    /** True when GPS confirmed the heading recently enough for the gyroscope's drift not to matter. */
    fun fresh(nowMs: Long): Boolean {
        val fix = lastFixMs ?: return false
        return heading != null && !moved && nowMs >= fix && nowMs - fix < 90_000
    }

    // Phone tilt half a second ago, to notice the phone being picked up or moved in its holder.
    private var anchorX = 0.0
    private var anchorY = 0.0
    private var anchorZ = 0.0
    private var anchorNanos = 0L

    /** True after the phone was picked up or moved, until a GPS fix at speed confirms the heading again. */
    var moved = false
        private set

    /**
     * Gravity in the phone's own axes (pointing up, as Android reports it). With [timestampNanos],
     * a tilt of more than [MOVED_DEGREES] within about half a second means the phone was handled or
     * moved in its holder: the gyroscope then measured the hand, not the car, so the heading is no
     * longer trusted ([fresh] false) until GPS gives it again.
     */
    fun onGravity(x: Float, y: Float, z: Float, timestampNanos: Long = 0L) {
        gx = x.toDouble(); gy = y.toDouble(); gz = z.toDouble()
        if (timestampNanos <= 0) return
        if (anchorNanos == 0L || timestampNanos < anchorNanos) { anchor(timestampNanos); return }
        if (timestampNanos - anchorNanos < 500_000_000L) return
        val a = sqrt(anchorX * anchorX + anchorY * anchorY + anchorZ * anchorZ)
        val g = sqrt(gx * gx + gy * gy + gz * gz)
        if (a > 1 && g > 1) {
            val cos = ((anchorX * gx + anchorY * gy + anchorZ * gz) / (a * g)).coerceIn(-1.0, 1.0)
            if (Math.toDegrees(Math.acos(cos)) > MOVED_DEGREES) {
                moved = true
            }
        }
        anchor(timestampNanos)
    }

    private fun anchor(t: Long) { anchorX = gx; anchorY = gy; anchorZ = gz; anchorNanos = t }

    /** Rotation speed (radians per second) around the phone's own axes. */
    fun onGyro(timestampNanos: Long, wx: Float, wy: Float, wz: Float) {
        val prev = lastGyroNanos
        lastGyroNanos = timestampNanos
        val h = heading ?: return
        if (prev == 0L) return
        val dt = (timestampNanos - prev) / 1e9
        if (dt <= 0 || dt > 0.25) return // a gap in readings: don't guess across it
        val g = sqrt(gx * gx + gy * gy + gz * gz)
        if (g < 7 || g > 12.5) return // gravity not settled (phone being handled)
        // Turning about "up", anticlockwise positive; compass bearings go clockwise, hence the minus.
        val yaw = (wx * gx + wy * gy + wz * gz) / g
        heading = norm(h - Math.toDegrees(yaw * dt))
    }

    /** A GPS fix. Only fixes at a steady speed have a trustworthy direction. */
    fun onGps(bearing: Float, speed: Float, nowMs: Long) {
        if (!bearing.isFinite() || !speed.isFinite() || speed < 4f || nowMs < 0) return
        val previous = lastFixMs
        if (previous != null && nowMs <= previous) return
        val h = heading
        heading = if (h == null || previous == null || moved || nowMs - previous >= 90_000) norm(bearing.toDouble())
        else {
            // Preserve the 0.3 correction at 1 Hz without amplifying GPS noise at higher rates.
            val seconds = (nowMs - previous) / 1000.0
            val weight = 1.0 - exp(ln(0.7) * seconds)
            norm(h + signedDiff(bearing.toDouble(), h) * weight)
        }
        lastFixMs = nowMs
        moved = false
    }

    fun reset() {
        heading = null
        lastFixMs = null
        lastGyroNanos = 0L
        anchorNanos = 0L
        moved = false
    }

    companion object {
        /** Tilt within half a second that means the phone was handled, not the car cornering. */
        const val MOVED_DEGREES = 15.0
        fun norm(a: Double) = ((a % 360) + 360) % 360
        /** b − a as the shorter way round, −180…180. */
        fun signedDiff(b: Double, a: Double) = ((b - a + 540) % 360) - 180
    }
}

