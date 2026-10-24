package com.drivemate.app.data
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.ListenableWorker
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class RegionalMapsAndroidTest {
 private lateinit var ctx:Context
 private lateinit var manager:WorkManager
 @Before fun setup() {
  ctx=ApplicationProvider.getApplicationContext()
  ctx.getSharedPreferences("regional_maps",0).edit().clear().commit()
  WorkManagerTestInitHelper.initializeTestWorkManager(ctx,
   Configuration.Builder().setExecutor(SynchronousExecutor()).build())
  manager=WorkManager.getInstance(ctx)
 }
 private fun prefs()=ctx.getSharedPreferences("regional_maps",0)
 @Test fun manualRequestDefaultsToUnmeteredAndPersistsSelection() {
  RegionalMaps.requestArea(ctx,53.4,-2.2,25.0)
  val work=manager.getWorkInfosForUniqueWork("regional-map-area").get().last()
  assertEquals(NetworkType.UNMETERED,work.constraints.requiredNetworkType)
  assertTrue(RegionalMaps.canResume(ctx))
 }
 @Test fun approvedMobileQueueHasNoDailyCap() {
  RegionalMaps.setMobileDownloadsAllowed(ctx,true)
  RegionalMaps.requestArea(ctx,53.4,-2.2,25.0)
  val work=manager.getWorkInfosForUniqueWork("regional-map-area").get().last()
  assertEquals(NetworkType.CONNECTED,work.constraints.requiredNetworkType)
  assertTrue(RegionalMaps.mobileDownloadsAllowed(ctx))
 }
 @Test fun failedWorkerReturnsFailureAndPersistsItem()=runBlocking {
  prefs().edit().putFloat("lat",53.4f).commit()
  val worker=TestListenableWorkerBuilder<RegionalMaps.Worker>(ctx).build()
  assertEquals(ListenableWorker.Result.failure(),worker.doWork())
  assertTrue(prefs().contains("failed_since"))
  assertEquals(RegionalDownloadStage.Step.FAILED,RegionalMaps.status.value.stage?.step)
 }
 @Test fun retryResetsContinuousFailureAge() {
  prefs().edit().putFloat("lat",53.4f).putLong("failed_since",123).commit()
  RegionalMaps.resume(ctx)
  assertFalse(prefs().contains("failed_since"))
  assertEquals(RegionalDownloadStage.Step.WAITING,RegionalMaps.status.value.stage?.step)
 }
 @Test fun failedItemSurvivesUntilTwoDaysThenClears() {
  val now=System.currentTimeMillis()
  prefs().edit().putFloat("lat",53.4f).putLong("failed_since",now-47L*60*60*1000).commit()
  RegionalMaps.refresh(ctx)
  assertEquals(RegionalDownloadStage.Step.FAILED,RegionalMaps.status.value.stage?.step)
  prefs().edit().putLong("failed_since",now-49L*60*60*1000).commit()
  RegionalMaps.refresh(ctx)
  assertNull(RegionalMaps.status.value.stage)
  assertFalse(prefs().contains("failed_since"))
 }
 @Test fun revokedApprovalDoesNotEnterDownload()=runBlocking {
  prefs().edit().putFloat("lat",53.4f).commit()
  RegionalMaps.updateApproved(ctx) {false}
  assertFalse(prefs().contains("failed_since"));assertFalse(RegionalMaps.downloadRunning)
 }
 @Test fun automaticFailureAlsoWaitsForUserRetry()=runBlocking {
  prefs().edit().putFloat("lat",53.4f).commit()
  try {RegionalMaps.updateApproved(ctx) {true};fail("Expected unavailable publisher")}catch(_:java.io.IOException){}
  assertTrue(prefs().contains("failed_since"));assertFalse(RegionalMaps.downloadRunning)
 }
}
