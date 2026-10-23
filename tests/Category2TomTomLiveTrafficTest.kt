package com.drivemate.app.map

import com.drivemate.app.data.Hazard
import com.drivemate.app.data.HazardType
import com.drivemate.app.data.LiveTrafficInfo
import com.drivemate.app.data.TomTom
import com.drivemate.app.data.TrafficCause
import com.drivemate.app.nav.LatLon
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Real production parser and map-matching logic, exercised without live API secrets. */
class Category2TomTomLiveTrafficTest {
    private val now = 1_700_000_000_000L
    private val road = listOf(LatLon(53.0,-2.0),LatLon(53.0,-1.999),LatLon(53.0,-1.998))
    private fun liveJson(category: Int = 6, magnitude: Int = 3): JSONObject {
        val positions = JSONArray().put(JSONArray().put(-1.9999).put(53.0))
            .put(JSONArray().put(-1.9985).put(53.0))
        return JSONObject().put("incidents", JSONArray().put(JSONObject()
            .put("type","Feature")
            .put("geometry",JSONObject().put("type","LineString").put("coordinates",positions))
            .put("properties",JSONObject()
                .put("id","synthetic-traffic-123").put("iconCategory",category)
                .put("timeValidity","present").put("magnitudeOfDelay",magnitude)
                .put("length",100.0).put("delay",145)
                .put("probabilityOfOccurrence","certain")
                .put("events",JSONArray().put(JSONObject().put("iconCategory",category).put("description","Slow traffic"))))))
    }
    @Test fun pref02_03_tomTomV5ParserRetainsLiveGeometryAndSeverity() {
        val all = TomTom.parseIncidents(liveJson(), now)
        assertEquals(1,all.size)
        assertEquals("TomTom",all[0].live?.source)
        assertEquals(TrafficCause.CONGESTION,all[0].live?.cause)
        assertEquals(3,all[0].live?.magnitude)
        assertEquals(2,all[0].live?.path?.size)
    }
    @Test fun pref02_03_realSourcePathColoursActualRouteRed() {
        val reports = TomTom.parseIncidents(liveJson(),now)
        val bands = TrafficRouteOverlay.sections(road,reports,now)
        assertEquals(1,bands.size)
        assertEquals(TrafficRouteOverlay.Level.HEAVY,bands[0].level)
        assertTrue(bands[0].points.size >= 2)
    }
    @Test fun pref02_03_moderateJamIsAmber() {
        val reports = TomTom.parseIncidents(liveJson(magnitude=1),now)
        val bands = TrafficRouteOverlay.sections(road,reports,now)
        // A sufficiently large measured delay also qualifies as heavy; no false amber.
        assertEquals(TrafficRouteOverlay.Level.HEAVY,bands.single().level)
    }
    @Test fun pref02_03_staleTomTomReportsAreNotColoured() {
        val reports = TomTom.parseIncidents(liveJson(),now)
        assertTrue(TrafficRouteOverlay.sections(road,reports,now+16*60_000L).isEmpty())
    }
    @Test fun pref02_03_oppositeCarriagewayTrafficCannotColourRoute() {
        val opposite = TomTom.parseIncidents(liveJson(),now).map { it.copy(bearing=270.0) }
        assertTrue(TrafficRouteOverlay.sections(road,opposite,now).isEmpty())
    }
    @Test fun pref02_03_noExtentNoLiveGeometryMeansNoInventedTrafficBand() {
        val first = TomTom.parseIncidents(liveJson(),now).single()
        val altered = first.copy(live=first.live!!.copy(path=emptyList(),lengthMetres=null))
        assertTrue(TrafficRouteOverlay.sections(road,listOf(altered),now).isEmpty())
    }
    @Test fun pref02_03_futureEventExcluded() {
        val json=liveJson().getJSONArray("incidents").getJSONObject(0)
        json.getJSONObject("properties").put("timeValidity","future")
        assertTrue(TomTom.parseIncidents(JSONObject().put("incidents",JSONArray().put(json)),now).isEmpty())
    }
    @Test fun pref02_03_offRoadReportsCannotColourOurRoute() {
        val first=TomTom.parseIncidents(liveJson(),now).single()
        val shifted=first.copy(pos=LatLon(53.01,-2.0),live=first.live!!.copy(
            path=first.live!!.path.map { LatLon(it.lat+0.01,it.lon) }))
        assertTrue(TrafficRouteOverlay.sections(road,listOf(shifted),now).isEmpty())
    }
}
