package com.drivemate.app.nav

import org.junit.Assert.*
import org.junit.Test
import java.util.Random

/** Combined production-policy scenarios, not a replacement navigation implementation. */
class GpsPreferenceFlowTest {
    private fun progress(now: Long, accuracy: Double = 5.0) = SharedProgress(now, 0, 20.0, 150.0, 0, 10.0, accuracy, 0.0, false)
    @Test fun lossHidesLanesBeforeShowingTheDelayedVisualWarning() {
        val u = PositionUncertainty(); val g = GuidanceFallback()
        g.update(1000, progress(1000), true, true); assertTrue(g.lanesAllowed)
        val reliable = PositionUncertainty.reliable(false, true, 5.0, 0)
        g.invalidate(); assertFalse(g.lanesAllowed); assertFalse(u.update(1000, reliable))
        assertFalse(u.update(3999, reliable)); assertTrue(u.update(4000, reliable))
        assertFalse(GpsLossPolicy.shouldWarn(1000, 4000))
    }
    @Test fun intermittentBadFixesCannotAccumulateIntoAContinuousWarning() {
        val u = PositionUncertainty()
        for (n in 0L..1000L) { val t = n * 3000; assertFalse(u.update(t, false)); assertFalse(u.update(t + 2999, true)) }
    }
    @Test fun recoveryRequiresStableLaneConfidenceButClearsTheLabelImmediately() {
        val u = PositionUncertainty(); val g = GuidanceFallback()
        u.update(1000, false); assertTrue(u.update(4000, false)); g.invalidate()
        assertFalse(u.update(4001, true)); g.update(4001, progress(4001), true, true); assertFalse(g.lanesAllowed)
        g.update(7000, progress(7000), true, true); assertFalse(g.lanesAllowed)
        g.update(7001, progress(7001), true, true); assertTrue(g.lanesAllowed)
    }
    @Test fun freshGyroHeadingCannotRestoreLanesForAnEstimatedPosition() {
        val e = HeadingEstimator(); e.onGravity(0f, 0f, 9.81f); e.onGps(90f, 10f, 1000)
        assertTrue(e.fresh(1500)); assertFalse(PositionUncertainty.reliable(true, false, 5.0, 0))
        val g = GuidanceFallback(); g.update(1500, progress(1500), true, true, true); assertFalse(g.lanesAllowed)
    }
    @Test fun uncertainMapRemainsPannableWithoutTimedRecentering() {
        for (t in 0L..120_000L step 250) {
            assertFalse(PositionCameraPolicy.mayAutoRecenter(true, false, false))
            assertFalse(PositionCameraPolicy.mayInitiallyCenter(true, false))
        }
    }
    @Test fun seededUncertaintySequencesMatchTheIndependentElapsedTimeOracle() {
        val random = Random(20261023); val p = PositionUncertainty(); var since: Long? = null; var t = 0L
        repeat(5000) {
            t += random.nextInt(501)
            val good = random.nextInt(15) == 0
            if (good) since = null else if (since == null) since = t
            assertEquals(!good && t - since!! >= 3000, p.update(t, good))
        }
    }
    @Test fun invalidNumbersAlwaysFailClosedForPositionAndLanes() {
        for (accuracy in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0, 0.0, 25.01, 100.0)) {
            assertFalse(PositionUncertainty.reliable(true, true, accuracy, 0))
            val g = GuidanceFallback(); g.update(1000, progress(1000, accuracy), true, true); assertFalse(g.lanesAllowed)
        }
    }
    @Test fun repeatedSensorResetCannotCarryHeadingBetweenSessions() {
        val e = HeadingEstimator()
        repeat(100) {
            e.onGps((it % 360).toFloat(), 10f, 1000); assertNotNull(e.heading)
            e.reset(); assertNull(e.heading); assertFalse(e.fresh(1000))
        }
    }
}
