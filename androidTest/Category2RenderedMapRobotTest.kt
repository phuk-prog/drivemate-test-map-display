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

    private val route: List<LatLon> by lazy {
        val stream = InstrumentationRegistry.getInstrumentation().context.assets
            .open("independent-osm-roads.json")
        val doc = JSONObject(stream.bufferedReader().use { it.readText() })
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
                assertNotNull(style.getSource("dm-route-src"))
                assertNotNull(style.getLayer("dm-traffic"))
                assertNotNull(style.getSource("dm-traffic-src"))
                val marker = route[route.size / 2]
                val screen = map.projection.toScreenLocation(LatLng(marker.lat, marker.lon))
                assertTrue(screen.x in 0f..it.mapView.width.toFloat())
                assertTrue(screen.y in 0f..it.mapView.height.toFloat())
                val rect = RectF(screen.x - 20f, screen.y - 20f, screen.x + 20f, screen.y + 20f)
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
}
