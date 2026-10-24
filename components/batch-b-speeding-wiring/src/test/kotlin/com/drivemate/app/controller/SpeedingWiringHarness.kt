package com.drivemate.app.controller
import kotlin.math.roundToInt

/** Exact proposed private warning method; Android, settings and output dependencies are adapted.
 * The method text is copied byte-for-byte from the staged private candidate.
 * Car speed TTL expression is copied from CarBridge, its clock is deterministic here.
 */
internal class SpeedingWiringHarness {
    enum class SoundMode { ALL, ALERTS, OFF }
    private data class Location(val provider: String?, val speed: Float, val speedPresent: Boolean, val elapsedRealtimeNanos: Long) {
        fun hasSpeed() = speedPresent
    }
    private object SystemClock {
        var now = 200_000L
        fun elapsedRealtime() = now
        fun elapsedRealtimeNanos() = now * 1_000_000
    }
    private object CarBridge {
        private var value: Float? = null
        private var at = 0L
        val carSpeed: Float? get() = if (SystemClock.elapsedRealtime() - at < 5_000) value else null
        fun set(speed: Float?, ageMs: Long) { value = speed; at = SystemClock.now - ageMs }
    }
    private class Settings { var sound = SoundMode.ALL; var beepAlerts = false }
    private val settings = Settings()
    private object format { fun limit(kmh: Double) = kmh.roundToInt() }
    private class Sink {
        val calls = mutableListOf<String>()
        var lastPolicyRepeat = false
        fun say(text: String, alert: Boolean, policyRepeat: Boolean = false) { lastPolicyRepeat = policyRepeat; calls += "speech:"+text+":"+alert }
        fun beep(urgent: Boolean) { calls += "tone:"+urgent }
    }
    private val voice = Sink()
    private var speedingWarningPolicy = com.drivemate.app.nav.SpeedingWarningPolicy()
    fun policyOwned() = voice.lastPolicyRepeat
    fun resetTrip() { speedingWarningPolicy = com.drivemate.app.nav.SpeedingWarningPolicy() }
    fun sample(now: Long = 200_000, speed: Float? = 15f, limit: Double? = 50.0, provider: String? = "gps",
               ageMs: Long? = 1_000, car: Float? = null, carAgeMs: Long = 0, sound: SoundMode = SoundMode.ALL, beepSetting: Boolean = false): List<String> {
        SystemClock.now = now
        settings.sound = sound; settings.beepAlerts = beepSetting
        CarBridge.set(car,carAgeMs)
        voice.calls.clear(); voice.lastPolicyRepeat = false
        val fix = if (ageMs == null) 0L else (now-ageMs)*1_000_000
        warnIfSpeeding(Location(provider,speed ?: 0f,speed != null,fix),limit)
        return voice.calls.toList()
    }
    /** Immediate measured-speed warning, repeated each minute; limits come from shownLimit. */
    private fun warnIfSpeeding(loc: Location, limitKmh: Double?) {
        val now = SystemClock.elapsedRealtime()
        val nowNanos = SystemClock.elapsedRealtimeNanos()
        val fixAgeMs = loc.elapsedRealtimeNanos.takeIf { it > 0 && it <= nowNanos }
            ?.let { (nowNanos - it) / 1_000_000 }
        val measured = com.drivemate.app.ui.PositionKind.of(loc.provider) == com.drivemate.app.ui.PositionKind.MEASURED
        val gpsSpeed = loc.speed.takeIf { measured && loc.hasSpeed() && fixAgeMs != null && fixAgeMs in 0..5_000 && it.isFinite() && it >= 0f }
        // CarBridge independently expires measured Android Auto speed after five seconds.
        val speed = CarBridge.carSpeed?.takeIf { it.isFinite() && it >= 0f } ?: gpsSpeed
        val sound = when (settings.sound) {
            SoundMode.ALL -> com.drivemate.app.nav.SpeedingWarningPolicy.Sound.FULL
            SoundMode.ALERTS -> com.drivemate.app.nav.SpeedingWarningPolicy.Sound.ALERTS
            SoundMode.OFF -> com.drivemate.app.nav.SpeedingWarningPolicy.Sound.MUTED
        }
        val result = speedingWarningPolicy.update(now, speed?.toDouble(), limitKmh,
            measured = speed != null, sound = sound)
        when (result.signal) {
            com.drivemate.app.nav.SpeedingWarningPolicy.Signal.SPEECH ->
                voice.say("Speed limit ${format.limit(limitKmh!!)}", alert = true, policyRepeat = true)
            com.drivemate.app.nav.SpeedingWarningPolicy.Signal.TONE -> voice.beep(urgent = true)
            com.drivemate.app.nav.SpeedingWarningPolicy.Signal.NONE -> Unit
        }
    }

}
