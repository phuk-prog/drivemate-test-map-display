package com.drivemate.app.data
import com.drivemate.app.nav.LatLon
// Minimal synthetic boundaries. These are not app model/provider implementations.
data class Place(val name:String,val address:String,val pos:LatLon)
data class RouteStep(val type:String,val modifier:String)
object OwnPlaces { fun ofCategories(k:Set<String>,a:Double,b:Double,c:Double,d:Double):List<Place> = error("Synthetic places must be injected") }
