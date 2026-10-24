package com.drivemate.app.data
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.io.IOException
import java.nio.file.Files
class DownloadStoragePreferencesTest {
 @Test fun pref12_25_failedTransferWaitsForUser() {
  val tmp=Files.createTempDirectory("synthetic-download").toFile()
  try {
   var calls=0
   val catalog=RegionMapCatalog(listOf(RegionMapCatalog.Pack("sample",100,"0".repeat(64))))
   val result=RegionalMapDownloader(VerifiedRegionalMapStore(),catalog,tmp,{ "https://invalid.example/synthetic" },
    fetch={_,_,_-> calls++; throw IOException("synthetic disconnected")},companionTarget={null})
    .download(RegionDownloadPlanner.Plan(listOf("sample"),emptyList()))
   assertEquals("A failed transfer must wait for explicit Retry",1,calls)
   assertTrue(result.failed.containsKey("sample"))
  } finally { tmp.deleteRecursively() }
 }
 @Test fun pref12_28_failedItemHasNoReason() {
  val text=RegionalDownloadText.line(RegionalDownloadStage(RegionalDownloadStage.Step.FAILED,note="synthetic internal HTTP error"))
  assertFalse(text,text.contains("synthetic internal HTTP error"))
 }
 @Test fun pref12_21_progressUsesPercentage() {
  val text=RegionalDownloadText.line(RegionalDownloadStage(RegionalDownloadStage.Step.DOWNLOADING,doneBytes=25,totalBytes=100))
  assertTrue(text,text.contains("25%")); assertFalse(text,text.contains("remaining",true))
 }
 @Test fun pref12_02_exactFreeStorageBoundary() {
  val n=OfflineStorage.spaceNeeded(listOf(100L to 20L),reserveBytes=10)
  assertEquals(190L,n.totalBytes)
  assertTrue(OfflineStorage.enoughSpace(n,190)); assertFalse(OfflineStorage.enoughSpace(n,189))
 }
 @Test fun pref12_02_savedBytesBounded() {
  assertEquals(110L,OfflineStorage.spaceNeeded(listOf(100L to 1000L),10).totalBytes)
  assertEquals(210L,OfflineStorage.spaceNeeded(listOf(100L to -1L),10).totalBytes)
 }
 @Test fun pref12_11_storageShortfallVisible() {
  val t=OfflineStorage.spaceText(OfflineStorage.SpaceNeed(2_000_000,1_000_000,0),1_000_000)
  assertTrue(t,t.contains("Free up 2 MB first"))
 }
 @Test fun cleanupProtectsPrivateUserData() {
  val root=Files.createTempDirectory("synthetic-storage").toFile()
  try {
   val files=File(root,"files").apply{mkdirs()}; val nb=File(root,"nb").apply{mkdirs()}
   val layout=OfflineStorage.Layout(files,nb,root)
   File(root,"shared_prefs").mkdirs(); val data=File(root,"shared_prefs/synthetic").apply{writeText("synthetic")}
   try { OfflineStorage.clean(layout,OfflineStorage.Kind.PRIVATE,OfflineStorage.Protection()); fail("Private deletion allowed") } catch(_:IllegalStateException) {}
   assertTrue(data.isFile)
  } finally {root.deleteRecursively()}
 }
 @Test fun cleanupNeverDeletesActiveGeneration() {
  val root=Files.createTempDirectory("synthetic-active").toFile()
  try {
   val layout=OfflineStorage.Layout(root,File(root,"nb"),root)
   val generation="a".repeat(64); val f=File(root,"regions/"+generation+"/map").apply{parentFile.mkdirs();writeText("synthetic")}
   try { OfflineStorage.clean(layout,OfflineStorage.Kind.AREA_MAPS,OfflineStorage.Protection(currentGeneration=generation,inUseGeneration=generation)); fail("In-use deletion allowed") } catch(_:IllegalStateException) {}
   assertTrue(f.isFile)
  } finally {root.deleteRecursively()}
 }
}
