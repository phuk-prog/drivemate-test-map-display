package com.drivemate.app.data

import android.content.Context

/** Explicit boundary fake: these tests do not download or install maps. */
object RegionalMaps {
    suspend fun updateApproved(ctx: Context, approved: () -> Boolean) { check(approved()) }
}
