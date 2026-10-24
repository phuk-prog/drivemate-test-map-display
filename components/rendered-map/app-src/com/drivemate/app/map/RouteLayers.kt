package com.drivemate.app.map

import com.drivemate.app.nav.LatLon
import com.drivemate.app.nav.RoutePalette
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.maps.Style

/**
 * The route line, the part already driven and the traffic bands, exactly as the phone map draws
 * them. Kept apart from MapController (which needs the whole app) so the public rendered-map
 * tests can draw this same file on an emulator, not a copy of it.
 */
internal object RouteLayers {
    const val SRC_ROUTE = "dm-route-src"
    const val SRC_DRIVEN = "dm-driven-src"
    const val TRAFFIC_SRC = "dm-traffic-src"
    const val TRAFFIC_LAYER = "dm-traffic"
    const val EMPTY = """{"type":"FeatureCollection","features":[]}"""

    /** 02.03: amber for slower traffic, red for heavy congestion. */
    const val SLOW_COLOR = "#F9AB00"
    const val HEAVY_COLOR = "#D93025"

    /** Route stack ids, bottom to top. The traffic band sits on the route fill it describes. */
    val LAYER_IDS = listOf("dm-route-contrast", "dm-route-casing", "dm-route", "dm-driven-casing", "dm-driven", TRAFFIC_LAYER)

    fun width(line: RoutePalette.Line): Expression = Expression.interpolate(
        Expression.exponential(1.6f), Expression.zoom(),
        Expression.stop(12, line.z12), Expression.stop(16, line.z16), Expression.stop(19, line.z19),
    )

    /** Adds the route, driven and traffic sources and their layers through [add] (under the car on the phone). */
    fun add(s: Style, night: Boolean, add: (Layer) -> Unit) {
        // The route keeps "line metrics" so we can colour it by how far along it you are.
        s.addSource(GeoJsonSource(SRC_ROUTE, EMPTY))
        s.addSource(GeoJsonSource(SRC_DRIVEN, EMPTY))
        s.addSource(GeoJsonSource(TRAFFIC_SRC, EMPTY))
        val colors = RoutePalette.colors(night)
        fun line(id: String, source: String, color: String, w: RoutePalette.Line) = add(
            LineLayer(id, source).withProperties(
                lineColor(color), lineWidth(width(w)),
                lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
            )
        )
        line("dm-route-contrast", SRC_ROUTE, RoutePalette.hex(colors.halo), RoutePalette.Line.HALO)
        // Widths grow with zoom (RoutePalette.Line). Zoom-varying widths crashed some graphics
        // drivers only together with a colour gradient, which the route no longer uses.
        line("dm-route-casing", SRC_ROUTE, RoutePalette.hex(colors.casing), RoutePalette.Line.CASING)
        line("dm-route", SRC_ROUTE, RoutePalette.hex(colors.fill), RoutePalette.Line.FILL)
        // The part already driven, drawn grey over the route. (A separate line rather than a
        // colour gradient along the route: gradients crashed the map renderer on some graphics.)
        line("dm-driven-casing", SRC_DRIVEN, RoutePalette.hex(colors.drivenCasing), RoutePalette.Line.CASING)
        line("dm-driven", SRC_DRIVEN, RoutePalette.hex(colors.driven), RoutePalette.Line.FILL)
        // Traffic bands use only the driven route centreline, not side roads or raster squares.
        // TrafficRouteOverlay rejects stale, directionless and non-route external reports.
        add(
            LineLayer(TRAFFIC_LAYER, TRAFFIC_SRC).withProperties(
                lineColor(Expression.get("color")), lineWidth(width(RoutePalette.Line.FILL)),
                lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
            )
        )
    }

    fun lines(list: List<List<LatLon>>): String {
        val features = list.filter { it.size >= 2 }.joinToString(",") { line ->
            val coords = line.joinToString(",") { "[${it.lon},${it.lat}]" }
            """{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[$coords]}}"""
        }
        return """{"type":"FeatureCollection","features":[$features]}"""
    }

    /** Traffic bands as GeoJSON: each band's points along the route with its colour. */
    fun traffic(bands: List<Pair<List<LatLon>, String>>): String {
        val features = org.json.JSONArray()
        bands.forEach { (points, color) ->
            if (points.size < 2) return@forEach
            val coordinates = org.json.JSONArray()
            points.forEach { p -> coordinates.put(org.json.JSONArray().put(p.lon).put(p.lat)) }
            features.put(org.json.JSONObject()
                .put("type", "Feature")
                .put("properties", org.json.JSONObject().put("color", color))
                .put("geometry", org.json.JSONObject().put("type", "LineString").put("coordinates", coordinates)))
        }
        return org.json.JSONObject().put("type", "FeatureCollection").put("features", features).toString()
    }
}
