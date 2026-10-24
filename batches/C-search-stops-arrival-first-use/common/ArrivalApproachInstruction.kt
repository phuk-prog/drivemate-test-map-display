package com.drivemate.app.nav

/** Shared text and voice wording before the measured arrival gate completes. */
internal fun arrivalApproachInstruction(type: String?, modifier: String?, arrived: Boolean = false): String? {
    if (arrived || type !in setOf("arrive", "stop")) return null
    val place = if (type == "stop") "Stop" else "Destination"
    val side = when (modifier) {
        "left" -> "on the left"
        "right" -> "on the right"
        else -> "ahead"
    }
    return "$place $side"
}
