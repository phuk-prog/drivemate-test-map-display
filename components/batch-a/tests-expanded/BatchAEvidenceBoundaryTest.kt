package com.drivemate.app.nav

import com.drivemate.app.data.Lane
import com.drivemate.app.ui.shouldFocusJunction
import org.junit.Assert.*
import org.junit.Test
import java.util.Random

/** Synthetic component scenarios. They do not establish UI wiring, surveyed lanes or legal access. */
class BatchAEvidenceBoundaryTest {
    private fun policy(name: String): Any = try { Class.forName("com.drivemate.app.nav.$name").getDeclaredConstructor().newInstance() }
        catch (e: ClassNotFoundException) { throw AssertionError("Approved preference policy absent: $name",e) }
    private fun uncertain(p: Any,t: Long,good: Boolean) = p.javaClass.getMethod("update",java.lang.Long.TYPE,java.lang.Boolean.TYPE).invoke(p,t,good) as Boolean
    private fun camera(method: String,vararg args: Boolean): Boolean {
        val cls=try { Class.forName("com.drivemate.app.nav.PositionCameraPolicy") } catch(e:ClassNotFoundException) { throw AssertionError("GPS-gap camera policy absent",e) }
        return cls.getMethod(method,*Array(args.size){java.lang.Boolean.TYPE}).invoke(cls.getField("INSTANCE").get(null),*args.toTypedArray()) as Boolean
    }
    @Test fun pref01_01_threeSecondsIncludesExactBoundaryAndResetsOnRecovery() {
        val p=policy("PositionUncertainty")
        assertFalse(uncertain(p,0,false));assertFalse(uncertain(p,2999,false));assertTrue(uncertain(p,3000,false))
        assertFalse(uncertain(p,3001,true));assertFalse(uncertain(p,3002,false));assertFalse(uncertain(p,6001,false));assertTrue(uncertain(p,6002,false))
    }
    @Test fun pref01_01_clockRegressionRestartsContinuousObservation() {
        val p=policy("PositionUncertainty");uncertain(p,5000,false)
        assertFalse(uncertain(p,1000,false));assertFalse(uncertain(p,3999,false));assertTrue(uncertain(p,4000,false))
    }
    @Test fun pref01_01_seededIntermittentLossUsesIndependentElapsedTimeOracle() {
        val p=policy("PositionUncertainty");val r=Random(184186);var start:Long?=null;var time=0L
        repeat(5000) { time+=r.nextInt(600);val good=r.nextInt(8)==0
            if(good) start=null else if(start==null) start=time
            val expected=!good && start!=null && time-start!!>=3000
            assertEquals(expected,uncertain(p,time,good))
        }
    }
    @Test fun pref01_03_missingGpsMustNotRecenterAPannedMap() {
        for(navigating in listOf(false,true)) for(following in listOf(false,true))
            assertFalse(camera("mayAutoRecenter",navigating,following,false))
    }
    @Test fun pref01_03_waitingForGpsKeepsAnExistingViewport() { assertFalse(camera("mayInitiallyCenter",true,false)) }
    @Test fun pref01_03_reliableRecoveryCanResumeExistingRecenterPolicy() { assertTrue(camera("mayAutoRecenter",true,false,true)) }
    @Test fun pref01_04_gyroWithoutAMeasuredHeadingCannotInventDirection() {
        val h=HeadingEstimator();h.onGravity(0f,0f,9.81f);h.onGyro(1_000_000_000,0f,0f,1f);h.onGyro(1_100_000_000,0f,0f,1f);assertNull(h.heading)
    }
    @Test fun pref01_04_eachNonFiniteGyroAxisCannotCorruptMeasuredHeading() {
        for(v in listOf(Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY)) for(axis in 0..2) {
            val h=HeadingEstimator();h.onGravity(0f,0f,9.81f);h.onGps(90f,10f,1000)
            val values=floatArrayOf(0f,0f,0f);values[axis]=v
            h.onGyro(1_000_000_000,0f,0f,0f);h.onGyro(1_100_000_000,values[0],values[1],values[2])
            assertEquals(90.0,h.heading!!,0.0)
        }
    }
    @Test fun pref04_02_suppliedLaneArrowsReachTheDetailedSceneModel() {
        val lanes=listOf(Lane(listOf("left"),false),Lane(listOf("straight"),true),Lane(listOf("right"),false))
        val plan=JunctionPlan.build("turn","straight",true,lanes,null,null,null,"","","",fastRoad=false)!!
        assertEquals(listOf(1),plan.highlighted);assertEquals(lanes.map{it.indications},plan.arrows)
        assertTrue(JunctionScene.build(plan,1080f,1920f,false,100f).isNotEmpty())
    }
    @Test fun pref04_02_missingLaneEvidenceMustNotGenerateLaneHighlights() {
        val plan=JunctionPlan.build("off ramp","left",true,emptyList(),null,null,null,"","","",fastRoad=true)!!
        assertTrue(plan.highlighted.isEmpty());assertTrue(plan.arrows.isEmpty());assertNull(plan.lanes)
    }
    @Test fun pref04_03_simpleTownTurnDoesNotRequestComplexJunctionFocus() { assertFalse(shouldFocusJunction(true,false,"turn",1,100.0)) }
    @Test fun pref04_03_complexRoundaboutRequestsFocusOnlyBeforeArrival() {
        assertTrue(shouldFocusJunction(true,false,"roundabout",3,100.0));assertFalse(shouldFocusJunction(true,true,"roundabout",3,100.0))
    }
    @Test fun pref04_06_immersiveSceneChangesContinuouslyAsTheCarApproaches() {
        val plan=JunctionPlan.build("off ramp","left",true,emptyList(),null,null,null,"","","",fastRoad=true)!!
        val far=JunctionScene.build(plan,1080f,1920f,false,300f)
        val near=JunctionScene.build(plan,1080f,1920f,false,100f)
        assertNotEquals(far,near)
        assertTrue(JunctionScene.screenFraction(100f)>JunctionScene.screenFraction(300f))
    }
}
