package com.drivemate.app.data
import com.drivemate.app.nav.LatLon
// Minimal synthetic boundaries. These are not app model/provider implementations.
data class Place(val name:String,val address:String,val pos:LatLon,val keywords:String="",val kind:String="")
data class RouteStep(val type:String,val modifier:String)
object OwnPlaces { fun search(q:String,p:LatLon):List<Place> = emptyList(); fun ofCategories(k:Set<String>,a:Double,b:Double,c:Double,d:Double):List<Place> = error("Synthetic places must be injected") }
