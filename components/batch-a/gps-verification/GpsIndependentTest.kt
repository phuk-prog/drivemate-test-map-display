package com.drivemate.app.nav

import org.junit.Assert.*
import org.junit.Test

/** Separate edge-case verification, not used to obtain the baseline score. */
class GpsIndependentTest {
    @Test fun unreliablePositionNeedsContinuousThreeSecondsAcrossManyUpdates() {
        val p = PositionUncertainty()
        for (t in 1000L..3999L step 13) assertFalse(p.update(t, false))
        assertTrue(p.update(4000, false))
    }
    @Test fun backwardClockStartsANewUncertaintyPeriod() {
        val p = PositionUncertainty(); p.update(10_000, false)
        assertFalse(p.update(1000, false)); assertFalse(p.update(3999, false)); assertTrue(p.update(4000, false))
    }
    @Test fun resetDoesNotCarryUncertaintyAcrossSessions() {
        val p = PositionUncertainty(); p.update(1000, false); p.reset(); assertFalse(p.update(9000, false))
    }
    @Test fun noPermissionCannotBecomeReliable() { assertFalse(PositionUncertainty.reliable(false, true, 5.0, 0)) }
    @Test fun estimatedPositionCannotBecomeReliable() { assertFalse(PositionUncertainty.reliable(true, false, 5.0, 0)) }
    @Test fun missingAccuracyCannotBecomeReliable() { assertFalse(PositionUncertainty.reliable(true, true, null, 0)) }
    @Test fun nonfiniteAccuracyCannotBecomeReliable() { assertFalse(PositionUncertainty.reliable(true, true, Double.NaN, 0)) }
    @Test fun futureFixCannotBecomeReliable() { assertFalse(PositionUncertainty.reliable(true, true, 5.0, -1)) }
    @Test fun missingTimestampCannotBecomeReliable() { assertFalse(PositionUncertainty.reliable(true, true, 5.0, null)) }
    @Test fun freshnessBoundaryIsExplicit() {
        assertTrue(PositionUncertainty.reliable(true, true, 25.0, 2500))
        assertFalse(PositionUncertainty.reliable(true, true, 25.0, 2501))
        assertFalse(PositionUncertainty.reliable(true, true, 25.01, 0))
    }
    private fun fix(time: Long = 10_000, lat: Double = 0.0, lon: Double = 0.0, accuracy: Double = 5.0) =
        LocationSelectionPolicy.Fix(LocationSelectionPolicy.Source.GPS, time, lat, lon, accuracy)
    @Test fun invalidCoordinatesAreRejected() {
        for (f in listOf(fix(lat = Double.NaN), fix(lon = Double.POSITIVE_INFINITY), fix(lat = 91.0), fix(lon = 181.0)))
            assertFalse(LocationSelectionPolicy().accept(f, 10_000, true))
    }
    @Test fun staleFixCannotEstablishPosition() { assertFalse(LocationSelectionPolicy().accept(fix(time = 1000), 10_000, true)) }
    @Test fun farFutureFixCannotEstablishPosition() { assertFalse(LocationSelectionPolicy().accept(fix(time = 12_000), 10_000, true)) }
    @Test fun duplicateAndOlderFixesCannotMovePosition() {
        val s = LocationSelectionPolicy(); assertTrue(s.accept(fix(), 10_000, true))
        assertFalse(s.accept(fix(), 10_000, true)); assertFalse(s.accept(fix(time = 9999), 10_000, true))
    }
    @Test fun implausibleJumpDoesNotReplacePosition() {
        val s = LocationSelectionPolicy(); assertTrue(s.accept(fix(), 10_000, true))
        assertFalse(s.accept(fix(time = 11_000, lat = 1.0), 11_000, true)); assertEquals(0.0, s.lastDelivered!!.latitude, 0.0)
    }
    @Test fun longitudeWrapIsAShortMovement() {
        val s = LocationSelectionPolicy(); assertTrue(s.accept(fix(lon = 179.9999), 10_000, true))
        assertTrue(s.accept(fix(time = 11_000, lon = -179.9999), 11_000, true))
    }
    @Test fun networkFixCannotEstablishNavigationPosition() {
        assertFalse(LocationSelectionPolicy().accept(fix().copy(source = LocationSelectionPolicy.Source.NETWORK), 10_000, true))
    }
    @Test fun poorPrecisionCannotEstablishNavigationPosition() {
        assertFalse(LocationSelectionPolicy().accept(fix(accuracy = 100.0), 10_000, true))
    }
    @Test fun lossOfFreshFixExpiresAuthority() {
        val s = LocationSelectionPolicy(); assertTrue(s.accept(fix(), 10_000, true)); assertFalse(s.authoritative(18_001))
    }
    @Test fun uncertainPositionNeverAutomaticallyRecentres() {
        assertFalse(PositionCameraPolicy.mayAutoRecenter(true, false, false))
        assertTrue(PositionCameraPolicy.mayAutoRecenter(true, false, true))
        assertFalse(PositionCameraPolicy.mayAutoRecenter(false, false, true))
        assertFalse(PositionCameraPolicy.mayAutoRecenter(true, true, true))
    }
    @Test fun uncertaintyPreservesTheExistingMapView() {
        assertFalse(PositionCameraPolicy.mayInitiallyCenter(true, false))
        assertTrue(PositionCameraPolicy.mayInitiallyCenter(true, true))
    }
    @Test fun firstCachedPreviewIsAllowedWithoutForcingALaterSnapBack() {
        assertTrue(PositionCameraPolicy.mayInitiallyCenter(false, false))
        assertFalse(PositionCameraPolicy.mayInitiallyCenter(true, false))
    }
    @Test fun uncertaintyNoticeRemainsVisibleDuringRerouting() {
        assertEquals(com.drivemate.app.ui.DrivingNotice.GPS_ESTIMATE,
            com.drivemate.app.ui.drivingNotice(true, true, true, true))
    }
    @Test fun invalidationRemovesCachedLaneConfidenceImmediately() {
        val g = GuidanceFallback()
        val p = SharedProgress(1000, 0, 10.0, 100.0, 0, 5.0, 5.0, 0.0, false)
        g.update(1000, p, true, true); assertTrue(g.lanesAllowed)
        g.invalidate(); assertFalse(g.lanesAllowed); assertEquals(GuidanceLevel.PLAIN_ARROW, g.level)
    }
}
