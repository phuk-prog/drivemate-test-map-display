package com.drivemate.app.data

/** Old app (main 10a2e97): only the selected region is drawn. */
internal object Drawn {
    fun regions(catalog: PublishedRegionCatalog, primary: PublishedRegionCatalog.Region, view: PublishedRegionCatalog.Bounds) =
        listOf(primary)
}
