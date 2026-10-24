package com.drivemate.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfflineAutoUpdatesAndroidTest {
    private lateinit var ctx: Context
    private lateinit var manager: WorkManager
    @Before fun prepare() {
        ctx = ApplicationProvider.getApplicationContext()
        ctx.getSharedPreferences("offline_auto_updates", Context.MODE_PRIVATE).edit().clear().commit()
        WorkManagerTestInitHelper.initializeTestWorkManager(ctx,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        manager = WorkManager.getInstance(ctx)
    }
    private fun work() = manager.getWorkInfosForUniqueWork("offline-map-approved-updates").get()

    @Test fun defaultApprovalOffCreatesNoWork() {
        assertFalse(OfflineAutoUpdates.enabled(ctx)); assertTrue(work().isEmpty())
    }
    @Test fun optInPersistsAndQueuesUnmeteredWorker() {
        assertTrue(OfflineAutoUpdates.setEnabled(ctx, true))
        assertTrue(OfflineAutoUpdates.enabled(ctx))
        val queued = work().single()
        assertEquals(NetworkType.UNMETERED, queued.constraints.requiredNetworkType)
        assertFalse(queued.state.isFinished)
    }
    @Test fun revokeCancelsQueuedAutomaticWork() {
        OfflineAutoUpdates.setEnabled(ctx, true)
        OfflineAutoUpdates.setEnabled(ctx, false)
        assertFalse(OfflineAutoUpdates.enabled(ctx))
        assertTrue(work().all { it.state == WorkInfo.State.CANCELLED })
    }
    @Test fun revokeAndReapproveChangesRevision() {
        OfflineAutoUpdates.setEnabled(ctx, true)
        val first = ctx.getSharedPreferences("offline_auto_updates", 0).getLong("approval_revision", -1)
        OfflineAutoUpdates.setEnabled(ctx, false)
        OfflineAutoUpdates.setEnabled(ctx, true)
        val next = ctx.getSharedPreferences("offline_auto_updates", 0).getLong("approval_revision", -1)
        assertTrue(next > first)
        assertFalse(OfflineUpdatePolicy.permits(OfflineUpdatePolicy.Approval(true, next), first))
    }
    @Test fun redundantToggleDoesNotReplaceWorker() {
        OfflineAutoUpdates.setEnabled(ctx, true)
        val id = work().single().id
        OfflineAutoUpdates.setEnabled(ctx, true)
        assertEquals(id, work().single().id)
    }
}
