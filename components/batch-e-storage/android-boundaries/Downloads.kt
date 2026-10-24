package com.drivemate.app.data
import java.io.File
import android.content.Context
import com.drivemate.app.nav.LatLon
object MapData {
 data class Asset(val sha256:String,val bytes:Long)
 data class Download(val asset:Asset,val url:String)
 fun init(ctx:Context) {}
 fun assetDownload(n:String):Download?=null
 fun downloadWeekly(n:String,f:File) { throw java.io.IOException("Synthetic unavailable publisher") }
 fun freshness(n:String,f:File):Boolean?=null
 fun mapArchiveSha256():String?=null
}
class RegionMapCatalog(val packages:List<Pack>,val sourceSha256:String="a".repeat(64)) {
 data class Pack(val name:String,val bytes:Long,val sha256:String)
}
class RegionalMapManifest(val catalog:RegionMapCatalog,val bounds:Map<String,DoubleArray> = emptyMap(),
 val companions:Map<String,Set<String>> = emptyMap(),val nationalFiles:List<String> = emptyList(),val builtDate:String?=null) {
 companion object {
  fun parse(t:String,requirePublished:Boolean=true):RegionalMapManifest { throw java.io.IOException("Synthetic unavailable manifest") }
  fun companionTarget(f:File,n:String):File?=null
  fun assetName(n:String)=n+".pmtiles"
 }
}
object RegionDownloadPlanner {
 data class Plan(val packages:List<String>,val companions:List<String>,val downloadBytes:Long=0,val uncoveredPoints:Int=0)
 fun around(m:RegionalMapManifest,lat:Double,lon:Double,radius:Double)=Plan(emptyList(),emptyList())
}
class VerifiedRegionalMapStore(val dir:File,val catalog:RegionMapCatalog) {
 enum class InstallPhase { ACTIVATING }
 fun matchesCatalog(c:RegionMapCatalog)=true
 fun verifiedFileForPackage(n:String,checkpoint:()->Unit = {}):File?=null
 fun expectedFileName(n:String)=n+".pmtiles"
 fun installCandidate(n:String,f:File,checkpoint:()->Unit,phase:(InstallPhase)->Unit) {checkpoint();phase(InstallPhase.ACTIVATING)}
}
object RegionalLiveMap {
 const val GENERATION_MANIFEST="manifest.json"
 var inUseGeneration:String?=null
 class Installed(val generation:String,val verified:Set<String>,val catalog:RegionMapCatalog)
 fun scan(root:File,excludedGeneration:String?=null):Installed?=null
 fun retainedGenerations(k:String,p:String?,i:String?)=setOfNotNull(k,p,i)
}
object WholeUk { fun <T> userDownload(block:()->T):T=block() }
object OwnMap { fun activeSnapshotTag():String?=null;fun detailGenerationNote():String?=null }
object MapPack { fun installed():File?=null }
object DataGenerations {
 fun note(b:Boolean):String?=null
 fun areaNote(b:Boolean):String?=null
 fun dmnMatchesMap(a:String?,b:String?)=true
 fun areaMapsMatchMap(a:String?,b:String?)=true
}
