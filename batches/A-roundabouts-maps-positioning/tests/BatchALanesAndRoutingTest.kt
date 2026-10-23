package com.drivemate.app.nav

import com.drivemate.app.data.Lane
import com.drivemate.app.ui.preferLanes
import org.junit.Assert.*
import org.junit.Test

/** Synthetic policy diagnostics, not a legal-lane oracle or rendered road. */
class BatchALanesAndRoutingTest {
    private fun lanes() = listOf(Lane(listOf("straight"),true),Lane(listOf("straight"),true),Lane(listOf("straight"),true))
    @Test fun pref04_01_allSuitableLanesRemainWhenNoFollowingTurn() { assertEquals(listOf(0,1,2),LanePlan.yours(preferLanes(lanes(),null))) }
    @Test fun pref04_01_followingRightTurnPrefersRightmostSuitableLane() { assertEquals(listOf(2),LanePlan.yours(preferLanes(lanes(),90f))) }
    @Test fun pref04_01_followingLeftTurnPrefersLeftmostSuitableLane() { assertEquals(listOf(0),LanePlan.yours(preferLanes(lanes(),-90f))) }
    @Test fun pref04_01_busAndUnknownMarkingsNeverBecomeRecommendations() {
        assertEquals(listOf(2),LanePlan.yours(listOf(Lane(listOf("straight"),true,bus=true),Lane(listOf("unknown"),true),Lane(listOf("straight"),true))))
    }
    @Test fun pref04_01_absentLaneEvidenceDoesNotInventALanePlan() { assertNull(LanePlan.turnLanes(emptyList(),true,3,null){null}) }
    @Test fun pref04_04_closeFollowingJunctionInfluencesExitLaneChoice() { assertTrue(LanePlan.followingWindow("off ramp")>=LanePlan.followingWindow("turn")) }
    @Test fun pref04_05_fastRoadGuidanceBeginsEarlierThanTownGuidance() { assertTrue(JunctionPlan.range(true,110.0)>JunctionPlan.range(false,30.0)) }
    @Test fun pref04_06_scenePersistsWhileCirculating() {
        val p=ImmersiveJunction.Mode(2,400.0)
        assertTrue(ImmersiveJunction.next(p,2,0.0,false,400.0,true,false).active)
    }
    @Test fun pref04_06_arrivalClearsTheImmersiveScene() {
        assertFalse(ImmersiveJunction.next(ImmersiveJunction.Mode(2,400.0),2,0.0,true,400.0,false,true).active)
    }
    @Test fun pref05_03_confirmedWrongTurnCanTriggerOnFirstReliableFix() {
        assertTrue(OffRouteRule.wrongTurn(10.0,false,40.0,5.0,180.0,listOf(0.0,0.0)))
        assertEquals(1,OffRouteRule.fixesNeeded(true,true,false))
        assertTrue(OffRouteRule.lastedLongEnough(1,0.0))
    }
    @Test fun pref05_03_aRoundaboutMustNotBeMistakenForAWrongTurn() { assertFalse(OffRouteRule.wrongTurn(10.0,true,40.0,5.0,180.0,listOf(0.0,0.0))) }
    @Test fun pref05_03_cornerHeadingMustBeComparedWithTheRoadAhead() { assertFalse(OffRouteRule.wrongTurn(10.0,false,40.0,5.0,90.0,listOf(0.0,90.0))) }
    @Test fun pref05_03_parallelRoadEvidenceCanIdentifyOffRouteTravel() { assertTrue(OffRouteRule.clearlyOff(100.0,5.0,true,false,10.0)) }
    @Test fun pref05_03_inaccuratePositionMustNotTriggerImmediateWrongTurn() { assertFalse(OffRouteRule.wrongTurn(10.0,false,30.0,100.0,180.0,listOf(0.0,0.0))) }
}
