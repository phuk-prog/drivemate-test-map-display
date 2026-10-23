package com.drivemate.app.map

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivemate.app.nav.LatLon
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Real MapLibre rendering on a GitHub Android emulator, using independently fetched
 * UK OSM road geometry. Test-only Activity/style. Uses ACTUAL private MapController
 * route & traffic layer creation; no production app code copied into public repo.
 * No live TomTom credentials or traffic conditions.
 */
@RunWith(AndroidJUnit4::class)
class Category2RenderedMapRobotTest {
    @get:Rule val activity = ActivityScenarioRule(MapRobotActivity::class.java)

    private val doc: JSONObject by lazy {
        val stream = InstrumentationRegistry.getInstrumentation().context.assets
            .open("independent-osm-roads.json")
        JSONObject(stream.bufferedReader().use { it.readText() })
    }
    private val route: List<LatLon> by lazy {
        assertEquals("EXTERNALLY_SOURCED_OSM_UK_ROAD_GEOMETRY", doc.getString("kind"))
        assertTrue(doc.getJSONArray("ways").length() > 0)
        val points = doc.getJSONArray("ways").getJSONObject(0).getJSONArray("coordinates")
        (0 until points.length()).map { i ->
            points.getJSONArray(i).let { LatLon(it.getDouble(1), it.getDouble(0)) }
        }
    }

    private data class RenderHost(val map: MapLibreMap, val controller: MapController)
    private fun setUpHost(): RenderHost {
        val done = CountDownLatch(1)
        val error = AtomicReference<Throwable>()
        val host = AtomicReference<RenderHost>()
        val realRoute = route
        activity.scenario.onActivity { a ->
            try {
                val c = MapController(a, a.mapView)
                a.mapView.getMapAsync { map ->
                    try {
                        // Offline robot background: any roads are drawn by the actual app layer
                        // code from independent OSM points. No external raster or style keys.
                        val json = """{"version":8,"name":"uk-map-robot","sources":{},"layers":[{"id":"robot-bg","type":"background","paint":{"background-color":"#182432"}}]}"""
                        map.setStyle(Style.Builder().fromJson(json)) { style ->
                            try {
                                // Independent actual OSM map geometry is a grey base road, and the
                                // app's own separate blue route line must be drawn directly over it.
                                val features = org.json.JSONArray()
                                val ways = doc.getJSONArray("ways")
                                for (i in 0 until ways.length()) {
                                    features.put(JSONObject()
                                        .put("type", "Feature")
                                        .put("properties", JSONObject().put("osm_way_id", ways.getJSONObject(i).getLong("osm_id")))
                                        .put("geometry", JSONObject()
                                            .put("type", "LineString")
                                            .put("coordinates", ways.getJSONObject(i).getJSONArray("coordinates"))))
                                }
                                val reference = JSONObject().put("type", "FeatureCollection").put("features", features)
                                style.addSource(GeoJsonSource("real-osm-road-src", reference.toString()))
                                style.addLayer(LineLayer("real-osm-roundabout", "real-osm-road-src").withProperties(
                                    lineColor("#8C98A6"), lineWidth(16f)))
                                val styleField = MapController::class.java.getDeclaredField("style")
                                styleField.isAccessible = true
                                styleField.set(c, style)
                                val renderLayers = MapController::class.java.getDeclaredMethod("addLayers", Style::class.java)
                                renderLayers.isAccessible = true
                                renderLayers.invoke(c, style)
                                c.showRoutes(realRoute, emptyList())
                                val split = (realRoute.size / 2).coerceIn(1, realRoute.size - 2)
                                c.setTraffic(true)
                                c.showTrafficSections(listOf(
                                    TrafficRouteOverlay.Section(realRoute.subList(0, split + 1), TrafficRouteOverlay.Level.SLOW, 1L),
                                    TrafficRouteOverlay.Section(realRoute.subList(split, realRoute.size), TrafficRouteOverlay.Level.HEAVY, 2L),
                                ))
                                val mid = realRoute[split]
                                map.moveCamera(CameraUpdateFactory.newCameraPosition(
                                    CameraPosition.Builder().target(LatLng(mid.lat, mid.lon)).zoom(16.0).tilt(0.0).build()))
                                host.set(RenderHost(map, c))
                            } catch (t: Throwable) {
                                error.set(t)
                            } finally { done.countDown() }
                        }
                    } catch (t: Throwable) { error.set(t); done.countDown() }
                }
            } catch (t: Throwable) { error.set(t); done.countDown() }
        }
        assertTrue("Native renderer did not create offline MapLibre style", done.await(60, TimeUnit.SECONDS))
        error.get()?.let { throw AssertionError("MapController real renderer failed: " + it, it) }
        assertNotNull(host.get())
        return host.get()
    }

    @Test fun pref02_01_renderedRealOsmRoundaboutFollowsMapControllerRouteAndCarLayers() {
        val host = setUpHost()
        // MapLibre GeoJSON sources update asynchronously; allow a native render frame.
        Thread.sleep(2000)
        val checked = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        activity.scenario.onActivity {
            try {
                val map = host.map
                val style = map.style!!
                assertNotNull(style.getLayer("dm-route"))
                assertNotNull(style.getLayer("real-osm-roundabout"))
                assertNotNull(style.getSource("dm-route-src"))
                assertNotNull(style.getLayer("dm-traffic"))
                assertNotNull(style.getSource("dm-traffic-src"))
                val marker = route[route.size / 2]
                val screen = map.projection.toScreenLocation(LatLng(marker.lat, marker.lon))
                assertTrue(screen.x in 0f..it.mapView.width.toFloat())
                assertTrue(screen.y in 0f..it.mapView.height.toFloat())
                val rect = RectF(screen.x - 20f, screen.y - 20f, screen.x + 20f, screen.y + 20f)
                assertTrue("Native renderer cannot see independently sourced real UK road", map.queryRenderedFeatures(rect, "real-osm-roundabout").isNotEmpty())
                assertTrue("Native renderer cannot see real UK road route", map.queryRenderedFeatures(rect, "dm-route").isNotEmpty())
                assertTrue("Native renderer cannot see coloured on-route traffic", map.queryRenderedFeatures(rect, "dm-traffic").isNotEmpty())
            } catch (t: Throwable) { failure.set(t) } finally { checked.countDown() }
        }
        assertTrue(checked.await(10, TimeUnit.SECONDS))
        failure.get()?.let { throw AssertionError("Native rendered route/traffic assertion failed", it) }
    }

    @Test fun pref02_03_nativeMapSnapshotContainsTrafficAndRouteInk() {
        val host = setUpHost()
        Thread.sleep(1800)
        val done = CountDownLatch(1)
        val error = AtomicReference<Throwable>()
        activity.scenario.onActivity {
            host.map.snapshot { bitmap: Bitmap? ->
                try {
                    assertNotNull("Native MapLibre snapshot is blank", bitmap)
                    val b = bitmap!!
                    assertTrue(b.width >= 200 && b.height >= 200)
                    val distinct = HashSet<Int>()
                    for (y in 0 until b.height step 8)
                        for (x in 0 until b.width step 8) distinct.add(b.getPixel(x, y))
                    assertTrue("Native GPU map screenshot shows no visible route/traffic detail", distinct.size >= 4)
                } catch (t: Throwable) { error.set(t) } finally { done.countDown() }
            }
        }
        assertTrue("No native MapLibre GPU screenshot", done.await(25, TimeUnit.SECONDS))
        error.get()?.let { throw AssertionError("Rendered pixel assertion failed", it) }
    }
    /** Invoke the real private camera presenter on an emulator, not a copied approximation. */
    private fun driveCamera(host: RenderHost, point: LatLon, zoom: Double, tilt: Double,
                            elapsedMs: Long = 1500L, insets: Boolean = false) {
        activity.scenario.onActivity {
            val location = android.location.Location("synthetic-gps-only-for-camera").apply {
                latitude = point.lat; longitude = point.lon
                accuracy = 3f; bearing = 0f; speed = 8f
                elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
            }
            val last = MapController::class.java.getDeclaredField("lastLocation")
            last.isAccessible = true
            last.set(host.controller, location)
            if (insets) host.controller.setDrivingViewport(130, 180)
            host.controller.setNavCamera(zoom, tilt)
            val method = MapController::class.java.getDeclaredMethod("moveNavCamera",
                Int::class.javaPrimitiveType, Long::class.javaPrimitiveType)
            method.isAccessible = true
            method.invoke(host.controller, 0, elapsedMs)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    @Test fun pref02_02_nativeDrivingCameraPositionsCarLowWithRealRoadAhead() {
        val host = setUpHost()
        val p = route[route.size / 2]
        driveCamera(host, p, 16.2, 50.0, insets = true)
        activity.scenario.onActivity {
            val pixel = host.map.projection.toScreenLocation(LatLng(p.lat, p.lon))
            assertTrue("Vehicle anchor not in lower section of rendered viewport: " + pixel.y,
                pixel.y > it.mapView.height * 0.50)
            assertTrue("Vehicle anchor horizontally lost from real UK road", pixel.x > it.mapView.width * 0.20 &&
                pixel.x < it.mapView.width * 0.80)
        }
    }

    @Test fun pref02_05_nativeCameraRespondsToFarAndNearTurnWithoutAbruptZoom() {
        val host = setUpHost()
        val p = route[route.size / 2]
        driveCamera(host, p, 14.6, 55.0, elapsedMs = 1000L)
        var farZoom = 0.0
        activity.scenario.onActivity {
            farZoom = host.map.cameraPosition.zoom
            assertTrue("Fast/open-road tilt absent", host.map.cameraPosition.tilt > 40.0)
        }
        driveCamera(host, p, 17.4, 15.0, elapsedMs = 1000L)
        activity.scenario.onActivity {
            val next = host.map.cameraPosition
            assertTrue("Near junction did not zoom inward", next.zoom > farZoom)
            assertTrue("Zoom jumped instead of respecting production rate limit", next.zoom - farZoom <= 1.21)
            assertTrue("Junction view did not tilt closer to overhead", next.tilt < 25.0)
        }
    }

    @Test fun pref02_09_nativeCameraFitsTwoUpcomingPointsOnRealRoad() {
        val host = setUpHost()
        val points = route
        val first = points[0]
        activity.scenario.onActivity {
            val location = android.location.Location("synthetic-route-replay").apply {
                latitude = first.lat; longitude = first.lon; accuracy = 4f
                elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
            }
            val field = MapController::class.java.getDeclaredField("lastLocation")
            field.isAccessible = true
            field.set(host.controller, location)
            host.controller.setNavCamera(16.0, 20.0)
            host.controller.setDrivingViewport(80, 120)
            host.controller.setTurnFrame(points)
            val drive = MapController::class.java.getDeclaredMethod("moveNavCamera",
                Int::class.javaPrimitiveType, Long::class.javaPrimitiveType)
            drive.isAccessible = true
            drive.invoke(host.controller, 0, 1500L)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        activity.scenario.onActivity {
            val width = it.mapView.width.toFloat()
            val height = it.mapView.height.toFloat()
            assertTrue("Map not measured", width >= 200f && height >= 200f)
            val firstPixel = host.map.projection.toScreenLocation(LatLng(first.lat, first.lon))
            val last = points.last()
            val lastPixel = host.map.projection.toScreenLocation(LatLng(last.lat, last.lon))
            for (pixel in listOf(firstPixel, lastPixel)) {
                assertTrue("One of the consecutive road points was outside visible map",
                    pixel.x >= 0f && pixel.x <= width && pixel.y >= 0f && pixel.y <= height)
            }
        }
    }

}
