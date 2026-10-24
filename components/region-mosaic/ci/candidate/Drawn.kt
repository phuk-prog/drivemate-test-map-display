package com.drivemate.app.data

/**
 * New app: the selected region plus the neighbours MapData.refreshNeighbours asks for, with the
 * view widened by a quarter exactly as it does. MapData itself needs Android, so this one line is
 * restated here; the private full-app run covers the wiring.
 */
internal object Drawn {
    fun regions(catalog: PublishedRegionCatalog, primary: PublishedRegionCatalog.Region, view: PublishedRegionCatalog.Bounds) =
        listOf(primary) + catalog.neighbours(primary, view.expanded(0.25))
}
