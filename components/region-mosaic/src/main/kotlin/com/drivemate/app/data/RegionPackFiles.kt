package com.drivemate.app.data

import java.io.File

/**
 * Which downloaded region file to draw, download or delete. Plain rules with no Android code, so
 * the public test suite compiles this exact file. Names are `<release tag>~<sha256>.pmtiles`.
 */
internal object RegionPackFiles {
    private val FILE = Regex("^(map-data-region-([a-z0-9][a-z0-9-]{0,79})-r[0-9]{1,20}(?:-[a-z0-9]{1,20})*)~([0-9a-f]{64})\\.pmtiles$")

    fun isRegionFile(name: String) = FILE.matches(name)

    fun fileName(region: PublishedRegionCatalog.Region) = "${region.map.tag}~${region.map.sha256}.pmtiles"

    /** The release tag a downloaded file came from. */
    fun tagOf(file: File): String? = FILE.matchEntire(file.name)?.groupValues?.get(1)

    private fun idOf(name: String): String? = FILE.matchEntire(name)?.groupValues?.get(2)

    /** The file for [region]: its current version, else the newest older one, else null. */
    fun choose(region: PublishedRegionCatalog.Region, files: List<File>): File? {
        files.firstOrNull { it.name == fileName(region) }?.let { return it }
        return files.filter { idOf(it.name) == region.id }.maxByOrNull { it.lastModified() }
    }

    /** Regions whose current version is not on the phone yet. */
    fun missing(catalog: PublishedRegionCatalog, names: Set<String>) =
        catalog.regions.filter { fileName(it) !in names }

    /** Files to delete: older versions whose current file is complete, and regions no longer published. */
    fun stale(catalog: PublishedRegionCatalog, names: Set<String>): Set<String> {
        val current = catalog.regions.associate { it.id to fileName(it) }
        return names.filter { name ->
            val id = idOf(name) ?: return@filter false
            val want = current[id]
            want == null || (name != want && want in names)
        }.toSet()
    }
}
