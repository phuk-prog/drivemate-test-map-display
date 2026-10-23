package com.drivemate.app.map

import com.drivemate.app.data.TomTom
import com.drivemate.app.nav.LatLon
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Fixtures are completely simulated: no actual TomTom response, API key, location trace,
 * user journey, network request, or key-bearing URL enters this public test repository.
 * Compiled and executed against the real private application, in private CI.
 */
class Category2SimulatedTrafficFixtureTest {
    private val source: JSONObject by lazy {
        val stream = requireNotNull(javaClass.getResourceAsStream("/tomtom-v5-simulated-traffic.json")) {
            "Missing public synthetic fixture"
        }
        JSONObject(stream.bufferedReader().use { it.readText() })
    }
    private val checkedAt: Long get() = source.getLong("checkedAt")
    private val route: List<LatLon> get() {
        val coordinates = source.getJSONObject("road").getJSONArray("coordinates")
        return (0 until coordinates.length()).map { i ->
            val pt = coordinates.getJSONArray(i)
            LatLon(pt.getDouble(1), pt.getDouble(0))
        }
    }
    private fun case(name: String): JSONObject = source.getJSONObject("cases").getJSONObject(name)
    private fun parse(name: String) = TomTom.parseIncidents(
        JSONObject().put("incidents", JSONArray().put(case(name))),
        checkedAt,
    )
    @Test fun pref02_03_fixtureIsExplicitlySyntheticAndContainsNoCredential() {
        assertEquals("SYNTHETIC_TOMTOM_V5_TRAFFIC_TEST_DATA", source.getString("kind"))
        assertTrue(source.getString("warning").contains("Invented"))
        assertFalse("Do not publish any real key", source.has("apiKey"))
    }
    @Test fun pref02_03_simulatedHeavyCongestionUsesRedRouteBand() {
        val bands = TrafficRouteOverlay.sections(route, parse("severe"), checkedAt)
        assertEquals(1, bands.size)
        assertEquals(TrafficRouteOverlay.Level.HEAVY, bands.single().level)
        assertTrue(bands.single().points.size >= 2)
    }
    @Test fun pref02_03_simulatedSlowerTrafficUsesAmberRouteBand() {
        val bands = TrafficRouteOverlay.sections(route, parse("moderate"), checkedAt)
        assertEquals(1, bands.size)
        assertEquals(TrafficRouteOverlay.Level.SLOW, bands.single().level)
    }
    @Test fun pref02_03_simulatedWrongDirectionMustNotColourOppositeCarriageway() {
        assertTrue(TrafficRouteOverlay.sections(route, parse("opposite"), checkedAt).isEmpty())
    }
    @Test fun pref02_03_simulatedOffRouteIncidentMustNotColourNearbyRoad() {
        assertTrue(TrafficRouteOverlay.sections(route, parse("offroad"), checkedAt).isEmpty())
    }
    @Test fun pref02_03_simulatedUnknownDirectionCannotClaimCarriageway() {
        assertTrue(parse("directionUnknown").single().bearing == null)
        assertTrue(TrafficRouteOverlay.sections(route, parse("directionUnknown"), checkedAt).isEmpty())
    }
    @Test fun pref02_03_simulatedExpiredAndFutureEventsAreExcluded() {
        assertTrue(parse("stale").isEmpty())
        assertTrue(parse("future").isEmpty())
    }
    @Test fun pref02_03_simulatedNonCongestionAccidentNotPaintedAsTrafficSpeed() {
        val events = parse("noncongestion")
        assertEquals(1, events.size)
        assertTrue(TrafficRouteOverlay.sections(route, events, checkedAt).isEmpty())
    }
    @Test fun pref02_03_simulatedMalformedCoordinateAndMissingIdAreRejected() {
        assertTrue(parse("invalidCoordinates").isEmpty())
        assertTrue(parse("missingId").isEmpty())
    }
    @Test fun pref02_03_simulatedUnknownExtentDoesNotInventRoadCoverage() {
        assertTrue(TrafficRouteOverlay.sections(route, parse("unknownExtent"), checkedAt).isEmpty())
    }
}
