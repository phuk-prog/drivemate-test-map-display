package com.drivemate.app.data

import java.io.File

/** Per-area user-selected cleanup; never removes base, settings, companions or route data. */
internal object OfflineAreaCleanup {
    data class Area(val id: String, val label: String, val file: File, val lastUsedMs: Long?, val affectsRoute: Boolean) {
        val bytes: Long get() = if (file.isFile) file.length() else 0
    }
    /** Unknown use dates are surfaced as unknown rather than invented from installation time. */
    fun suggested(areas: List<Area>): List<Area> =
        areas.sortedWith(compareBy<Area> { it.lastUsedMs ?: Long.MIN_VALUE }.thenBy { it.label }.thenBy { it.id })
    fun selectedBytes(areas: List<Area>, selected: Set<String>): Long =
        areas.distinctBy { it.id }.filter { it.id in selected }.sumOf { it.bytes }
    fun missingBytes(required: Long, free: Long, selectedBytes: Long): Long =
        (required - free - selectedBytes).coerceAtLeast(0)
    /** Conservative segment-box intersection: a route crossing an area is warned even without a sample inside. */
    fun affectsRoute(bounds: DoubleArray?, route: List<Pair<Double, Double>>): Boolean {
        if (bounds == null || bounds.size != 4 || bounds.any { !it.isFinite() }) return route.isNotEmpty()
        fun inside(p: Pair<Double, Double>) = p.first in bounds[1]..bounds[3] && p.second in bounds[0]..bounds[2]
        if (route.any(::inside)) return true
        return route.zipWithNext().any { (a,b) ->
            minOf(a.second,b.second) <= bounds[2] && maxOf(a.second,b.second) >= bounds[0] &&
                minOf(a.first,b.first) <= bounds[3] && maxOf(a.first,b.first) >= bounds[1]
        }
    }
    /** All paths/selection/protection are checked before the first deletion. Call under the download lock. */
    fun remove(installedDir: File, areas: List<Area>, selected: Set<String>, protected: Boolean,
               routeConfirmed: Boolean): Long {
        check(!protected) { "Area maps are in use or a download is running." }
        require(selected.isNotEmpty()) { "No areas selected." }
        val chosen = areas.distinctBy { it.id }.filter { it.id in selected }
        require(chosen.size == selected.size) { "The selected area list changed; review it again." }
        check(routeConfirmed || chosen.none { it.affectsRoute }) { "Removing this area affects your planned route." }
        val parent = installedDir.canonicalFile
        chosen.forEach { a ->
            check(a.id != "base" && Regex("region-z(8|9|10)-x[0-9]{1,4}-y[0-9]{1,4}").matches(a.id))
            check(a.file.canonicalFile.parentFile == parent && a.file.isFile) { "Area file changed; review it again." }
        }
        var freed=0L
        for(a in chosen) { val bytes=a.bytes; if(a.file.delete()) freed+=bytes }
        return freed
    }
}
