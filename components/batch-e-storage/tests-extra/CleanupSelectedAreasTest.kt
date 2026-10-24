package com.drivemate.app.data
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.nio.file.Files
class CleanupSelectedAreasTest {
 private fun fixture(block:(File,List<OfflineAreaCleanup.Area>)->Unit) {
  val dir=Files.createTempDirectory("synthetic-selected-areas").toFile()
  try {
   val areas=(1..3).map { i -> OfflineAreaCleanup.Area("region-z8-x"+i+"-y1","Synthetic "+i,
    File(dir,"synthetic-"+i+".pmtiles").apply{writeBytes(ByteArray(i*10))},i.toLong(),i==2) }
   block(dir,areas)
  } finally { dir.deleteRecursively() }
 }
 @Test fun pref12_06_07_selectedUniqueAreasFreeExactRunningTotal()=fixture { _,a ->
  assertEquals(0L,OfflineAreaCleanup.selectedBytes(a,emptySet()))
  assertEquals(40L,OfflineAreaCleanup.selectedBytes(a,setOf(a[0].id,a[2].id)))
  assertEquals(40L,OfflineAreaCleanup.selectedBytes(a+a[0],setOf(a[0].id,a[2].id)))
 }
 @Test fun pref12_11_missingStorageClampsAtZero() {
  assertEquals(30L,OfflineAreaCleanup.missingBytes(100,50,20))
  assertEquals(0L,OfflineAreaCleanup.missingBytes(100,50,100))
 }
 @Test fun pref12_08_routeCrossingWarnsEvenWithOutsideSamples() {
  val bounds=doubleArrayOf(-1.0,-1.0,1.0,1.0)
  assertTrue(OfflineAreaCleanup.affectsRoute(bounds,listOf(0.0 to -2.0,0.0 to 2.0)))
  assertFalse(OfflineAreaCleanup.affectsRoute(bounds,listOf(5.0 to 3.0,6.0 to 4.0)))
 }
 @Test fun pref12_09_routeRemovalRequiresExplicitConfirmation()=fixture { dir,a ->
  try {OfflineAreaCleanup.remove(dir,a,setOf(a[1].id),false,false);fail("Confirmation omitted")}catch(_:IllegalStateException){}
  assertTrue(a.all{it.file.isFile})
  assertEquals(20L,OfflineAreaCleanup.remove(dir,a,setOf(a[1].id),false,true))
  assertFalse(a[1].file.exists());assertTrue(a[0].file.isFile);assertTrue(a[2].file.isFile)
 }
 @Test fun protectedOrChangedSelectionDeletesNothing()=fixture { dir,a ->
  try {OfflineAreaCleanup.remove(dir,a,setOf(a[0].id),true,true);fail("Protection omitted")}catch(_:IllegalStateException){}
  try {OfflineAreaCleanup.remove(dir,a,setOf(a[0].id,"unknown"),false,true);fail("Stale selection accepted")}catch(_:IllegalArgumentException){}
  assertTrue(a.all{it.file.isFile})
 }
 @Test fun outsideInstalledFolderIsNeverDeleted()=fixture { dir,a ->
  val outside=Files.createTempFile("synthetic-private",".txt").toFile()
  try {
   val bad=a[0].copy(file=outside)
   try {OfflineAreaCleanup.remove(dir,listOf(bad),setOf(bad.id),false,true);fail("Path boundary omitted")}catch(_:IllegalStateException){}
   assertTrue(outside.isFile)
  } finally {outside.delete()}
 }
}
