package com.drivemate.render

import android.graphics.Bitmap
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivemate.app.map.RouteLayers
import com.drivemate.app.nav.LatLon
import com.drivemate.render.RenderJudge.Band
import com.drivemate.render.RenderJudge.Expect
import com.drivemate.render.RenderJudge.Geo
import com.drivemate.render.RenderJudge.Picture
import com.drivemate.render.RenderJudge.Px
import com.drivemate.render.RenderJudge.Sample
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Checklist 02.01 and 02.03 on a real MapLibre GPU render (Android emulator).
 *
 * - Roads: two separately mapped carriageways of Princess Road (A5103), Manchester, pinned from
 *   OpenStreetMap (fixtures/princess-road-a5103-osm.json). They are drawn in a plain grey so the
 *   judge can tell road from route; no app map style is involved.
 * - Route and traffic: DriveMate's own RouteLayers (published byte-for-byte, see
 *   source-manifest.json) with its real colours and widths, day or night.
 * - Verdict: RenderJudge, which never reads the app's palette or geometry.
 *
 * Each requirement also has a rendered negative control that must be rejected, so a pass cannot
 * come from a judge that accepts anything.
 */
@RunWith(AndroidJUnit4::class)
class RenderedRouteTest {
    @get:Rule val activity = ActivityScenarioRule(RenderHostActivity::class.java)
    @get:Rule val name = TestName()

    private val fixture: JSONObject by lazy {
        InstrumentationRegistry.getInstrumentation().context.assets.open("princess-road-a5103-osm.json")
            .bufferedReader().use { JSONObject(it.readText()) }
    }
    private fun carriageway(key: String): List<Geo> {
        val c = fixture.getJSONObject("carriageways").getJSONObject(key)
        assertEquals("A5103", c.getJSONObject("tags").getString("ref"))
        assertEquals("yes", c.getJSONObject("tags").getString("oneway"))
        val a = c.getJSONArray("coordinates")
        return (0 until a.length()).map { Geo(a.getJSONArray(it).getDouble(1), a.getJSONArray(it).getDouble(0)) }
    }
    private val own by lazy { carriageway("own") }
    private val opposite by lazy { carriageway("opposite") }

    private class Rendered(val picture: Picture, val own: List<Sample>, val opposite: List<Sample>, val bitmap: Bitmap)

    /** Camera centre: 300 m along the north-east-bound carriageway, looking along it. */
    private val centreStation = 300.0
    private fun centre() = RenderJudge.densify(own, 1.0).first { it.second >= centreStation }.first
    private fun ahead() = RenderJudge.densify(own, 1.0).first { it.second >= centreStation + 50 }.first

    private fun bearing(a: Geo, b: Geo): Double {
        val p1 = Math.toRadians(a.lat); val p2 = Math.toRadians(b.lat); val dl = Math.toRadians(b.lon - a.lon)
        val y = Math.sin(dl) * Math.cos(p2)
        val x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl)
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360
    }

    private fun lineFeature(points: List<Geo>) = JSONObject().put("type", "Feature")
        .put("properties", JSONObject())
        .put("geometry", JSONObject().put("type", "LineString").put("coordinates",
            JSONArray().apply { points.forEach { put(JSONArray().put(it.lon).put(it.lat)) } }))

    private fun latLon(points: List<Geo>) = points.map { LatLon(it.lat, it.lon) }

    /**
     * Draws the two reference carriageways, then the app's route stack on [route] with [bands],
     * waits for the renderer to go idle and takes a GPU snapshot.
     */
    private fun render(route: List<Geo>, bands: List<Pair<List<Geo>, String>>, night: Boolean,
                       zoom: Double, tilt: Double): Rendered {
        val bg = if (night) BG_NIGHT else BG_DAY
        val ready = CountDownLatch(1)
        val idle = CountDownLatch(1)
        val error = AtomicReference<Throwable>()
        val view = AtomicReference<MapView>()
        activity.scenario.onActivity { a ->
            view.set(a.mapView)
            a.mapView.getMapAsync { map ->
                val json = """{"version":8,"name":"rendered-route-test","sources":{},"layers":[
                    {"id":"bg","type":"background","paint":{"background-color":"${hex(bg)}"}}]}"""
                map.setStyle(Style.Builder().fromJson(json)) { style ->
                    try {
                        val refs = JSONObject().put("type", "FeatureCollection")
                            .put("features", JSONArray().put(lineFeature(own)).put(lineFeature(opposite)))
                        style.addSource(GeoJsonSource("ref-src", refs.toString()))
                        style.addLayer(LineLayer("ref-roads", "ref-src").withProperties(
                            lineColor(hex(REFERENCE)), lineWidth(REFERENCE_WIDTH_DP)))
                        RouteLayers.add(style, night) { style.addLayer(it) }
                        style.getSourceAs<GeoJsonSource>(RouteLayers.SRC_ROUTE)!!
                            .setGeoJson(RouteLayers.lines(listOf(latLon(route))))
                        style.getSourceAs<GeoJsonSource>(RouteLayers.TRAFFIC_SRC)!!
                            .setGeoJson(RouteLayers.traffic(bands.map { latLon(it.first) to it.second }))
                        val c = centre()
                        map.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder()
                            .target(LatLng(c.lat, c.lon)).zoom(zoom).tilt(tilt).bearing(bearing(c, ahead())).build()))
                        a.mapView.addOnDidBecomeIdleListener { idle.countDown() }
                        map.triggerRepaint()
                    } catch (t: Throwable) { error.set(t) } finally { ready.countDown() }
                }
            }
        }
        assertTrue("MapLibre did not load the test style", ready.await(60, TimeUnit.SECONDS))
        error.get()?.let { throw AssertionError("Building the scene failed", it) }
        assertTrue("Renderer never became idle", idle.await(60, TimeUnit.SECONDS))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        val shot = CountDownLatch(1)
        val result = AtomicReference<Rendered>()
        activity.scenario.onActivity { a ->
            a.mapView.getMapAsync { map ->
                val density = a.resources.displayMetrics.density.toDouble()
                fun samples(line: List<Geo>) = RenderJudge.densify(line, 5.0).map { (g, s) ->
                    val p = map.projection.toScreenLocation(LatLng(g.lat, g.lon))
                    Sample(g, s, Px(p.x, p.y))
                }
                val ownSamples = samples(own); val oppSamples = samples(opposite)
                map.snapshot { bitmap ->
                    try {
                        assertEquals("Snapshot size differs from the map view", a.mapView.width, bitmap.width)
                        assertEquals("Snapshot size differs from the map view", a.mapView.height, bitmap.height)
                        val pic = Picture(bitmap.width, bitmap.height, density) { x, y -> bitmap.getPixel(x, y) }
                        result.set(Rendered(pic, ownSamples, oppSamples, bitmap))
                    } catch (t: Throwable) { error.set(t) } finally { shot.countDown() }
                }
            }
        }
        assertTrue("No GPU snapshot", shot.await(30, TimeUnit.SECONDS))
        error.get()?.let { throw AssertionError("Snapshot failed", it) }
        assertTrue(view.get().width >= 300 && view.get().height >= 300)
        return result.get()
    }

    /** Saves the picture and the verdict where connectedAndroidTest collects them. */
    private fun record(r: Rendered, verdict: JSONObject) {
        val args = InstrumentationRegistry.getArguments()
        val dir = args.getString("additionalTestOutputDir")?.let(::File)
            ?: InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("render")!!
        dir.mkdirs()
        File(dir, name.methodName + ".png").outputStream().use { r.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(dir, name.methodName + ".json").writeText(verdict.put("test", name.methodName)
            .put("width_px", r.picture.width).put("height_px", r.picture.height)
            .put("density", r.picture.density).toString(1))
    }

    private fun json(v: RenderJudge.CarriagewayVerdict) = JSONObject()
        .put("pass", v.pass).put("reasons", JSONArray(v.reasons))
        .put("own_in_view", v.ownInView).put("own_inked", v.ownInked)
        .put("median_route_width_dp", v.medianWidthDp)
        .put("opposite_in_view", v.oppositeInView).put("opposite_testable", v.oppositeQualified)
        .put("opposite_visible", v.oppositeVisible)

    private fun carriagewayCase(night: Boolean, zoom: Double, tilt: Double) {
        val r = render(own, emptyList(), night, zoom, tilt)
        val v = RenderJudge.carriageway(r.picture, r.own, r.opposite, if (night) BG_NIGHT else BG_DAY, REFERENCE)
        record(r, json(v).put("checklist", "02.01").put("night", night).put("zoom", zoom).put("tilt", tilt))
        assertTrue(v.reasons.toString(), v.pass)
    }

    @Test fun pref02_01_dayTopDownZoom17RouteStaysOnOwnCarriageway() = carriagewayCase(false, 17.0, 0.0)

    @Test fun pref02_01_nightDrivingTiltZoom18RouteStaysOnOwnCarriageway() = carriagewayCase(true, 18.0, 50.0)

    @Test fun pref02_01_negativeControlRouteOnOppositeCarriagewayIsRejected() {
        val r = render(opposite, emptyList(), false, 17.0, 0.0)
        val v = RenderJudge.carriageway(r.picture, r.own, r.opposite, BG_DAY, REFERENCE)
        record(r, json(v).put("checklist", "02.01").put("negative_control", true))
        assertFalse("Judge accepted a route drawn on the opposite carriageway", v.pass)
        assertTrue(v.reasons.toString(), v.reasons.any { it.startsWith("route covers") })
    }

    private val trafficBands = listOf(
        Band(centreStation - 150, centreStation - 60, Expect.AMBER),
        Band(centreStation - 60, centreStation + 20, Expect.NEITHER),
        Band(centreStation + 20, centreStation + 150, Expect.RED),
    )

    private fun trafficCase(slowColor: String, heavyColor: String): Pair<Rendered, RenderJudge.TrafficVerdict> {
        val drawn = listOf(
            RenderJudge.slice(own, trafficBands[0].from, trafficBands[0].to) to slowColor,
            RenderJudge.slice(own, trafficBands[2].from, trafficBands[2].to) to heavyColor,
        )
        val r = render(own, drawn, false, 17.0, 0.0)
        return r to RenderJudge.traffic(r.picture, r.own, trafficBands)
    }

    private fun json(v: RenderJudge.TrafficVerdict) = JSONObject().put("pass", v.pass)
        .put("reasons", JSONArray(v.reasons))
        .put("bands", JSONArray().apply { v.counts.forEach { (b, c) -> put(JSONObject()
            .put("expect", b.expect.name).put("from_m", b.from).put("to_m", b.to)
            .put("expected_colour_samples", c.first).put("samples", c.second)) } })

    @Test fun pref02_03_slowIsAmberHeavyIsRedOnlyOnTheirSections() {
        val (r, v) = trafficCase(RouteLayers.SLOW_COLOR, RouteLayers.HEAVY_COLOR)
        // Traffic must also stay on the driver's own carriageway.
        val side = RenderJudge.carriageway(r.picture, r.own, r.opposite, BG_DAY, REFERENCE)
        record(r, json(v).put("checklist", "02.03").put("carriageway", json(side)))
        assertTrue(v.reasons.toString(), v.pass)
        assertTrue(side.reasons.toString(), side.pass)
    }

    @Test fun pref02_03_negativeControlSwappedTrafficColoursAreRejected() {
        val (r, v) = trafficCase(RouteLayers.HEAVY_COLOR, RouteLayers.SLOW_COLOR)
        record(r, json(v).put("checklist", "02.03").put("negative_control", true))
        assertFalse("Judge accepted red for slow and amber for heavy traffic", v.pass)
        assertEquals(v.reasons.toString(), 2, v.reasons.size)
    }

    private companion object {
        const val BG_DAY = 0xFFF2EFE9.toInt()
        const val BG_NIGHT = 0xFF182432.toInt()
        /** Mid grey: distinct from both backgrounds and every route colour. */
        const val REFERENCE = 0xFF8C98A6.toInt()
        const val REFERENCE_WIDTH_DP = 8f
        fun hex(c: Int) = "#%06X".format(c and 0xFFFFFF)
    }
}
