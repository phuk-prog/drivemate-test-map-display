package com.drivemate.app.nav
class GpsFilter {
    companion object {
        /**
         * 8.2: stopped at lights or in a queue, GPS wanders a few metres either way. Below walking
         * pace the arrow stays where it is unless the fix has moved clearly further than its own
         * error ([d] metres from where the arrow is held, [accuracy] metres: Android's 68 % radius,
         * so a still phone lands beyond it about a third of the time; twice it, rarely).
         * Pulling away (speed above 0.8 m/s) moves it at once.
         */
        fun staysStill(speed: Float, d: Double, accuracy: Double): Boolean =
            speed < 0.8f && d < maxOf(accuracy * 2.0, 12.0)

        /**
         * Fixes thrown away: a much less accurate fix (e.g. from Wi-Fi) right after a good GPS one,
         * or an impossible jump (faster than 250 km/h and further than both fixes' error).
         * [dt] seconds and [d] metres since the last kept fix.
         */
        fun rejects(dt: Double, d: Double, accuracy: Double, previousAccuracy: Double): Boolean =
            (accuracy > 40 && accuracy > previousAccuracy * 2 && dt < 15) ||
                (dt < 30 && d / dt > 70 && d > accuracy + previousAccuracy)

        /**
         * Speed to use for a kept fix: the reported one, or worked out from movement when there is
         * none, or when a receiver with no speed accuracy says exactly 0 while the car has clearly
         * moved further than the fix error (the emulator's `geo fix` does this, which hid every
         * speed-dependent behaviour from the robot drive). Returns null to keep the reported value.
         */
        fun derivedSpeed(reported: Float?, hasSpeedAccuracy: Boolean, dt: Double, d: Double, accuracy: Double): Float? {
            if (dt <= 0) return null
            val fromMovement = (d / dt).toFloat()
            if (reported == null) return fromMovement
            val zeroButMoving = reported == 0f && !hasSpeedAccuracy && dt <= 5.0 && d > maxOf(2 * accuracy, 10.0)
            return if (zeroButMoving) fromMovement else null
        }
    }

}
