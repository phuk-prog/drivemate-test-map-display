package com.drivemate.app.ui

import com.drivemate.app.data.Lane
import com.drivemate.app.data.Route
import com.drivemate.app.data.TrafficLookupStatus
import com.drivemate.app.data.TrafficSnapshot
import com.drivemate.app.data.TrafficTiming
import com.drivemate.app.nav.LimitComparison
import com.drivemate.app.nav.Wording
import org.junit.Assert.*
import org.junit.Test

/**
 * Public, source-referenced Batch B diagnostics for actual private DriveMate classes.
 * Official UK road/traffic requirements are in the corresponding public JSON contract.
 * These are NOT a legal survey, recorded speed-limit sign, Compose screenshot, or live traffic API test.
 */
class BatchBGuidanceTrafficWarningsTest {
    private val clock = 1_700_000_000_000L

    @Test fun b03_01_roadTextRetainsStreetAndDestination() {
        val places = navigationPlaces("A6 Stockport Road", "Manchester", emptyList())
        assertEquals(listOf("A6 Stockport Road", "Manchester"), places)
    }

    @Test fun b03_01_roadReferenceIsNotDuplicated() {
        assertEquals(listOf("A6"), navigationPlaces("A6", "A6", emptyList()))
    }

    @Test fun b03_02_farApproachNotNow() {
        assertFalse(turnBannerNow(false, "turn", 600.0, 15f, 1000L))
    }

    @Test fun b03_02_nearTurnWithFreshSpeedIsNow() {
        assertTrue(turnBannerNow(false, "turn", 30.0, 12f, 1_000L))
        assertFalse(turnBannerNow(true, "arrive", 0.0, 12f, 1_000L))
    }

    @Test fun b03_02_staleGpsCannotClaimImminentApproach() {
        assertFalse(turnBannerNow(false, "turn", 50.0, 35f, 45_000L))
        assertFalse(turnBannerNow(false, "turn", Double.NaN, 35f, 1_000L))
    }

    @Test fun b03_03_contiguousRightLanesWording() {
        val lanes = (0..4).map { Lane(listOf("through"), valid = it >= 2) }
        assertEquals("Use the right three lanes", Wording.laneWords(lanes))
    }

    @Test fun b03_03_nonContiguousLanesNotDescribedAsOneBlock() {
        val lanes = (0..3).map { Lane(listOf("through"), valid = it == 0 || it == 2) }
        assertEquals("Use lane 1 or 3 from the left", Wording.laneWords(lanes))
    }

    @Test fun b03_03_noEvidenceNoSpecificLane() {
        assertEquals("", Wording.laneWords(List(3) { Lane(listOf("through"), valid = false) }))
    }

    @Test fun b08_01_onlyFreshTrafficCanBeCurrent() {
        assertTrue(TrafficTiming.fresh(clock - 120_000L, clock))
        assertFalse(TrafficTiming.fresh(clock - 5 * 60_000L - 1, clock))
        assertFalse(TrafficTiming.fresh(clock + 1L, clock))
        assertFalse(TrafficTiming.fresh(0L, clock))
    }

    @Test fun b08_01_unverifiedTrafficNeverClaimsReliableLiveRanking() {
        val current = Route(emptyList(), 1500.0, 90.0, emptyList(), "CURRENT",
            live = true, trafficCheckedAt = clock - 90_000L, comparisonReliable = true)
        val old = Route(emptyList(), 1500.0, 80.0, emptyList(), "STALE",
            live = true, trafficCheckedAt = clock - 6 * 60_000L, comparisonReliable = true)
        val ranked = TrafficTiming.rank(listOf(old, current), clock)
        assertEquals("CURRENT", ranked.first().summary)
        assertFalse("stale traffic cannot make routes comparable",
            TrafficTiming.comparable(ranked, clock))
    }

    @Test fun b08_03_officialSourceUnavailabilityIsExplicit() {
        val snapshot = TrafficSnapshot(emptyList<String>(), TrafficLookupStatus.UNAVAILABLE,
            source = "National Highways")
        assertFalse(snapshot.lookupSucceeded)
        assertTrue(snapshot.summary(clock).contains("could not be checked"))
    }

    @Test fun b08_03_cachedResultCannotMasqueradeAsNewOfficialReport() {
        val snapshot = TrafficSnapshot(listOf("reported-closure"), TrafficLookupStatus.RECENT_CACHE,
            checkedAt = clock - 10 * 60_000L, source = "National Highways")
        assertFalse(snapshot.lookupSucceeded)
        assertTrue(snapshot.summary(clock).contains("10 min ago"))
    }

    @Test fun b09_03_missingSpeedCannotBeClaimedMeasured() {
        assertNull(measuredSpeed(null, 1000L))
        assertNull(measuredSpeed(-1f, 1000L))
        assertNull(measuredSpeed(30f, 30_000L))
    }

    @Test fun b09_03_estimatedPositionCannotCreateSpeedingClaim() {
        assertNull(shownMeasuredSpeed("estimate", 35f, 1_000L))
        assertEquals(35f, shownMeasuredSpeed("gps", 35f, 1_000L)!!, 0.001f)
    }

    @Test fun b09_13_unverifiedUnknownLimitsCannotClaimCertainty() {
        val shown = LimitComparison.shown(ours = null, corrected = false, tomtom = null, nationalHighways = null)
        assertNull(shown.kmh)
        assertFalse(shown.unsure)
    }

    @Test fun b09_13_officialTemporaryLowerLimitOverridesStaticLimit() {
        // Diagnostic only: assumes a genuine active official source was already validated upstream.
        // This does NOT prove currently active sign/order on a named UK road.
        val shown = LimitComparison.shown(ours = 112.65, corrected = false, tomtom = null,
            nationalHighways = 48.28)
        assertEquals(48.28, shown.kmh!!, 0.01)
        assertTrue(shown.unsure)
    }
}
