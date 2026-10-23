package com.drivemate.app.data

import org.json.JSONObject
import org.json.JSONTokener

/** Regional publication index. Coordinates are used locally to choose an immutable bundle. */
internal class PublishedRegionCatalog private constructor(val coverage: String, val regions: List<Region>) {
    data class Bounds(val west: Double, val south: Double, val east: Double, val north: Double) {
        fun contains(lat: Double, lon: Double) = lat in south..north && lon in west..east
        val area: Double get() = (east - west) * (north - south)
    }
    data class Asset(val tag: String, val sha256: String, val bytes: Long)
    data class Region(val id: String, val name: String, val bounds: Bounds, val manifest: Asset,
                      val map: Asset, val mapName: String)

    fun select(lat: Double?, lon: Double?): Region? {
        if (lat == null || lon == null) return regions.firstOrNull { it.id == "stockport" } ?: regions.firstOrNull()
        if (!lat.isFinite() || !lon.isFinite()) return null
        return regions.filter { it.bounds.contains(lat, lon) }.minWithOrNull(compareBy<Region> { it.bounds.area }.thenBy { it.id })
    }

    /**
     * Prefer a verified published region covering the full map viewport.
     * Fallback to the smallest region at the centre if no one region can do so.
     * No missing map region is fabricated or downloaded based on precise GPS.
     */
    fun selectForViewport(lat: Double?, lon: Double?, viewport: Bounds?): Region? {
        val centre = select(lat, lon) ?: return null
        if (lat == null || lon == null) return centre
        val view = viewport ?: return centre
        if (listOf(view.west, view.south, view.east, view.north).any { !it.isFinite() } ||
            view.west >= view.east || view.south >= view.north) return centre
        return regions.asSequence()
            .filter { it.bounds.contains(lat, lon) &&
                it.bounds.west <= view.west && it.bounds.east >= view.east &&
                it.bounds.south <= view.south && it.bounds.north >= view.north }
            .minWithOrNull(compareBy<Region> { it.bounds.area }.thenBy { it.id }) ?: centre
    }

    companion object {
        const val MAX_BYTES = 2 * 1024 * 1024
        private val ID = Regex("[a-z0-9][a-z0-9-]{0,79}")
        private val TAG = Regex("map-data-region-[0-9A-Za-z._-]{1,110}")
        private val SHA = Regex("[0-9a-f]{64}")
        private fun asset(value: JSONObject, max: Long): Asset {
            val tag = value.getString("tag").also { require(TAG.matches(it)) }
            val sha = value.getString("sha256").also { require(SHA.matches(it)) }
            val raw = value.get("bytes")
            require(raw is Int || raw is Long)
            val bytes = (raw as Number).toLong().also { require(it in 1..max) }
            return Asset(tag, sha, bytes)
        }
        fun parse(text: String): PublishedRegionCatalog {
            require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
            val root = JSONTokener(text).nextValue() as? JSONObject ?: error("Invalid region catalog")
            require(root.get("schema") == 1)
            val coverage = root.getString("coverage").also { require(it in setOf("partial", "complete")) }
            val values = root.getJSONObject("regions")
            require(values.length() <= 512)
            val regions = values.keys().asSequence().map { id ->
                require(ID.matches(id))
                val value = values.getJSONObject(id)
                require(value.getString("id") == id)
                val name = value.getString("name").also { require(it.isNotBlank() && it.length <= 120) }
                val bounds = value.getJSONArray("bounds")
                require(bounds.length() == 4)
                val b = Bounds(bounds.getDouble(0), bounds.getDouble(1), bounds.getDouble(2), bounds.getDouble(3))
                require(listOf(b.west, b.south, b.east, b.north).all { it.isFinite() } &&
                    b.west in -180.0..180.0 && b.east in -180.0..180.0 &&
                    b.south in -85.1..85.1 && b.north in -85.1..85.1 && b.west < b.east && b.south < b.north)
                val manifest = asset(value.getJSONObject("manifest"), MAX_BYTES.toLong())
                val mapJson = value.getJSONObject("map")
                require(mapJson.getString("name") == "drivemate-v1.pmtiles" && mapJson.getString("layout") == "drivemate-v1")
                val map = asset(mapJson, 1_950_000_000L)
                require(map.tag.startsWith("map-data-region-$id-") && manifest.tag.startsWith("map-data-region-$id-"))
                Region(id, name, b, manifest, map, "drivemate-v1.pmtiles")
            }.toList().sortedBy { it.id }
            return PublishedRegionCatalog(coverage, regions)
        }
    }
}
