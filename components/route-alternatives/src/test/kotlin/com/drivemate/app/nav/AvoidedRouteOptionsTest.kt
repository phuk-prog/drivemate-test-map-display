package com.drivemate.app.nav
import org.junit.Assert.*
import org.junit.Test

/** Supplied evidence scenarios, not real-road legal/access verification or live API calls. */
class AvoidedRouteOptionsTest {
    private val now=1_000_000L
    private fun route(key:String="strict",seconds:Double=600.0,uses:Set<String> = emptySet(),relaxed:Set<String> = emptySet())=
        AvoidedRouteOptions.Evidence(key,key,seconds,uses,relaxed,liveCheckedAtMs=now,timingSource="TomTom",timingReliable=true)
    private fun motorway(seconds:Double=400.0)=route("motorway",seconds,setOf("motorway"),setOf("motorway"))
    @Test fun fasterWarnedAlternativeNeverReplacesCompliantDefault() {
        val r=AvoidedRouteOptions.build(setOf("motorway"),listOf(route()),listOf(motorway()),now)
        assertEquals(listOf("strict","motorway"),r.choices.map{it.evidence.key});assertEquals("strict",r.defaultKey)
        assertTrue(r.choices.last().requiresExplicitChoice);assertTrue(r.choices.last().fasterConfirmed)
        assertTrue(r.choices.last().warning!!.contains("motorways"))
    }
    @Test fun noCompliantRouteShowsWarnedOptionsWithNothingSelected() {
        val r=AvoidedRouteOptions.build(setOf("motorway"),emptyList(),listOf(motorway()),now)
        assertNull(r.defaultKey);assertEquals(1,r.choices.size);assertFalse(r.choices[0].fasterConfirmed)
    }
    @Test fun fallbackDoesNotInventAFasterComparisonOrLiveTrafficClaim() {
        val r=AvoidedRouteOptions.build(setOf("motorway"),emptyList(),listOf(motorway().copy(liveCheckedAtMs=0,timingReliable=false)),now)
        assertEquals(1,r.choices.size);assertFalse(r.choices[0].warning!!.contains("faster"))
    }
    @Test fun advisoryCannotStartWithoutExplicitMatchingChoice() {
        assertFalse(AvoidedRouteOptions.mayStart(true,"route",null));assertFalse(AvoidedRouteOptions.mayStart(true,"route","other"))
        assertTrue(AvoidedRouteOptions.mayStart(true,"route","route"))
    }
    @Test fun ordinaryRouteCanStartWithoutAdvisoryConsent() { assertTrue(AvoidedRouteOptions.mayStart(false,"route",null)) }
    @Test fun requestsNeverRelaxUnknownLegalOrVehicleRestrictions() {
        val keep=setOf("closed-road","height","weight");val requests=AvoidedRouteOptions.requests(keep+setOf("motorway","toll"))
        assertTrue(requests.isNotEmpty());assertTrue(requests.all{it.avoid.containsAll(keep)})
        assertTrue(requests.any{it.relaxed==setOf("motorway","toll")})
    }
    @Test fun noAvoidanceSettingsCauseNoRelaxedRequests() { assertTrue(AvoidedRouteOptions.requests(emptySet()).isEmpty()) }
    @Test fun unknownRestrictionAloneIsNeverRelaxed() { assertTrue(AvoidedRouteOptions.requests(setOf("closed-road")).isEmpty()) }
    @Test fun equalOrSlowerAlternativesAreNotRecommendedAsFaster() {
        for(t in listOf(600.0,601.0)) assertEquals(1,AvoidedRouteOptions.build(setOf("motorway"),listOf(route()),listOf(motorway(t)),now).choices.size)
    }
    @Test fun staleTrafficCannotSupportAFasterClaim() {
        val e=motorway().copy(liveCheckedAtMs=now-300_001)
        assertEquals(1,AvoidedRouteOptions.build(setOf("motorway"),listOf(route()),listOf(e),now).choices.size)
    }
    @Test fun futureTrafficCannotSupportAFasterClaim() {
        assertEquals(1,AvoidedRouteOptions.build(setOf("motorway"),listOf(route()),listOf(motorway().copy(liveCheckedAtMs=now+1)),now).choices.size)
    }
    @Test fun unrelatedTimingSourcesCannotBeCompared() {
        assertEquals(1,AvoidedRouteOptions.build(setOf("motorway"),listOf(route()),listOf(motorway().copy(timingSource="other")),now).choices.size)
    }
    @Test fun staleStrictRouteDoesNotMakeAnAlternativeProvenFaster() {
        val r=AvoidedRouteOptions.build(setOf("motorway"),listOf(route().copy(liveCheckedAtMs=1)),listOf(motorway()),now)
        assertEquals(1,r.choices.size)
    }
    @Test fun knownClosedAlternativeIsNeverOffered() {
        assertTrue(AvoidedRouteOptions.build(setOf("motorway"),emptyList(),listOf(motorway().copy(knownClosed=true)),now).choices.isEmpty())
    }
    @Test fun knownClosedStrictRouteIsNotAnAutomaticDefault() {
        assertNull(AvoidedRouteOptions.build(emptySet(),listOf(route().copy(knownClosed=true)),emptyList(),now).defaultKey)
    }
    @Test fun unknownRoadUseCannotBecomeAnInventedWarning() {
        assertTrue(AvoidedRouteOptions.build(setOf("motorway"),emptyList(),listOf(motorway().copy(reportedUses=emptySet())),now).choices.isEmpty())
    }
    @Test fun alternativeCannotViolateAnAvoidanceStillRequested() {
        assertTrue(AvoidedRouteOptions.build(setOf("motorway","toll"),emptyList(),listOf(motorway().copy(reportedUses=setOf("motorway","toll"))),now).choices.isEmpty())
    }
    @Test fun jointRelaxationShowsBothKnownAvoidedRoadTypes() {
        val e=motorway().copy(reportedUses=setOf("motorway","toll"),relaxed=setOf("motorway","toll"))
        val r=AvoidedRouteOptions.build(setOf("motorway","toll"),emptyList(),listOf(e),now)
        assertTrue(r.choices.single().warning!!.contains("tolls"));assertNull(r.defaultKey)
    }
    @Test fun invalidDurationsDoNotBecomeRoutingSuggestions() {
        for(t in listOf(Double.NaN,Double.POSITIVE_INFINITY,-1.0,0.0)) assertTrue(AvoidedRouteOptions.build(setOf("motorway"),emptyList(),listOf(motorway(t)),now).choices.isEmpty())
    }
    @Test fun duplicateGeometryIsNotOfferedTwice() {
        val e=motorway();assertEquals(1,AvoidedRouteOptions.build(setOf("motorway"),emptyList(),listOf(e,e),now).choices.size)
    }
    @Test fun strictRouteWithReportedAvoidedUseRequiresTheWarnedPath() {
        assertNull(AvoidedRouteOptions.build(setOf("motorway"),listOf(motorway().copy(relaxed=emptySet())),emptyList(),now).defaultKey)
    }
    @Test fun refreshedChoiceDoesNotReuseConsentForAnotherRoute() {
        assertFalse(AvoidedRouteOptions.mayStart(true,"new-route","old-route"))
    }
    @Test fun motorwayEvidenceUsesExplicitReferencesInsteadOfRoadNames() {
        assertEquals(setOf("motorway"),PreferenceRoadEvidence.reported(listOf("M60;A627(M)"),emptyList(),false,false))
        assertTrue(PreferenceRoadEvidence.reported(listOf("Motorway Farm","A60","M60 Services"),emptyList(),false,false).isEmpty())
    }
    @Test fun ferryAndTollWarningsRequireSuppliedMetadata() {
        assertEquals(setOf("toll","ferry"),PreferenceRoadEvidence.reported(emptyList(),listOf("ferry"),true,false))
        assertTrue(PreferenceRoadEvidence.reported(emptyList(),listOf("Ferry Road"),false,false).isEmpty())
    }
    @Test fun zoneWarningDoesNotInventAChargeWithoutACrossing() {
        assertEquals(setOf("charge"),PreferenceRoadEvidence.reported(emptyList(),emptyList(),false,true))
        assertTrue(PreferenceRoadEvidence.reported(emptyList(),emptyList(),false,false).isEmpty())
    }
    @Test fun invalidOrMissingGeometryCannotSupplyAChoiceIdentity() {
        assertNull(RouteShapeKey.of(emptyList()));assertNull(RouteShapeKey.of(listOf(91.0 to 0.0,0.0 to 0.0)))
        assertNull(RouteShapeKey.of(listOf(Double.NaN to 0.0,0.0 to 0.0)))
    }
    @Test fun geometryIdentityIsStableAndSeparatesDifferentRoutes() {
        val p=listOf(53.0 to -2.0,53.001 to -2.001)
        assertEquals(RouteShapeKey.of(p),RouteShapeKey.of(p.toList()))
        assertNotEquals(RouteShapeKey.of(p),RouteShapeKey.of(p.reversed()))
    }
}