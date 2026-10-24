package com.drivemate.app.data

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner report (2026-10-24): at an overview zoom the map showed one square and blank around it.
 * For real driving-overview views over the published catalogue, every point that some published
 * region covers must be drawn. Runs unchanged on the old app (one region) and the new (mosaic);
 * [Drawn] says which regions each version draws.
 */
class PublishedViewDrawnTest {
    private val catalog = PublishedRegionCatalog.parse(
        javaClass.classLoader!!.getResource("region-catalog-2026-10-24.json")!!.readText())

    private fun check(west: Double, south: Double, east: Double, north: Double) {
        val view = PublishedRegionCatalog.Bounds(west, south, east, north)
        val lat = (south + north) / 2
        val lon = (west + east) / 2
        val primary = catalog.selectForViewport(lat, lon, view)!!
        val drawn = Drawn.regions(catalog, primary, view)
        var published = 0
        var shown = 0
        for (i in 0..20) for (j in 0..20) {
            val y = south + (north - south) * i / 20
            val x = west + (east - west) * j / 20
            if (catalog.regions.none { it.bounds.contains(y, x) }) continue
            published++
            if (drawn.any { it.bounds.contains(y, x) }) shown++
        }
        val share = 100.0 * shown / published
        println("VIEW $west,$south,$east,$north drawn=${drawn.map { it.id }} shown=$shown/$published (${"%.1f".format(share)}%)")
        assertTrue("Only $shown of $published published sample points drawn (${"%.1f".format(share)}%) by ${drawn.map { it.id }}",
            shown == published)
    }

    /** The owner's screenshot: Radcliffe to Bramhall, Swinton to Chadderton. */
    @Test fun manchesterOverviewFromTheOwnersScreenshot() = check(-2.45, 53.30, -2.05, 53.62)
    @Test fun m62ManchesterToLiverpool() = check(-3.00, 53.33, -2.20, 53.55)
    @Test fun leedsAndBradford() = check(-1.85, 53.70, -1.40, 53.88)
    @Test fun centralLondon() = check(-0.30, 51.42, 0.05, 51.58)
    @Test fun bristolAndBath() = check(-2.75, 51.32, -2.25, 51.52)
    @Test fun englandScotlandBorderAtCarlisle() = check(-3.10, 54.82, -2.75, 55.05)
}
