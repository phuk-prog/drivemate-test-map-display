package com.drivemate.app.dmn
import com.drivemate.app.nav.LatLon
object Dmn { data class Route(val duration:Double); fun route(p:List<LatLon>, h:Float):List<Route> = error("Synthetic road timings must be injected") }
