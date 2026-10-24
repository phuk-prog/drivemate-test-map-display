package com.drivemate.app.dmn
import java.io.File
import com.drivemate.app.nav.LatLon
object Dmn { fun available()=false }
object DmnTiles {
 enum class Offline { READY,PARTIAL,MISSING }
 fun offlineArea(f:File,b:DoubleArray)=Offline.MISSING
 fun offline(f:File,p:List<LatLon>)=Offline.MISSING
 fun localSource(f:File):String?=null
}
