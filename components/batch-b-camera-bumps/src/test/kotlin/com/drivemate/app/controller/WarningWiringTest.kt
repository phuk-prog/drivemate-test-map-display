package com.drivemate.app.controller
import com.drivemate.app.data.HazardType
import com.drivemate.app.nav.CameraApproachPolicy as P
import com.drivemate.app.nav.LatLon
import org.junit.Assert.*
import org.junit.Test

class WarningWiringTest {
    private val h = WarningWiringHarness()
    private fun camera(limit: Double? = 50.0) = Hazard(1, HazardType.CAMERA, LatLon(0.0, 1.0), limitKmh = limit)
    private fun bump(id: Long) = Hazard(id, HazardType.SPEED_BUMP, LatLon(0.0, id.toDouble()))
    @Test fun cameraPreparationDistanceScalesWithSpeed() { assertEquals(400.0,P.approachMetres(20.0),0.0);assertEquals(600.0,P.approachMetres(30.0),0.0) }
    @Test fun invalidSpeedsDoNotCreateLongCameraRanges() { for(s in listOf(Double.NaN,Double.POSITIVE_INFINITY,-1.0)) assertEquals(200.0,P.approachMetres(s),0.0) }
    @Test fun cameraTimingHasBoundedUrbanAndHighSpeedRanges() { assertEquals(200.0,P.approachMetres(0.0),0.0);assertEquals(800.0,P.approachMetres(100.0),0.0);assertEquals(250.0,P.extraMetres(100.0),0.0) }
    @Test fun ordinaryCameraUsesRouteDistanceInsteadOfStraightLineShortcut() { assertTrue(h.run(listOf(camera()),mapOf(1L to 401.0)).isEmpty()) }
    @Test fun standardCameraWarnsAtExactApproachBoundary() { assertEquals(listOf("speech:Speed camera ahead, limit 50:true"),h.run(listOf(camera()),mapOf(1L to 400.0))) }
    @Test fun cameraBehindDriverDoesNotWarn() { assertTrue(h.run(listOf(camera()),mapOf(1L to -1.0)).isEmpty()) }
    @Test fun unmatchedParallelRoadCameraDoesNotWarn() { assertTrue(h.run(listOf(camera()),emptyMap()).isEmpty()) }
    @Test fun unreliablePositionSuppressesRouteCameraAudio() { assertTrue(h.run(listOf(camera()),mapOf(1L to 100.0),reliable=false).isEmpty()) }
    @Test fun fullCameraStillSpeaksWhenLegacyBeepOptionIsOn() { assertEquals(1,h.run(listOf(camera()),mapOf(1L to 100.0),beep=true).filter { it.startsWith("speech:") }.size) }
    @Test fun speedingGetsSeparateFinalApproachWarning() {
        assertEquals(1,h.run(listOf(camera()),mapOf(1L to 200.0)).size)
        assertEquals(listOf("speech:Speed camera ahead. You are over the limit:true"),h.run(listOf(camera()),mapOf(1L to 100.0)))
    }
    @Test fun additionalCameraWarningOnlyHappensOnce() {
        h.run(listOf(camera()),mapOf(1L to 200.0));h.run(listOf(camera()),mapOf(1L to 100.0))
        assertTrue(h.run(listOf(camera()),mapOf(1L to 80.0)).isEmpty())
    }
    @Test fun cameraUnderKnownLimitHasNoExtraWarning() {
        h.run(listOf(camera()),mapOf(1L to 200.0),speed=10f)
        assertTrue(h.run(listOf(camera()),mapOf(1L to 70.0),speed=10f).isEmpty())
    }
    @Test fun cameraWithUnknownLimitCannotClaimSpeeding() {
        h.run(listOf(camera(null)),mapOf(1L to 200.0))
        assertTrue(h.run(listOf(camera(null)),mapOf(1L to 50.0)).isEmpty())
    }
    @Test fun staleGpsCannotTriggerAdditionalSpeedingCameraWarning() {
        h.run(listOf(camera()),mapOf(1L to 200.0))
        assertTrue(h.run(listOf(camera()),mapOf(1L to 50.0),ageMs=5_001).isEmpty())
    }
    @Test fun estimatedSpeedCannotTriggerAdditionalSpeedingCameraWarning() {
        h.run(listOf(camera()),mapOf(1L to 200.0))
        assertTrue(h.run(listOf(camera()),mapOf(1L to 50.0),provider="estimate").isEmpty())
    }
    @Test fun futureGpsCannotTriggerAdditionalSpeedingCameraWarning() {
        h.run(listOf(camera()),mapOf(1L to 200.0))
        assertTrue(h.run(listOf(camera()),mapOf(1L to 50.0),ageMs=-1).isEmpty())
    }
    @Test fun twoNearbyBumpsAreGroupedOnce() {
        val items=listOf(bump(1),bump(2))
        assertEquals(listOf("speech:Several speed bumps ahead:false"),h.run(items,mapOf(1L to 60.0,2L to 160.0)))
        assertTrue(h.run(items,mapOf(1L to 30.0,2L to 130.0)).isEmpty())
    }
    @Test fun distantSecondBumpIsNotGrouped() { assertEquals(listOf("speech:Speed bump ahead:false"),h.run(listOf(bump(1),bump(2)),mapOf(1L to 60.0,2L to 211.0))) }
    @Test fun legacyBeepOptionDoesNotToneBumps() { assertEquals(listOf("speech:Several speed bumps ahead:false"),h.run(listOf(bump(1),bump(2)),mapOf(1L to 60.0,2L to 160.0),beep=true)) }
    @Test fun bumpTypeAllowsSeparateMapFeatures() { assertTrue(HazardType.SPEED_BUMP.onMap) }
}
