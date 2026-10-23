package com.drivemate.app.map

import com.drivemate.app.nav.LatLon
import com.drivemate.app.nav.RoadCentreline
import com.drivemate.app.nav.RouteGeometryAudit
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * External expected road shape comes from current OpenStreetMap contributors (ODbL)
 * fetched by public ci/fetch_independent_uk_roads.py at private CI time.
 * This tests geometry diagnostics, not legal lane permissions or actual pixel rendering.
 * A missing real-road fixture is a HARD FAILURE, not silently replaced by fake roads.
 */
class Category2IndependentUkRoadGeometryTest {
    private val fixture: JSONObject by lazy {
        val input = requireNotNull(javaClass.getResourceAsStream("/independent-osm-roads.json")) {
            "Independent real UK roundabout fixture absent: do not pass simulated roads as truth"
        }
        JSONObject(input.bufferedReader().use { it.readText() })
    }
    private val ways get() = fixture.getJSONArray("ways")
    private fun actualGeometry(index: Int): List<LatLon> {
        val coordinates = ways.getJSONObject(index).getJSONArray("coordinates")
        return (0 until coordinates.length()).map { i ->
            coordinates.getJSONArray(i).let { LatLon(it.getDouble(1), it.getDouble(0)) }
        }
    }
    @Test fun pref02_01_externalRoundaboutMustBeRealSourcedAndAttributed() {
        assertEquals("EXTERNALLY_SOURCED_OSM_UK_ROAD_GEOMETRY", fixture.getString("kind"))
        assertEquals("Open Database License (ODbL) 1.0", fixture.getString("licence"))
        assertTrue(fixture.getString("source_url").startsWith("https://www.openstreetmap.org/"))
        assertTrue(fixture.getString("not_legal_truth").contains("official signs"))
        assertTrue(ways.length() > 0)
        for (i in 0 until ways.length()) {
            val way = ways.getJSONObject(i)
            assertTrue(way.getLong("osm_id") > 0)
            assertEquals("Roscoe's Roundabout", way.getString("name"))
            assertTrue(way.getString("junction") in listOf("roundabout", "circular"))
            assertTrue(actualGeometry(i).size >= 3)
        }
    }
    @Test fun pref02_01_currentRealRoadPolylineMustHaveLowVisualDrift() {
        // This is a geometry-only check on independently surveyed, not app-authored, road points.
        val ref = actualGeometry(0)
        val road = RoadCentreline("OSM:way/" + ways.getJSONObject(0).getLong("osm_id"), ref, true)
        val result = RouteGeometryAudit.measure(ref, listOf(road))
        assertTrue(result.samples >= 3)
        assertEquals(0, result.unmatchedSamples)
        assertNotNull(result.p95Metres)
        assertTrue("Visible line should follow independently sourced roundabout geometry", result.p95Metres!! < 1.0)
    }
    @Test fun pref02_01_offRoadLineMustNotPassAsValidRealRoundabout() {
        val ref = actualGeometry(0)
        val road = RoadCentreline("OSM:way/" + ways.getJSONObject(0).getLong("osm_id"), ref, true)
        // Shift south by 300m: NOT an invented road; a deliberately incorrect overlay for negative control.
        val visiblyWrong = ref.map { LatLon(it.lat - 0.003, it.lon) }
        val result = RouteGeometryAudit.measure(visiblyWrong, listOf(road))
        assertTrue(result.p95Metres != null && result.p95Metres!! > 150.0)
    }
    @Test fun pref02_01_externalRoadCannotBeClaimedLegallyVerifiedFromOsmAlone() {
        val ref = actualGeometry(0)
        val uncorroboratedLegalRoad = RoadCentreline("unknown-legal-access", ref, false)
        val result = RouteGeometryAudit.measure(ref, listOf(uncorroboratedLegalRoad))
        assertEquals(result.samples, result.unmatchedSamples)
        assertNull(result.p95Metres)
    }
}
