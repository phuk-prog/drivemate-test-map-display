package com.drivemate.app.nav

/** User preferences 09.03/09.04: immediate warning, then each continuous 60 seconds.
 * Input must contain a currently applicable trusted limit and recent measured speed.
 * This policy cannot establish road-limit truth or GPS freshness by itself.
 */
class SpeedingWarningPolicy {
    enum class Sound { FULL, ALERTS, MUTED }
    enum class Signal { NONE, SPEECH, TONE }
    data class Result(val over: Boolean, val signal: Signal)
    private var lastAudioAt: Long? = null
    private var previousTime: Long? = null
    fun update(nowMs: Long, speedMps: Double?, limitKmh: Double?, measured: Boolean, sound: Sound): Result {
        val valid = nowMs >= 0 && measured && speedMps != null && speedMps.isFinite() && speedMps >= 0 &&
            limitKmh != null && limitKmh.isFinite() && limitKmh > 0
        if (!valid) { lastAudioAt = null; previousTime = null; return Result(false, Signal.NONE) }
        if (previousTime?.let { nowMs < it } == true) lastAudioAt = null
        previousTime = nowMs
        val over = speedMps!! * 3.6 > limitKmh!!
        if (!over) { lastAudioAt = null; return Result(false, Signal.NONE) }
        if (sound == Sound.MUTED) return Result(true, Signal.NONE)
        val last = lastAudioAt
        if (last != null && nowMs - last < 60_000) return Result(true, Signal.NONE)
        lastAudioAt = nowMs
        return Result(true, if (sound == Sound.FULL) Signal.SPEECH else Signal.TONE)
    }
}
