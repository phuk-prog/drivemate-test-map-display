package com.drivemate.app.data

import android.content.Context

/** Per-device consent. This preference is deliberately excluded from Android and portable backups. */
class LocalPrivacySettings(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** Journey history is opt-in on this device; restoring a backup cannot enable it. */
    fun journeyHistoryEnabled(): Boolean = preferences.getBoolean(KEY_JOURNEY_HISTORY, false)

    fun setJourneyHistoryEnabled(enabled: Boolean) {
        check(preferences.edit().putBoolean(KEY_JOURNEY_HISTORY, enabled).commit()) { "Journey history preference could not be saved" }
    }

    fun compareGpsProviders(): Boolean = preferences.getBoolean(KEY_COMPARE_GPS_PROVIDERS, false)

    fun setCompareGpsProviders(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_COMPARE_GPS_PROVIDERS, enabled).apply()
    }

    private companion object {
        const val KEY_JOURNEY_HISTORY = "journeyHistoryEnabled"
        const val FILE_NAME = "drivemate_local_privacy"
        const val KEY_COMPARE_GPS_PROVIDERS = "compareGpsProviders"
    }
}
