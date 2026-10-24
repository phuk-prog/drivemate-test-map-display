package com.drivemate.app.map

import android.content.Context
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.SymbolLayer
import kotlin.math.cos
import kotlin.math.pow

/**
 * The car symbol MapLibre's location component draws. Kept apart from MapController (which
 * needs the whole app) so the public rendered-map tests can draw this same file.
 *
 * [drawable] is the symbol's image, or null for the classic blue dot; [arrow] is the driving arrow.
 */
internal object CarMarker {
    /**
     * 02.04: at driving zooms the arrow covers a fixed width of road, between one UK lane (3.65 m)
     * and a carriageway, so it grows and shrinks with the map instead of staying one screen size.
     */
    const val ARROW_GROUND_M = 8.0
    /** Ground size is exact at mid-UK latitude; 50–58.7°N gives about 8.7–7.1 m. */
    const val REFERENCE_LAT = 54.0
    /** Driving zooms. Outside them the arrow keeps its size at the nearer end. */
    const val GROUND_FROM_ZOOM = 16.0
    const val GROUND_TO_ZOOM = 18.0
    /** ic_car_arrow's visible width (outline and shadow, x 9–39 of 48) in its 32 dp box. */
    const val ARROW_VISIBLE_DP = 32.0 * 30.0 / 48.0

    /** MapLibre's ground metres per dp at [zoom] and [lat] (512 dp world at zoom 0). */
    fun metresPerDp(zoom: Double, lat: Double = REFERENCE_LAT) =
        40_075_016.686 * cos(Math.toRadians(lat)) / (512.0 * 2.0.pow(zoom))

    /** Icon size multiplier that makes the arrow [ARROW_GROUND_M] wide at [zoom]. */
    fun arrowIconSize(zoom: Double): Double {
        val z = zoom.coerceIn(GROUND_FROM_ZOOM, GROUND_TO_ZOOM)
        return ARROW_GROUND_M / (ARROW_VISIBLE_DP * metresPerDp(z))
    }

    /** Base-2 exponential between the driving zooms: constant ground width, as map features scale. */
    private fun arrowSizeExpression(): Expression = Expression.interpolate(
        Expression.exponential(2f), Expression.zoom(),
        Expression.stop(GROUND_FROM_ZOOM, arrowIconSize(GROUND_FROM_ZOOM)),
        Expression.stop(GROUND_TO_ZOOM, arrowIconSize(GROUND_TO_ZOOM)),
    )

    /**
     * MapLibre can only scale its car symbol linearly over zoom 0–25.5, which keeps it about one
     * screen size. Replace that on its symbol layers; MapLibre resets it whenever it re-styles them
     * (activation, [restyle], every style load before the app's own style callback).
     */
    private fun scaleArrow(style: Style) {
        style.layers.filterIsInstance<SymbolLayer>()
            .filter { it.id.startsWith(MapLayerOrder.CAR_LAYER_PREFIX) }
            .forEach { it.setProperties(iconSize(arrowSizeExpression())) }
    }

    fun options(context: Context, drawable: Int?, arrow: Boolean): LocationComponentOptions {
        val b = LocationComponentOptions.builder(context)
        if (drawable != null) b.gpsDrawable(drawable).foregroundDrawable(drawable)
        // Big and easy to spot.
        // Avoid an enormous direction arrow at city overview zoom levels.
        return if (arrow) b.maxZoomIconScale(MapOverviewPolicy.ARROW_MAX_ZOOM_SCALE)
            .minZoomIconScale(MapOverviewPolicy.ARROW_MIN_ZOOM_SCALE).build()
        else b.maxZoomIconScale(1.5f).minZoomIconScale(1.0f).build()
    }

    /** Turns the car symbol on for [style] (after every style load). */
    fun show(lc: LocationComponent, context: Context, style: Style, drawable: Int?, arrow: Boolean) {
        if (!lc.isLocationComponentActivated) {
            lc.activateLocationComponent(
                LocationComponentActivationOptions.builder(context, style)
                    .useDefaultLocationEngine(false)
                    .locationComponentOptions(options(context, drawable, arrow))
                    .build()
            )
        }
        if (arrow) scaleArrow(style)
    }

    /** A different car symbol chosen in settings. */
    fun restyle(lc: LocationComponent, context: Context, style: Style, drawable: Int?, arrow: Boolean) {
        lc.applyStyle(options(context, drawable, arrow))
        if (arrow) scaleArrow(style)
    }
}
