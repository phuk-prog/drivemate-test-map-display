package com.drivemate.app.map

import android.content.Context
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.maps.Style

/**
 * The car symbol MapLibre's location component draws. Kept apart from MapController (which
 * needs the whole app) so the public rendered-map tests can draw this same file.
 *
 * [drawable] is the symbol's image, or null for the classic blue dot; [arrow] is the driving arrow.
 */
internal object CarMarker {
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
    }

    /** A different car symbol chosen in settings. */
    fun restyle(lc: LocationComponent, context: Context, drawable: Int?, arrow: Boolean) {
        lc.applyStyle(options(context, drawable, arrow))
    }
}
