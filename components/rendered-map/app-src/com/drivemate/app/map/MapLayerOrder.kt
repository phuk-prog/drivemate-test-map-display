package com.drivemate.app.map

/** Where DriveMate's own map layers go, so nothing we draw ever covers the car arrow. */
internal object MapLayerOrder {
    /** MapLibre's location (car) layers all start with this id prefix (checked in MapLibre 13.6.1). */
    const val CAR_LAYER_PREFIX = "mapbox-location-"

    /** The lowest car layer in [layerIds] (bottom to top order), or null when the car isn't on the map yet. */
    fun firstCarLayer(layerIds: List<String>): String? = layerIds.firstOrNull { it.startsWith(CAR_LAYER_PREFIX) }
}
