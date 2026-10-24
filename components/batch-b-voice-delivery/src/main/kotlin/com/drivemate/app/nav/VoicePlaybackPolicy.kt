package com.drivemate.app.nav

/** A playing sentence may finish after a mode change; queued/preparing speech may not start. */
internal enum class VoiceSoundState { FULL, ALERTS, MUTED }
internal object VoicePlaybackPolicy {
    /** Delegated main-alert defaults; unknown/minor hazards never become alert tones. */
    fun mainHazard(kind: String): Boolean = kind in setOf("CAMERA", "RED_LIGHT", "AVERAGE_SPEED", "CRASH", "CLOSURE", "OBJECT", "BROKEN_CAR", "ANIMALS", "HAZARD")
    fun mainRoadEvent(kind: String): Boolean = kind in setOf("ROAD_CLOSED", "LANES_CLOSED")
    fun canStart(state: VoiceSoundState, tone: Boolean): Boolean =
        state == VoiceSoundState.FULL || (state == VoiceSoundState.ALERTS && tone)
    fun mayKeepCurrent(state: VoiceSoundState, tone: Boolean, speechStarted: Boolean): Boolean =
        speechStarted || canStart(state, tone)
}
