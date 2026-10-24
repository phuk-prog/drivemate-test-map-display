package com.drivemate.app.data

import android.content.Context

/** Per-device consent. This preference is deliberately excluded from Android and portable backups. */
class LocalPrivacySettings(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun compareGpsProviders(): Boolean = preferences.getBoolean(KEY_COMPARE_GPS_PROVIDERS, false)

    fun setCompareGpsProviders(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_COMPARE_GPS_PROVIDERS, enabled).apply()
    }

    private companion object {
        const val FILE_NAME = "drivemate_local_privacy"
        const val KEY_COMPARE_GPS_PROVIDERS = "compareGpsProviders"
    }
}
