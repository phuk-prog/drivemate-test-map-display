package com.drivemate.app.controller
import com.drivemate.app.Mode
import com.drivemate.app.nav.LatLon
class NavigationSession {
 var mode=Mode.IDLE
 var routes=listOf<Route>()
 var selectedRoute=0
 data class Route(val points:List<LatLon>)
}
