package com.drivemate.render

import android.Manifest
import android.graphics.Bitmap
import android.location.Location
import android.os.SystemClock
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.drivemate.app.map.CarMarker
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
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.cos
import kotlin.math.sin

/**
 * Checklist 02.04: the vehicle arrow is centred on your carriageway, points along the road and is
 * proportional to the map (owner's definition: between one lane, 3.65 m, and the whole
 * carriageway on the ground at driving zooms 16–18).
 *
 * The arrow is DriveMate's ic_car_arrow drawn by MapLibre's location component exactly as the app
 * sets it up: CarMarker.show (published byte for byte) in GPS render mode with the camera driven by
 * the app, not the component. Run once per phase: the app before (baseline) and after (candidate)
 * the 02.04 change. The fix is placed on Princess Road's north-east-bound carriageway centreline
 * (OSM way 526771299, 3 lanes) and faces along it; the judge finds the arrow by comparing renders
 * with and without it. The route line is left out so nothing else changes between the two.
 */
@RunWith(AndroidJUnit4::class)
class CarArrowTest {
    @get:Rule val activity = ActivityScenarioRule(RenderHostActivity::class.java)
    @get:Rule val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.ACCESS_FINE_LOCATION)
    @get:Rule val name = TestName()

    private val phase = InstrumentationRegistry.getArguments().getString("phase") ?: "candidate"

    private val fixture: JSONObject by lazy {
        InstrumentationRegistry.getInstrumentation().context.assets.open("princess-road-a5103-osm.json")
            .bufferedReader().use { JSONObject(it.readText()) }
    }
    private fun carriageway(key: String): JSONObject = fixture.getJSONObject("carriageways").getJSONObject(key)
    private fun line(key: String): List<Geo> {
        val a = carriageway(key).getJSONArray("coordinates")
        return (0 until a.length()).map { Geo(a.getJSONArray(it).getDouble(1), a.getJSONArray(it).getDouble(0)) }
    }
    private val own by lazy { line("own") }
    private val opposite by lazy { line("opposite") }
    private val lanes by lazy { carriageway("own").getJSONObject("tags").getString("lanes").toInt() }

    private val station = 300.0
    private fun pointAt(s: Double) = RenderJudge.densify(own, 1.0).first { it.second >= s }.first
    private fun bearing(a: Geo, b: Geo): Double {
        val p1 = Math.toRadians(a.lat); val p2 = Math.toRadians(b.lat); val dl = Math.toRadians(b.lon - a.lon)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360
    }
    /** Direction of travel at the fix, from the OSM centreline 10 m either side. */
    private fun roadBearing() = bearing(pointAt(station - 10), pointAt(station + 10))

    /** [p] moved [metres] to the right of [heading] (flat-earth, fine over a few metres). */
    private fun offsetRight(p: Geo, heading: Double, metres: Double): Geo {
        val t = Math.toRadians(heading + 90)
        return Geo(p.lat + metres * cos(t) / 111_320.0, p.lon + metres * sin(t) / (111_320.0 * cos(Math.toRadians(p.lat))))
    }

    private class Shot(val picture: Picture, val bitmap: Bitmap)
    private class Rendered(val without: Shot, val with: Shot, val own: List<Sample>, val opposite: List<Sample>)

    private fun feature(points: List<Geo>) = JSONObject().put("type", "Feature").put("properties", JSONObject())
        .put("geometry", JSONObject().put("type", "LineString").put("coordinates",
            JSONArray().apply { points.forEach { put(JSONArray().put(it.lon).put(it.lat)) } }))

    private fun loadScene(map: MapLibreMap, style: Style, zoom: Double, cameraBearing: Double) {
        val refs = JSONObject().put("type", "FeatureCollection")
            .put("features", JSONArray().put(feature(own)).put(feature(opposite)))
        style.addSource(GeoJsonSource("ref-src", refs.toString()))
        style.addLayer(LineLayer("ref-roads", "ref-src").withProperties(lineColor(hex(REFERENCE)), lineWidth(8f)))
        val c = pointAt(station)
        map.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder()
            .target(LatLng(c.lat, c.lon)).zoom(zoom).tilt(0.0).bearing(cameraBearing).build()))
    }

    private val styleJson = """{"version":8,"name":"car-arrow-test","sources":{},"layers":[
        {"id":"bg","type":"background","paint":{"background-color":"${hex(BG)}"}}]}"""

    /** Waits for the renderer to settle, then takes a GPU snapshot. */
    private fun snapshot(): Shot {
        val idle = CountDownLatch(1)
        activity.scenario.onActivity { a ->
            a.mapView.addOnDidBecomeIdleListener { idle.countDown() }
            a.mapView.getMapAsync { it.triggerRepaint() }
        }
        assertTrue("Renderer never became idle", idle.await(60, TimeUnit.SECONDS))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val done = CountDownLatch(1)
        val shot = AtomicReference<Shot>()
        val error = AtomicReference<Throwable>()
        activity.scenario.onActivity { a ->
            a.mapView.getMapAsync { map ->
                val density = a.resources.displayMetrics.density.toDouble()
                map.snapshot { b ->
                    try {
                        assertEquals(a.mapView.width, b.width); assertEquals(a.mapView.height, b.height)
                        shot.set(Shot(Picture(b.width, b.height, density) { x, y -> b.getPixel(x, y) }, b))
                    } catch (t: Throwable) { error.set(t) } finally { done.countDown() }
                }
            }
        }
        assertTrue("No GPU snapshot", done.await(30, TimeUnit.SECONDS))
        error.get()?.let { throw AssertionError("Snapshot failed", it) }
        return shot.get()
    }

    /**
     * Renders the roads, snapshots them, then shows the app's car arrow at [at] facing [heading]
     * and snapshots again. [reloadStyle] loads a fresh style between, as the app does for night
     * mode or a new map, and shows the arrow again the way MapController.enableLocation does.
     */
    private fun render(zoom: Double, cameraBearing: Double, at: Geo, heading: Double, reloadStyle: Boolean = false): Rendered {
        val ready = CountDownLatch(1)
        val error = AtomicReference<Throwable>()
        activity.scenario.onActivity { a ->
            a.mapView.getMapAsync { map ->
                map.setStyle(Style.Builder().fromJson(styleJson)) { style ->
                    try {
                        loadScene(map, style, zoom, cameraBearing)
                        val lc = map.locationComponent
                        CarMarker.show(lc, a, style, R.drawable.ic_car_arrow, true)
                        lc.isLocationComponentEnabled = false
                    } catch (t: Throwable) { error.set(t) } finally { ready.countDown() }
                }
            }
        }
        assertTrue("Style did not load", ready.await(60, TimeUnit.SECONDS))
        error.get()?.let { throw AssertionError("Scene failed", it) }
        val without = snapshot()

        val shown = CountDownLatch(1)
        activity.scenario.onActivity { a ->
            a.mapView.getMapAsync { map ->
                try {
                    val lc = map.locationComponent
                    lc.isLocationComponentEnabled = true
                    // As MapController.applyFollow while navigating: the app moves the camera itself.
                    lc.cameraMode = CameraMode.NONE
                    lc.renderMode = RenderMode.GPS
                    lc.forceLocationUpdate(Location("synthetic-fix-on-osm-centreline").apply {
                        latitude = at.lat; longitude = at.lon; bearing = heading.toFloat(); speed = 10f
                        time = System.currentTimeMillis(); elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                    })
                } catch (t: Throwable) { error.set(t) } finally { shown.countDown() }
            }
        }
        assertTrue(shown.await(10, TimeUnit.SECONDS))
        error.get()?.let { throw AssertionError("Showing the arrow failed", it) }
        if (reloadStyle) {
            val reloaded = CountDownLatch(1)
            activity.scenario.onActivity { a ->
                a.mapView.getMapAsync { map ->
                    map.setStyle(Style.Builder().fromJson(styleJson)) { style ->
                        try {
                            loadScene(map, style, zoom, cameraBearing)
                            CarMarker.show(map.locationComponent, a, style, R.drawable.ic_car_arrow, true)
                        } catch (t: Throwable) { error.set(t) } finally { reloaded.countDown() }
                    }
                }
            }
            assertTrue("Reloaded style did not load", reloaded.await(60, TimeUnit.SECONDS))
            error.get()?.let { throw AssertionError("Style reload failed", it) }
        }
        // Let the location component finish animating to the fix and its bearing.
        Thread.sleep(2500)
        val with = snapshot()

        var ownSamples = emptyList<Sample>(); var oppSamples = emptyList<Sample>()
        val projected = CountDownLatch(1)
        activity.scenario.onActivity { a ->
            a.mapView.getMapAsync { map ->
                fun samples(l: List<Geo>) = RenderJudge.densify(l, 5.0).map { (g, s) ->
                    val p = map.projection.toScreenLocation(LatLng(g.lat, g.lon)); Sample(g, s, Px(p.x, p.y))
                }
                ownSamples = samples(own); oppSamples = samples(opposite); projected.countDown()
            }
        }
        assertTrue(projected.await(10, TimeUnit.SECONDS))
        return Rendered(without, with, ownSamples, oppSamples)
    }

    private fun judge(r: Rendered, extra: JSONObject): RenderJudge.ArrowVerdict {
        val mask = RenderJudge.added(r.without.picture, r.with.picture)
        val v = RenderJudge.arrow(mask, r.own, lanes)
        val args = InstrumentationRegistry.getArguments()
        val dir = args.getString("additionalTestOutputDir")?.let(::File)
            ?: InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("render")!!
        dir.mkdirs()
        val base = "$phase-${name.methodName}"
        File(dir, "$base.png").outputStream().use { r.with.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(dir, "$base.json").writeText(extra.put("test", name.methodName).put("phase", phase)
            .put("checklist", "02.04").put("pass", v.pass).put("reasons", JSONArray(v.reasons))
            .put("arrow_width_m", v.widthM).put("arrow_offset_m", v.offsetM)
            .put("arrow_heading_screen_deg", v.headingDeg).put("road_heading_screen_deg", v.roadDeg)
            .put("mirror_match", v.symmetry).put("arrow_px", v.pixels).put("lanes", lanes)
            .put("allowed_width_m", JSONArray().put(RenderJudge.LANE_M).put(lanes * RenderJudge.LANE_M))
            .put("width_px", r.with.picture.width).put("density", r.with.picture.density).toString(1))
        return v
    }

    private fun northUp(zoom: Double) {
        val road = roadBearing()
        val v = judge(render(zoom, 0.0, pointAt(station), road), JSONObject().put("zoom", zoom).put("camera", "north-up"))
        assertTrue(v.reasons.toString(), v.pass)
    }

    @Test fun pref02_04_northUpZoom16ArrowOnCarriagewayAlongRoadLaneToCarriagewayWide() = northUp(16.0)
    @Test fun pref02_04_northUpZoom17ArrowOnCarriagewayAlongRoadLaneToCarriagewayWide() = northUp(17.0)
    @Test fun pref02_04_northUpZoom18ArrowOnCarriagewayAlongRoadLaneToCarriagewayWide() = northUp(18.0)

    @Test fun pref02_04_drivingViewZoom17ArrowPointsUpTheRoad() {
        val road = roadBearing()
        val v = judge(render(17.0, road, pointAt(station), road), JSONObject().put("zoom", 17.0).put("camera", "along road"))
        assertTrue(v.reasons.toString(), v.pass)
        assertTrue("Arrow not pointing up the screen: ${v.headingDeg}", RenderJudge.angleBetween(v.headingDeg, 0.0) <= RenderJudge.MAX_ARROW_HEADING_DEG)
    }

    @Test fun pref02_04_zoom16SizeSurvivesAStyleReload() {
        val road = roadBearing()
        val v = judge(render(16.0, 0.0, pointAt(station), road, reloadStyle = true),
            JSONObject().put("zoom", 16.0).put("camera", "north-up").put("style_reloaded", true))
        assertTrue(v.reasons.toString(), v.pass)
    }

    @Test fun pref02_04_negativeControlArrowFacingAgainstTrafficIsRejected() {
        val road = roadBearing()
        val v = judge(render(17.0, 0.0, pointAt(station), (road + 180) % 360),
            JSONObject().put("zoom", 17.0).put("negative_control", "reversed heading"))
        assertFalse("Judge accepted an arrow facing against traffic", v.pass)
        assertTrue(v.reasons.toString(), v.reasons.any { it.contains("away from the road") })
    }

    @Test fun pref02_04_negativeControlArrowOffTheCentrelineIsRejected() {
        val road = roadBearing()
        // 5 m towards the central reservation: between the two carriageways.
        val here = pointAt(station)
        val nearestOpposite = RenderJudge.densify(opposite, 1.0).minBy { RenderJudge.metres(it.first, here) }.first
        val toOpposite = if (RenderJudge.metres(offsetRight(here, road, 5.0), nearestOpposite) <
            RenderJudge.metres(here, nearestOpposite)) 5.0 else -5.0
        val v = judge(render(17.0, 0.0, offsetRight(here, road, toOpposite), road),
            JSONObject().put("zoom", 17.0).put("negative_control", "5 m off centreline"))
        assertFalse("Judge accepted an arrow 5 m off the centreline", v.pass)
        assertTrue(v.reasons.toString(), v.reasons.any { it.contains("off the carriageway centreline") })
    }

    private companion object {
        const val BG = 0xFFF2EFE9.toInt()
        const val REFERENCE = 0xFF8C98A6.toInt()
        fun hex(c: Int) = "#%06X".format(c and 0xFFFFFF)
    }
}
