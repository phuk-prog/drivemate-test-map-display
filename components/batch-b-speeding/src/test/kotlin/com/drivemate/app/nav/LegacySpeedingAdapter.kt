package com.drivemate.app.nav
import kotlin.math.roundToInt

/** Extracted unchanged controller method from app f29c40582394817eb7396eb220cbc287f638a479.
 * Android clock/location, settings and output sinks only are replaced by deterministic adapters.
 * Legacy scenario uses imperial=false, speedMargin=0, beepAlerts=false.
 * It does not run Android TTS, check real signs or verify private integration.
 */
internal class LegacySpeedingAdapter {
    private var overSince = 0L
    private var legalSince = 0L
    private var lastSpeedWarning = 0L
    private var clock = 0L
    private object SystemClock { var time = 0L; fun elapsedRealtime() = time }
    private data class Location(val speed: Float, val provider: String, val validSpeed: Boolean) {
        fun hasSpeed() = validSpeed
    }
    private object CarBridge { val carSpeed: Float? = null }
    private object settings { const val speedMargin = 0; const val beepAlerts = false }
    private object format {
        fun isOverBy(speed: Float, limitKmh: Double, margin: Int) = (speed * 3.6f).roundToInt() > limitKmh.roundToInt() + margin
        fun limit(kmh: Double) = kmh.roundToInt()
    }
    private class Sink { var emitted = false; fun beep(urgent: Boolean) { emitted = true }; fun say(text: String, alert: Boolean) { emitted = true } }
    private val voice = Sink()
    private fun warnIfSpeeding(loc: Location, limitKmh: Double?) {
        val now = SystemClock.elapsedRealtime()
        // 8.1: never a speeding warning from a speed the app only estimated (no GPS); the car's
        // own speed from Android Auto is measured and still counts.
        val estimated = loc.provider == "estimate"
        if (limitKmh == null || !loc.hasSpeed() || (estimated && CarBridge.carSpeed == null)) { overSince = 0L; return }
        if (!format.isOverBy(loc.speed, limitKmh, settings.speedMargin)) {
            overSince = 0L
            if (legalSince == 0L) legalSince = now
            if (now - legalSince > 30_000) lastSpeedWarning = 0L
            return
        }
        legalSince = 0L
        if (overSince == 0L) overSince = now
        if (now - overSince >= 5_000 && now - lastSpeedWarning > 120_000) {
            lastSpeedWarning = now
            if (settings.beepAlerts) voice.beep(urgent = true)
            else voice.say("Speed limit ${format.limit(limitKmh)}", alert = true)
        }
    }
    fun update(now: Long, speed: Double?, limit: Double?, measured: Boolean, sound: SpeedingWarningPolicy.Sound): SpeedingWarningPolicy.Signal {
        SystemClock.time = now
        voice.emitted = false
        warnIfSpeeding(Location(speed?.toFloat() ?: 0f, if (measured) "gps" else "estimate", speed != null), limit)
        return if (!voice.emitted || sound == SpeedingWarningPolicy.Sound.MUTED) SpeedingWarningPolicy.Signal.NONE else SpeedingWarningPolicy.Signal.SPEECH
    }
}
