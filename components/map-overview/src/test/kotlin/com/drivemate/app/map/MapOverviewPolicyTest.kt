package com.drivemate.app.map

import org.junit.Assert.*
import org.junit.Test

/** Pure component contract; rendering and actual regional release coverage still need Android robot evidence. */
class MapOverviewPolicyTest {
    private fun outside(w: Double, s: Double, e: Double, n: Double): Boolean =
        MapOverviewPolicy.outsideRegion(w,s,e,n,-2.6,53.2,-2.1,53.7)

    @Test fun smallerVehicleArrowCannotOccupyEnormousCityOverview() {
        assertEquals(32, MapOverviewPolicy.ARROW_ICON_DP)
        assertTrue(MapOverviewPolicy.ARROW_MAX_ZOOM_SCALE * MapOverviewPolicy.ARROW_ICON_DP <= 40.0f)
        assertTrue(MapOverviewPolicy.ARROW_MIN_ZOOM_SCALE <= 1.0f)
        assertTrue(MapOverviewPolicy.ARROW_MIN_ZOOM_SCALE <= MapOverviewPolicy.ARROW_MAX_ZOOM_SCALE)
    }

    @Test fun hazardIconsMustRespectCollisionPlacement() {
        assertFalse(MapOverviewPolicy.ALLOW_HAZARD_OVERLAP)
        assertFalse(MapOverviewPolicy.IGNORE_HAZARD_COLLISIONS)
    }

    @Test fun broadOverviewDoesNotShowDenseIndividualPins() {
        assertTrue(MapOverviewPolicy.MIN_HAZARD_ICON_ZOOM >= 11f)
    }

    @Test fun interiorViewportFitsRegionalMap() {
        assertFalse(outside(-2.5,53.3,-2.2,53.6))
    }

    @Test fun exactEdgeStillCountsAsCovered() {
        assertFalse(outside(-2.6,53.2,-2.1,53.7))
    }

    @Test fun viewBeyondWesternBoundaryRequiresWarning() {
        assertTrue(outside(-2.7,53.3,-2.2,53.6))
    }

    @Test fun viewBeyondEasternBoundaryRequiresWarning() {
        assertTrue(outside(-2.5,53.3,-2.0,53.6))
    }

    @Test fun viewBeyondSouthernBoundaryRequiresWarning() {
        assertTrue(outside(-2.5,53.1,-2.2,53.6))
    }

    @Test fun viewBeyondNorthernBoundaryRequiresWarning() {
        assertTrue(outside(-2.5,53.3,-2.2,53.8))
    }

    @Test fun missingOrInfiniteViewportFailsClosed() {
        assertTrue(outside(Double.NaN,53.3,-2.2,53.6))
        assertTrue(outside(-2.5,53.3,Double.POSITIVE_INFINITY,53.6))
    }

    @Test fun reversedBoundsFailClosed() {
        assertTrue(outside(-2.1,53.3,-2.6,53.6))
        assertTrue(outside(-2.5,53.7,-2.2,53.2))
    }

    @Test fun malformedRegionFailsClosed() {
        assertTrue(MapOverviewPolicy.outsideRegion(-2.5,53.3,-2.2,53.6,
           -2.1,53.2,-2.6,53.7))
        assertTrue(MapOverviewPolicy.outsideRegion(-2.5,53.3,-2.2,53.6,
           -2.6,53.2,-2.1,Double.NaN))
    }
}
