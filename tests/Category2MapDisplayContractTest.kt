package com.drivemate.app.map

import com.drivemate.app.nav.Geo
import com.drivemate.app.nav.LatLon
import com.drivemate.app.nav.RoadCentreline
import com.drivemate.app.nav.RouteGeometryAudit
import com.drivemate.app.nav.RouteGeometryIndex
import com.drivemate.app.nav.RoutePalette
import org.junit.Assert.*
import org.junit.Test

/** Public, synthetic Category 2 contracts compiled against the ACTUAL private app in private CI.
 * These are logic tests, never substitutes for screenshots, map matching or live traffic data.
 */
class Category2MapDisplayContractTest {
    @Test fun pref02_01_verifiedRoadGeometryHasLowDrift() {
        val route = listOf(LatLon(53.0, -2.0), LatLon(53.0, -1.999))
        val truth = listOf(RoadCentreline("same-carriageway", route, true))
        val result = RouteGeometryAudit.measure(route, truth)
        assertTrue(result.samples >= 2)
        assertEquals(0, result.unmatchedSamples)
        assertTrue(result.p95Metres!! < 0.5)
    }

    @Test fun pref02_01_unverifiedRoadCannotBeUsedAsProof() {
        val route = listOf(LatLon(53.0, -2.0), LatLon(53.0, -1.999))
        val unverified = listOf(RoadCentreline("unverified-parallel", route, false))
        val result = RouteGeometryAudit.measure(route, unverified)
        assertEquals(result.samples, result.unmatchedSamples)
        assertNull(result.p95Metres)
    }

    @Test fun pref02_02_paddedCameraLeavesMoreViewAhead() {
        val insets = DrivingCamera.ViewportInsets(top = 100, bottom = 140)
        val lowerCar = DrivingCamera.padding(1080, 1920, insets, 0.5)
        val ordinary = DrivingCamera.padding(1080, 1920, insets, 0.0)
        assertTrue(lowerCar[1] > ordinary[1])
        assertEquals(ordinary[3], lowerCar[3], 0.0)
    }

    @Test fun pref02_02_largePanelsCannotConsumeViewport() {
        val huge = DrivingCamera.ViewportInsets(top = 9000, bottom = 9000, left = 3000, right = 3000)
        val p = DrivingCamera.padding(1000, 1600, huge, 0.4)
        assertTrue(p[0] + p[2] <= 750.01)
        assertTrue(p[1] + p[3] <= 1600.01)
    }

    @Test fun pref02_04_cameraBearingWrapsShortWayAcrossNorth() {
        val policy = CameraHeading()
        val before = policy.update(1000, 359.0, 12.0)!!
        val after = policy.update(2000, 1.0, 12.0)!!
        assertTrue(Geo.angleDiff(before, after) < 5.0)
    }

    @Test fun pref02_04_carLayerIsIdentifiedBelowOwnOverlays() {
        val layers = listOf("background", "road-primary", "mapbox-location-foreground", "dm-route")
        assertEquals("mapbox-location-foreground", MapLayerOrder.firstCarLayer(layers))
        assertNull(MapLayerOrder.firstCarLayer(listOf("road-primary", "dm-route")))
    }

    @Test fun pref02_05_fastRoadHasWiderZoomThanSlowStreet() {
        val slow = AdaptiveZoom().update(0, 4.0, 4000.0, false, false)
        val fast = AdaptiveZoom().update(0, 30.0, 4000.0, false, false)
        assertTrue("higher speed should show more road", fast < slow)
    }

    @Test fun pref02_05_nearTurnRaisesZoomWithoutLosingTurnContext() {
        val straight = AdaptiveZoom().update(0, 5.0, 1200.0, true, false)
        val near = AdaptiveZoom().update(0, 5.0, 50.0, true, false)
        assertTrue(near > straight)
        assertTrue(near <= 17.4)
    }

    @Test fun pref02_06_openFastRoadTiltRequiresSustainedSpeed() {
        val tilt = AdaptiveTilt()
        assertEquals(AdaptiveTilt.NORMAL, tilt.target(0, 30.0, 4000.0, false, false), 0.01)
        assertEquals(AdaptiveTilt.OPEN, tilt.target(9000, 30.0, 4000.0, false, false), 0.01)
    }

    @Test fun pref02_06_zoomFollowsRoadSpeedBand() {
        val low = AdaptiveZoom().update(1000, 5.0, 4000.0, false, false)
        val high = AdaptiveZoom().update(1000, 29.0, 4000.0, false, false)
        assertTrue(high < low)
    }

    @Test fun pref02_07_junctionTiltsTowardOverhead() {
        val tilt = AdaptiveTilt()
        tilt.target(0, 26.0, 1500.0, false, false)
        tilt.target(9000, 26.0, 1500.0, false, false)
        val approach = tilt.target(10000, 18.0, 80.0, true, false)
        assertEquals(AdaptiveTilt.JUNCTION, approach, 0.01)
        assertTrue(approach < AdaptiveTilt.OPEN)
    }

    @Test fun pref02_07_roundaboutUsesJunctionPerspective() {
        assertEquals(AdaptiveTilt.JUNCTION, AdaptiveTilt().target(0, 10.0, 200.0, true, true), 0.01)
    }

    @Test fun pref02_08_zoomIsTimeRateLimited() {
        assertEquals(15.0, DrivingCamera.limitZoomOverTime(15.0, 18.0, 0), 0.00001)
        assertEquals(16.2, DrivingCamera.limitZoomOverTime(15.0, 18.0, 1000), 0.00001)
    }

    @Test fun pref02_08_tiltDoesNotJumpAcrossOneFix() {
        val tilt = AdaptiveTilt()
        tilt.update(1000, 30.0, 3000.0, false, false)
        tilt.update(10000, 30.0, 3000.0, false, false)
        val before = tilt.tilt
        val after = tilt.update(11000, 10.0, 50.0, true, false)
        assertTrue(kotlin.math.abs(after - before) <= AdaptiveTilt.MAX_RATE + 0.00001)
    }

    @Test fun pref02_08_insetChangesGlideInsteadOfJumping() {
        val glide = InsetGlide()
        glide.snap(DrivingCamera.ViewportInsets(top = 10, bottom = 50), 1000)
        val next = glide.update(DrivingCamera.ViewportInsets(top = 900, bottom = 800), 1100)
        assertTrue(next.top > 10)
        assertTrue(next.top <= 10 + 60)
        assertTrue(next.bottom <= 50 + 60)
    }

    @Test fun pref02_09_newTurnCanWidenFittedFrame() {
        val same = DrivingCamera.turnFrameZoom(16.5, 15.8, true)
        val changed = DrivingCamera.turnFrameZoom(16.5, 15.8, false)
        assertEquals(16.5, same, 0.001)
        assertEquals(15.8, changed, 0.001)
    }

    @Test fun pref02_09_geometrySlicePreservesIntermediateRoute() {
        val route = listOf(LatLon(53.0, -2.0), LatLon(53.0, -1.999), LatLon(53.001, -1.999))
        val index = RouteGeometryIndex(route)
        val slice = index.slice(0.0, index.total)
        assertTrue(slice.size >= 3)
        assertEquals(route.first(), slice.first())
        assertEquals(route.last(), slice.last())
    }

    @Test fun pref02_03_routePaletteIsNotATrafficState() {
        // Verifies the existing route palette's single meaning; it does NOT verify traffic shading.
        assertEquals(RoutePalette.colors(false).fill, RoutePalette.colors(true).fill)
        assertTrue(RoutePalette.width(RoutePalette.Line.FILL, 17.0) > 0)
    }
}
