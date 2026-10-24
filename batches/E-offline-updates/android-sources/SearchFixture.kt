package com.drivemate.app.data

/** Explicit catalogue-provider fake for UI tests; catalogue sorting/matching tests run separately. */
internal object MapData {
    fun offlineRegionOptions(query: String): List<PublishedRegionCatalog.Region> = emptyList()
}
internal object RegionalLiveMap { const val GENERATION_MANIFEST = "installed-manifest.json" }
