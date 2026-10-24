package com.drivemate.app.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Explicit global approval for Wi-Fi updates. Android persists the periodic work and waits for
 * unmetered connectivity; no coordinates or provider credentials enter WorkManager input data.
 * Manual downloads use a different unique name and are unaffected by revoking this approval.
 */
object OfflineAutoUpdates {
    private const val WORK = "offline-map-approved-updates"
    private const val REVISION = "approval_revision"
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("offline_auto_updates", Context.MODE_PRIVATE)
    private fun approval(ctx: Context) = prefs(ctx).let {
        OfflineUpdatePolicy.Approval(it.getBoolean("enabled", false), it.getLong(REVISION, 0L))
    }
    fun enabled(ctx: Context): Boolean = approval(ctx).enabled

    /** No toast, cancellation log, or confirmation: the switch is the feedback. */
    @Synchronized fun setEnabled(ctx: Context, enabled: Boolean): Boolean {
        val previous = approval(ctx)
        if (previous.enabled == enabled) return true
        val revision = previous.revision + 1L
        // Persist revocation before cancellation: a worker already starting must fail its guard.
        if (!prefs(ctx).edit().putBoolean("enabled", enabled).putLong(REVISION, revision).commit()) return false
        val manager = WorkManager.getInstance(ctx)
        if (!enabled) {
            manager.cancelUniqueWork(WORK)
        } else {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresStorageNotLow(true).build()
            val request = PeriodicWorkRequestBuilder<Worker>(24, TimeUnit.HOURS)
                .setConstraints(constraints).setInputData(workDataOf(REVISION to revision)).build()
            manager.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request)
        }
        return true
    }

    class Worker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
        override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
            val scheduledRevision = inputData.getLong(REVISION, -1L)
            fun approved() = OfflineUpdatePolicy.permits(approval(applicationContext), scheduledRevision)
            if (!approved()) return@withContext Result.success()
            try {
                RegionalMaps.updateApproved(applicationContext, ::approved)
                Result.success()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // This update attempt does not create an immediate automatic retry loop.
                Result.failure()
            }
        }
    }
}
