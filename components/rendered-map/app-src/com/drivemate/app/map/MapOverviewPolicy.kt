package com.drivemate.app.map

/**
 * Display-only limits for a legible map overview; these are engineering UI decisions.
 * They do not assert speed limits, road geometry, sign truth or live traffic certainty.
 *
 * An incomplete regional source must be disclosed rather than silently painted as
 * complete UK coverage. All comparisons use bounds supplied by the trusted catalogue.
 */
internal object MapOverviewPolicy {
    const val ARROW_ICON_DP = 32
    const val ARROW_MIN_ZOOM_SCALE = 0.8f
    const val ARROW_MAX_ZOOM_SCALE = 1.25f
    const val MIN_HAZARD_ICON_ZOOM = 11f
    const val ALLOW_HAZARD_OVERLAP = false
    const val IGNORE_HAZARD_COLLISIONS = false

    fun outsideRegion(
        viewportWest: Double, viewportSouth: Double, viewportEast: Double, viewportNorth: Double,
        regionWest: Double, regionSouth: Double, regionEast: Double, regionNorth: Double,
    ): Boolean {
        val values = listOf(viewportWest, viewportSouth, viewportEast, viewportNorth,
            regionWest, regionSouth, regionEast, regionNorth)
        // Invalid or wrapped viewports must never be labelled fully covered.
        if (values.any { !it.isFinite() } ||
            viewportWest > viewportEast || viewportSouth > viewportNorth ||
            regionWest >= regionEast || regionSouth >= regionNorth) return true
        return viewportWest < regionWest || viewportSouth < regionSouth ||
            viewportEast > regionEast || viewportNorth > regionNorth
    }
}
