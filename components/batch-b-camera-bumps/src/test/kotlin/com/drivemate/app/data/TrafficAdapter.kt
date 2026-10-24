package com.drivemate.app.data
import com.drivemate.app.controller.Hazard
/** No provider expiry behavior is claimed by these tests. */
internal object TrafficDetails { fun stale(h: Hazard,now: Long) = false }
