package com.drivemate.app.data
import java.io.File
object RegionalLiveMap { const val GENERATION_MANIFEST = "installed-manifest.json" }
object MapData { fun downloadWeekly(name:String,dest:File) { error("Unused synthetic boundary") } }
class RegionMapCatalog(val packages:List<Pack>) { data class Pack(val name:String,val bytes:Long,val sha256:String) }
object RegionDownloadPlanner { data class Plan(val packages:List<String>,val companions:List<String>) }
class VerifiedRegionalMapStore {
 enum class InstallPhase { ACTIVATING }
 fun matchesCatalog(c:RegionMapCatalog)=true
 fun verifiedFileForPackage(n:String, checkpoint:()->Unit):File?=null
 fun expectedFileName(n:String)=n+".pmtiles"
 fun installCandidate(n:String,f:File,checkpoint:()->Unit,phase:(InstallPhase)->Unit) { checkpoint(); phase(InstallPhase.ACTIVATING) }
}
