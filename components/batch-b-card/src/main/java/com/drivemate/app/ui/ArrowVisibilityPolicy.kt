package com.drivemate.app.ui

/** Presentation timing only; route instructions remain visible at every distance.
 * Reveal 15 seconds ahead, bounded to 120–600 metres. Missing/stale speed uses 120 m.
 * Keep the arrow throughout a roundabout passage. This never proves road geometry.
 */
internal fun showTurnArrow(arrived: Boolean, stepType: String?, distance: Double,
                           onRoundabout: Boolean, speed: Float?, fixAgeMs: Long): Boolean {
    if (arrived || stepType == null) return false
    if (onRoundabout) return true
    if (!distance.isFinite() || distance < 0.0) return false
    val approach = ((measuredSpeed(speed, fixAgeMs) ?: 0f).toDouble() * 15.0).coerceIn(120.0, 600.0)
    return distance <= approach
}
