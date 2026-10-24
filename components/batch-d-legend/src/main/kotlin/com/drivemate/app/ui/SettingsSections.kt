package com.drivemate.app.ui

import java.text.Normalizer
import java.util.Locale

/** One searchable directory for the existing preferences; discovery never changes a preference. */
enum class SettingsSection(val title: String, val description: String, private val searchTerms: String) {
    MAP("Map appearance", "Day/night theme, map source, buildings and vehicle marker.",
        "style colours colors dark light 3d flat car icon pictures attribution"),
    DRIVING("Driving view", "Perspective, automatic zoom and battery saver.",
        "tilt camera orientation follow zoom power screen dim"),
    LANES("Lane and junction guidance", "Junction views and saved lane corrections.",
        "immersive roundabout lanes road markings correction review"),
    ROUTES("Routes", "Avoidances, shortcuts and faster-route preferences.",
        "motorways tolls ferries simpler rerouting alternative rat runs"),
    TRAFFIC("Traffic", "Live traffic keys, motorway information and street works.",
        "tomtom national highways incidents closures roadworks signs red x subscription api"),
    MUSIC("Voice and music", "Spoken instructions, audio output and speed warnings.",
        "sound speak bluetooth stereo pause volume timing speeding camera alerts beep spotify"),
    OFFLINE("Offline maps", "Nationwide and regional map downloads and road matching with no signal.",
        "wifi wi fi storage package fonts symbols no signal greater manchester offline routing routes"),
    PLACES("Saved places and contacts", "Home, work, favourites and parked-car location.",
        "favorites addresses phone contact destination forget parking"),
    OPENSTREETMAP("OpenStreetMap", "Connect your account for one-tap map fixes and notes; live map or test server.",
        "osm account connect sign in edits notes changeset test server client id oauth fix map editor"),
    REMINDERS("Reminders", "Leave-by reminders and weekday traffic checks before usual trips.",
        "commute departure leave by alarm morning check notification schedule cancel"),
    PRIVACY("Privacy and post-drive review", "Location disclosure, trip history and retention.",
        "gps providers recording journeys delete data consent observation learning"),
    SYMBOLS("Warning symbols", "A simple guide to the warning symbols shown on the map.",
        "legend icon icons camera cameras hazard hazards signs"),
    ACCESSIBILITY("Accessibility", "Readable controls and your phone's accessibility options.",
        "text font size contrast screen reader talkback display"),
    BACKUP("Backup and recovery", "Export and restore a portable DriveMate backup.",
        "account google drive files usb save import restore recovery"),
    ABOUT("About and support", "App version, source credits and diagnostic sharing.",
        "help problems health reports attribution licence license diagnostics version");

    internal val searchableText: String get() = settingsSearchText("$title $description $searchTerms")
}

private fun settingsSearchText(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase(Locale.ROOT)
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
    .trim()

fun settingsSectionsMatching(query: String, navigating: Boolean = false): List<SettingsSection> {
    val words = settingsSearchText(query).split(' ').filter { it.isNotBlank() }
    return SettingsSection.entries.filter { section ->
        (section != SettingsSection.SYMBOLS || !navigating) && words.all { it in section.searchableText }
    }
}
