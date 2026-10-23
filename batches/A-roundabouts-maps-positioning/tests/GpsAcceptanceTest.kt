package com.drivemate.app.nav

import org.junit.Assert.*
import org.junit.Test

/** Synthetic inputs only. These tests execute production Kotlin in a private checkout. */
class GpsAcceptanceTest {
    private fun progress(accuracy: Double = 10.0, offRoute: Double = 0.0, age: Long = 0) =
        SharedProgress(1000, age, 20.0, 150.0, 0, 10.0, accuracy, offRoute, false)
    private fun update(g: GuidanceFallback, p: SharedProgress, now: Long = 1000, estimated: Boolean = false) =
        g.update(now, p, true, true, estimated)
    @Test fun reliablePositionAllowsLanes() { val g = GuidanceFallback(); update(g, progress()); assertTrue(g.lanesAllowed) }
    @Test fun inaccuratePositionHidesLanesImmediately() {
        val g = GuidanceFallback(); update(g, progress()); update(g, progress(35.0)); assertFalse(g.lanesAllowed)
    }
    @Test fun missingAccuracyCannotClaimLanes() {
        val g = GuidanceFallback(); update(g, progress(Double.NaN)); assertFalse(g.lanesAllowed)
    }
    @Test fun invalidRouteDistanceCannotClaimLanes() {
        val g = GuidanceFallback(); update(g, progress(offRoute = Double.NaN)); assertFalse(g.lanesAllowed)
    }
    @Test fun negativeAccuracyCannotClaimLanes() {
        val g = GuidanceFallback(); update(g, progress(-1.0)); assertFalse(g.lanesAllowed)
    }
    @Test fun stalePositionHidesLanes() {
        val g = GuidanceFallback(); update(g, progress(), 7000); assertFalse(g.lanesAllowed)
    }
    @Test fun estimatedPositionNeverAllowsLanes() {
        val g = GuidanceFallback(); update(g, progress(), estimated = true); assertFalse(g.lanesAllowed)
    }
    @Test fun lanesRecoverOnlyAfterStableFixes() {
        val g = GuidanceFallback(); update(g, progress(40.0)); update(g, progress()); assertFalse(g.lanesAllowed)
        update(g, progress().copy(receivedMs = 3999), 3999); assertFalse(g.lanesAllowed)
        update(g, progress().copy(receivedMs = 4000), 4000); assertTrue(g.lanesAllowed)
    }
    @Test fun briefGpsLossKeepsExistingSpokenWarningQuiet() { assertFalse(GpsLossPolicy.shouldWarn(1000, 60999)) }
    @Test fun existingSpokenWarningStillStartsAtOneMinute() { assertTrue(GpsLossPolicy.shouldWarn(1000, 61000)) }
    private fun heading() = HeadingEstimator().apply { onGravity(0f, 0f, 9.81f); onGps(90f, 10f, 1000) }
    @Test fun gyroSupportsHeadingBetweenMeasuredFixes() {
        val e = heading(); e.onGyro(1_000_000_000, 0f, 0f, 1f); e.onGyro(1_100_000_000, 0f, 0f, 1f)
        assertEquals(90.0 - Math.toDegrees(0.1), e.heading!!, 0.001)
    }
    @Test fun invalidGyroCannotPoisonHeading() {
        val e = heading(); e.onGyro(1_000_000_000, 0f, 0f, 1f); e.onGyro(1_100_000_000, Float.NaN, 0f, 1f)
        assertEquals(90.0, e.heading!!, 0.0)
    }
    @Test fun invalidGravityCannotPoisonHeading() {
        val e = heading(); e.onGravity(Float.NaN, 0f, 9.81f)
        e.onGyro(1_000_000_000, 0f, 0f, 1f); e.onGyro(1_100_000_000, 0f, 0f, 1f)
        assertTrue(e.heading!!.isFinite())
    }
    @Test fun olderGyroSampleCannotRewindIntegrationClock() {
        val e = heading(); e.onGyro(1_000_000_000, 0f, 0f, 1f)
        e.onGyro(1_100_000_000, 0f, 0f, 1f); e.onGyro(1_050_000_000, 0f, 0f, 1f)
        e.onGyro(1_200_000_000, 0f, 0f, 1f)
        assertEquals(90.0 - Math.toDegrees(0.2), e.heading!!, 0.001)
    }
    @Test fun sensorsCannotKeepHeadingFreshIndefinitely() { assertFalse(heading().fresh(91_000)) }
    @Test fun stationaryGpsDoesNotChangeHeading() {
        val e = heading(); e.onGps(180f, 0f, 2000); assertEquals(90.0, e.heading!!, 0.0)
    }
    // The old app has no continuous-uncertainty policy. Absence is an explicit baseline gap,
    // not a measurement of a timing implementation that does not exist.
    private fun uncertainty(): Any = try {
        Class.forName("com.drivemate.app.nav.PositionUncertainty").getDeclaredConstructor().newInstance()
    } catch (e: ClassNotFoundException) { throw AssertionError("Three-second continuous-uncertainty feature absent", e) }
    private fun uncertain(p: Any, now: Long, reliable: Boolean): Boolean =
        p.javaClass.getMethod("update", java.lang.Long.TYPE, java.lang.Boolean.TYPE).invoke(p, now, reliable) as Boolean
    @Test fun uncertainLabelStartsAtThreeSeconds() {
        val p = uncertainty(); assertFalse(uncertain(p, 1000, false)); assertFalse(uncertain(p, 3999, false))
        assertTrue(uncertain(p, 4000, false))
    }
    @Test fun recoveredPositionResetsContinuousUncertaintyTimer() {
        val p = uncertainty(); uncertain(p, 1000, false); assertFalse(uncertain(p, 3500, true))
        assertFalse(uncertain(p, 4000, false)); assertFalse(uncertain(p, 6999, false)); assertTrue(uncertain(p, 7000, false))
    }
    @Test fun goodPositionClearsUncertaintyImmediately() {
        val p = uncertainty(); uncertain(p, 1000, false); assertTrue(uncertain(p, 4000, false)); assertFalse(uncertain(p, 4001, true))
    }
}
