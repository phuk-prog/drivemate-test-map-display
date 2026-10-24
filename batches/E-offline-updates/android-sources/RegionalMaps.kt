package com.drivemate.app.data

import android.content.Context
import kotlinx.coroutines.awaitCancellation
import java.util.concurrent.CountDownLatch

/** Explicit boundary fake: cancellation is real coroutine cancellation, not a real HTTP transfer. */
object RegionalMaps {
    @Volatile var waitForCancellation = false
    var started = CountDownLatch(1)
    var stopped = CountDownLatch(1)
    fun reset() { waitForCancellation = false; started = CountDownLatch(1); stopped = CountDownLatch(1) }
    suspend fun updateApproved(ctx: Context, approved: () -> Boolean) {
        check(approved())
        started.countDown()
        try { if (waitForCancellation) awaitCancellation() } finally { stopped.countDown() }
    }
}
