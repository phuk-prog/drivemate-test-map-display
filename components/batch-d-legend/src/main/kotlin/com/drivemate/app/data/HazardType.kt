package com.drivemate.app.data

enum class HazardType(
    val emoji: String,
    val label: String,
    /** What the voice says, followed by "ahead". */
    val spoken: String,
    val color: String,
    val letter: String,
    val ttlMinutes: Int,
    /** Shown in the report menu (things drivers report); the rest come from map data. */
    val reportable: Boolean = true,
    /** Drawn as a dot on the map. */
    val onMap: Boolean = true,
    /** Only warn while navigating a route (too noisy otherwise). */
    val navOnly: Boolean = false,
    /** How far ahead to warn, in metres. */
    val alertRange: Int = 600,
    /** Only warn above this speed (m/s), e.g. speed bumps don't matter when crawling. */
    val minSpeed: Float = 0f,
) {
    // Reported by drivers
    POLICE("👮", "Police", "Police", "#3730A3", "P", 60),
    CRASH("💥", "Crash", "Crash", "#D93025", "C", 120),
    HAZARD("⚠️", "Hazard", "Hazard on the road", "#F29900", "!", 120),
    TRAFFIC("🚗", "Traffic jam", "Traffic jam", "#B3261E", "T", 45),
    CAMERA("📷", "Speed camera", "Speed camera", "#5F6368", "S", 60 * 24 * 30),
    CLOSURE("⛔", "Road closed", "Road closure", "#202124", "X", 60 * 12),
    POTHOLE("🕳️", "Pothole", "Pothole", "#795548", "O", 60 * 24 * 7, alertRange = 300),
    OBJECT("📦", "Object on road", "Object on the road", "#F29900", "!", 90, alertRange = 400),
    BROKEN_CAR("🚙", "Broken-down car", "Broken-down vehicle", "#F29900", "V", 90, alertRange = 400),
    WEATHER("🌫️", "Fog / ice", "Bad weather", "#5E97F6", "W", 180),
    ANIMALS("🦌", "Animals", "Animals on the road", "#0B8043", "N", 60, alertRange = 400),

    // From OpenStreetMap
    TOLL_BOOTH("💷", "Toll booth", "Toll booth", "#7B1FA2", "", 0, reportable = false, navOnly = true, alertRange = 500),
    RED_LIGHT("🚦", "Red light camera", "Red light camera", "#D93025", "R", 0, reportable = false),
    AVERAGE_SPEED("⏱️", "Average speed check", "Average speed check", "#5F6368", "A", 0, reportable = false),
    LEVEL_CROSSING("🚂", "Level crossing", "Level crossing", "#B06000", "L", 0, reportable = false, alertRange = 400),
    SPEED_BUMP("〰️", "Speed bump", "Speed bump", "#F9AB00", "", 0, reportable = false, onMap = false, navOnly = true, alertRange = 120, minSpeed = 6f),
    SCHOOL("🏫", "School zone", "School zone", "#F9AB00", "", 0, reportable = false, onMap = false, navOnly = true, alertRange = 300, minSpeed = 6f),
    ANIMAL_SIGN("🦌", "Animal crossing", "Animal crossing", "#0B8043", "N", 0, reportable = false, navOnly = true, alertRange = 400, minSpeed = 9f),
    ROAD_SIGN_HAZARD("⚠️", "Hazard sign", "Hazard", "#F29900", "!", 0, reportable = false, navOnly = true, alertRange = 400, minSpeed = 6f),

    // Worked out from the route's shape
    SHARP_BEND("↩️", "Sharp bend", "Sharp bend", "#E37400", "", 0, reportable = false, onMap = false, navOnly = true, alertRange = 300, minSpeed = 12f),
    ;

    /** Cameras that can be confirmed or hidden with "Still there" / "Not there". */
    val isCamera get() = this == CAMERA || this == RED_LIGHT || this == AVERAGE_SPEED
}
