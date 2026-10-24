package com.drivemate.app.ui

import com.drivemate.app.data.Lane
import com.drivemate.app.data.RouteStep
import com.drivemate.app.nav.LatLon
import com.drivemate.app.nav.Wording
import com.drivemate.app.nav.TurnInstructions
import org.junit.Assert.*
import org.junit.Test

/** Owner preference assertions on supplied lane evidence; not lane legality or UI rendering. */
class GuidancePreferenceTest {
    private fun lanes(n: Int, valid: Set<Int>) = List(n) { Lane(listOf("through"), it in valid) }
    private fun step(type: String = "continue", modifier: String = "straight") =
        RouteStep("Continue straight", type, modifier, "", 100.0, LatLon(53.4,-2.2))
    @Test fun rightThreeOfFourNamesUsableLanes() {
        assertEquals("Use the right 3 lanes", Wording.laneWords(lanes(4,setOf(1,2,3))))
    }
    @Test fun rightThreeOfFiveHasExplicitCount() {
        assertEquals("Use the right 3 lanes", Wording.laneWords(lanes(5,setOf(2,3,4))))
    }
    @Test fun leftThreeOfFourNamesUsableLanes() {
        assertEquals("Use the left 3 lanes", Wording.laneWords(lanes(4,setOf(0,1,2))))
    }
    @Test fun fourUsableLanesKeepsTheirCount() {
        assertEquals("Use the right 4 lanes", Wording.laneWords(lanes(6,setOf(2,3,4,5))))
    }
    @Test fun rightTwoLanesHasExplicitCount() {
        assertEquals("Use the right 2 lanes", Wording.laneWords(lanes(4,setOf(2,3))))
    }
    @Test fun singleRightLaneStaysSingular() {
        assertEquals("Use the right lane", Wording.laneWords(lanes(3,setOf(2))))
    }
    @Test fun allLanesUsableNeedsNoSpecificAdvice() {
        assertEquals("", Wording.laneWords(lanes(3,setOf(0,1,2))))
    }
    @Test fun noUsableLaneDoesNotInventAdvice() {
        assertEquals("", Wording.laneWords(lanes(3,emptySet())))
    }
    @Test fun separatedLanesCannotBeNamedAsOneEdgeBlock() {
        assertEquals("Use lane 1 or 3 from the left", Wording.laneWords(lanes(4,setOf(0,2))))
    }
    @Test fun busLaneIsExcludedFromRecommendation() {
        val supplied=lanes(3,setOf(0,2)).mapIndexed { i,l -> if(i==0) l.copy(bus=true) else l }
        assertEquals("Use the right lane", Wording.laneWords(supplied))
    }
    @Test fun unknownMarkingIsExcludedFromRecommendation() {
        val supplied=listOf(Lane(listOf("unknown"),true),Lane(listOf("through"),false),Lane(listOf("through"),true))
        assertEquals("Use the right lane", Wording.laneWords(supplied))
    }
    @Test fun combinedStraightInstructionMatchesOwnerExample() {
        val use=Wording.laneWords(lanes(4,setOf(1,2,3)))
        assertEquals("Use the right 3 lanes to continue straight",Wording.withLane(step(),"Continue straight",use))
    }
    @Test fun combinedStraightInstructionRetainsRoadSuffix() {
        assertEquals("Use the right 3 lanes to continue straight onto A6",Wording.withLane(step(),"Continue straight onto A6","Use the right 3 lanes"))
    }
    @Test fun turnInstructionRetainsTurnRatherThanInventingStraight() {
        assertEquals("use the left lane, then Turn left onto A6",Wording.withLane(step("turn","left"),"Turn left onto A6","Use the left lane"))
    }
    @Test fun absentLaneAdviceRetainsOriginalInstruction() {
        assertEquals("Continue straight",Wording.withLane(step(),"Continue straight",""))
    }
    @Test fun farTurnPolicyDoesNotClaimNow() {
        assertFalse(turnBannerNow(false,"turn",600.0,15f,1000L))
    }
    @Test fun nearTurnPolicyUsesFreshActualSpeed() {
        assertTrue(turnBannerNow(false,"turn",60.0,12f,1000L))
        assertFalse(turnBannerNow(false,"turn",60.1,12f,1000L))
    }
    @Test fun staleSpeedCannotExtendApproachThreshold() {
        assertFalse(turnBannerNow(false,"turn",50.0,35f,45000L))
    }
    @Test fun malformedDistanceCannotClaimNow() {
        assertFalse(turnBannerNow(false,"turn",Double.NaN,12f,1000L))
        assertFalse(turnBannerNow(false,"turn",-1.0,12f,1000L))
    }
    @Test fun arrivalCannotClaimTurnNow() {
        assertFalse(turnBannerNow(true,"arrive",0.0,12f,1000L))
        assertFalse(turnBannerNow(false,"stop",0.0,12f,1000L))
    }

    private fun actualStep(type: String, modifier: String, name: String): RouteStep {
        val instruction=TurnInstructions.describe(type,modifier,name,0,true)
        return RouteStep(instruction,type,modifier,name,100.0,LatLon(53.4,-2.2))
    }
    @Test fun realContinueFormatterWithExplicitStraightDirectionCombinesLaneInstruction() {
        val s=actualStep("continue","straight","A6")
        assertEquals("Continue onto A6",s.instruction)
        assertEquals("Use the right 3 lanes to continue straight onto A6",Wording.withLane(s,s.instruction,"Use the right 3 lanes"))
    }
    @Test fun realContinueFormatterWithoutRoadStillUsesExplicitStraightDirection() {
        val s=actualStep("continue","straight","")
        assertEquals("Use the right 3 lanes to continue straight",Wording.withLane(s,s.instruction,"Use the right 3 lanes"))
    }
    @Test fun realNewNameFormatterPreservesRoadOnExplicitStraightDirection() {
        val s=actualStep("new name","straight","Stockport Road")
        assertEquals("Use the right 3 lanes to continue straight onto Stockport Road",Wording.withLane(s,s.instruction,"Use the right 3 lanes"))
    }
    @Test fun realNotificationFormatterUsesExplicitThroughDirection() {
        val s=actualStep("notification","through","A6")
        assertEquals("Use the right 3 lanes to continue straight onto A6",Wording.withLane(s,s.instruction,"Use the right 3 lanes"))
    }
    @Test fun genericContinueWithoutDirectionCannotInventStraight() {
        val s=actualStep("continue","","A6")
        assertEquals("use the right 3 lanes, then Continue onto A6",Wording.withLane(s,s.instruction,"Use the right 3 lanes"))
    }
    @Test fun bendingContinueCannotBecomeStraightInstruction() {
        val s=actualStep("continue","slight right","A6")
        assertEquals("use the right 3 lanes, then Bear right onto A6",Wording.withLane(s,s.instruction,"Use the right 3 lanes"))
    }
}
