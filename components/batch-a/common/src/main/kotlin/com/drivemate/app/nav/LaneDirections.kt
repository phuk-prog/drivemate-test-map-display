package com.drivemate.app.nav

/** Explicit lane arrows only. Unknown markings and merge permissions are not through arrows. */
internal object LaneDirections {
    fun angle(direction: String): Float? = when (direction.replace('_', ' ')) {
        "sharp left" -> -135f
        "left" -> -90f
        "slight left" -> -45f
        "straight", "through" -> 0f
        "slight right" -> 45f
        "right" -> 90f
        "sharp right" -> 135f
        "uturn", "reverse" -> 180f
        else -> null
    }
    fun fromOsm(value: String): String = when (value) {
        "through" -> "straight"
        "slight_left" -> "slight left"
        "sharp_left" -> "sharp left"
        "slight_right" -> "slight right"
        "sharp_right" -> "sharp right"
        "left", "right" -> value
        "reverse" -> "uturn"
        "merge_to_left" -> "merge left"
        "merge_to_right" -> "merge right"
        else -> "unknown"
    }
}
