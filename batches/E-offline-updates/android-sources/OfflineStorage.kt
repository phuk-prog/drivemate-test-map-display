package com.drivemate.app.data

import java.io.File

/**
 * Storage used by offline maps, free-space checks and user-chosen cleanup
 * (backlog WA23). File-system only, no Android, so it can be unit tested.
 *
 * Safety rules:
 * - Cleanup works on an allow-list of map folders only. Saved places,
 *   favourites, Home/Work, settings and trip history ([Kind.PRIVATE]) are
 *   measured but can never be removed here.
 * - The area maps in use on the map, during a journey or by a running
 *   download are protected and refused, not silently skipped.
 * - Data squares that belong to installed area packages are kept, so a
 *   cleanup never makes a downloaded area incomplete.
 * - Each removable item says what stops working offline before it is removed.
 */
internal object OfflineStorage {
    private val SHA = Regex("[0-9a-f]{64}")
    private const val RESERVE_BYTES = 300L * 1024 * 1024
    private val SQUARE_DIRS = listOf("lanes", "limits", "roadinfo", "places")

    enum class Kind(val label: String) {
        WHOLE_UK_MAP("Whole UK map"),
        AREA_MAPS("Downloaded area maps"),
        RECOVERY_MAPS("Previous area map version (kept for recovery)"),
        OLD_MAPS("Older area map versions not in use"),
        TEMPORARY("Unfinished downloads"),
        SQUARES("Lane, speed-limit and place data"),
        OFFLINE_ROUTES("Road matching for no signal"),
        SEARCH("Offline search index"),
        PRIVATE("Your saved places, favourites, settings and trips"),
    }

    /** The app's storage folders (Context.filesDir, noBackupFilesDir and dataDir). */
    class Layout(val filesDir: File, val noBackupDir: File, val dataDir: File) {
        val regions: File get() = File(filesDir, "regions")
    }

    data class Protection(
        /** Newest complete verified area-map generation (folder name). */
        val currentGeneration: String? = null,
        val recoveryGeneration: String? = null,
        /** Generation the live map is reading now, if any. */
        val inUseGeneration: String? = null,
        val downloadRunning: Boolean = false,
        val navigating: Boolean = false,
        /** Data-square files that installed area packages rely on. */
        val keepSquares: Set<File> = emptySet(),
    )

    data class Item(
        val kind: Kind,
        val bytes: Long,
        /** True when the user may remove it from this list now. */
        val removable: Boolean,
        /** Why it is protected or managed elsewhere; null when removable. */
        val reason: String?,
        /** What stops working offline if it is removed. */
        val consequence: String,
    )

    fun breakdown(layout: Layout, protection: Protection): List<Item> {
        val out = ArrayList<Item>()
        fun add(kind: Kind, bytes: Long) {
            if (bytes <= 0L && kind != Kind.PRIVATE) return
            val reason = protectedReason(kind, protection)
            out += Item(kind, bytes, reason == null, reason, consequence(kind))
        }
        add(Kind.WHOLE_UK_MAP, sizeOf(File(layout.filesDir, "map")))
        add(Kind.AREA_MAPS, protection.currentGeneration?.let { installedBytes(layout, it) } ?: 0L)
        add(Kind.RECOVERY_MAPS, protection.recoveryGeneration
            ?.takeIf { it != protection.currentGeneration }?.let { installedBytes(layout, it) } ?: 0L)
        add(Kind.OLD_MAPS, oldGenerations(layout, protection).sumOf { installedBytes(layout, it.name) })
        add(Kind.TEMPORARY, temporaryFiles(layout, protection).sumOf { sizeOf(it) })
        add(Kind.SQUARES, removableSquares(layout, protection).sumOf { it.length() })
        add(Kind.OFFLINE_ROUTES, sizeOf(File(layout.noBackupDir, "offline-roads")))
        add(Kind.SEARCH, sizeOf(File(layout.filesDir, "search")))
        add(Kind.PRIVATE, privateFiles(layout).sumOf { sizeOf(it) })
        return out
    }

    private fun protectedReason(kind: Kind, p: Protection): String? = when (kind) {
        Kind.PRIVATE -> "Never removed by map cleanup."
        Kind.WHOLE_UK_MAP -> "Turn off \"Whole UK map\" above to remove it."
        Kind.OFFLINE_ROUTES -> "Choose \"Greater Manchester\" under \"Road matching with no signal\" to remove it."
        Kind.SEARCH -> "Part of offline search; kept with the map data."
        Kind.AREA_MAPS -> when {
            p.inUseGeneration != null && p.inUseGeneration == p.currentGeneration -> "In use on the map now."
            p.navigating -> "Can't be removed during a journey."
            p.downloadRunning -> "A download is running."
            else -> null
        }
        Kind.RECOVERY_MAPS -> when {
            p.inUseGeneration != null && p.inUseGeneration == p.recoveryGeneration -> "In use on the map now."
            p.navigating -> "Can't be removed during a journey."
            p.downloadRunning -> "A download is running."
            else -> null
        }
        Kind.OLD_MAPS, Kind.TEMPORARY, Kind.SQUARES ->
            if (p.downloadRunning) "A download is running." else null
    }

    fun consequence(kind: Kind): String = when (kind) {
        Kind.WHOLE_UK_MAP -> "Without signal, the map would only show what your area maps cover."
        Kind.AREA_MAPS -> "Detailed maps of your downloaded area are removed. Without signal there you'd need " +
            "the whole UK map; you can download the area again on Wi-Fi."
        Kind.RECOVERY_MAPS -> "If a future map update turns out to be damaged, there is no earlier checked " +
            "version to fall back to until the next good download."
        Kind.OLD_MAPS -> "Nothing you use now. These versions have been replaced."
        Kind.TEMPORARY -> "Any paused area download starts again from the beginning."
        Kind.SQUARES -> "Lane arrows, speed limits and offline place search for places outside your " +
            "downloaded areas need signal again until they re-download. Data for your downloaded areas is kept."
        Kind.OFFLINE_ROUTES -> "Without signal, the car is matched to the road only around Greater Manchester. New routes are not affected."
        Kind.SEARCH -> "Without signal, searching for places would find fewer results."
        Kind.PRIVATE -> "Not affected by map cleanup."
    }

    /**
     * Removes one user-chosen [kind] and returns bytes freed. Throws
     * [IllegalStateException] for protected or non-removable kinds rather
     * than deleting anything.
     */
    fun clean(layout: Layout, kind: Kind, protection: Protection): Long {
        protectedReason(kind, protection)?.let { throw IllegalStateException(it) }
        val targets: List<File> = when (kind) {
            Kind.AREA_MAPS -> listOfNotNull(protection.currentGeneration?.let { generationDir(layout, it) })
            Kind.RECOVERY_MAPS -> listOfNotNull(protection.recoveryGeneration
                ?.takeIf { it != protection.currentGeneration }?.let { generationDir(layout, it) })
            Kind.OLD_MAPS -> oldGenerations(layout, protection)
            Kind.TEMPORARY -> temporaryFiles(layout, protection)
            Kind.SQUARES -> removableSquares(layout, protection)
            else -> throw IllegalStateException("Not removable here")
        }
        var freed = 0L
        for (f in targets) {
            check(isInsideMapData(layout, f)) { "Refusing to remove outside map data" }
            val size = sizeOf(f)
            if (f.deleteRecursively()) freed += size
        }
        return freed
    }

    private fun isInsideMapData(layout: Layout, f: File): Boolean {
        val path = f.canonicalPath
        val allowed = listOf(layout.regions) + SQUARE_DIRS.map { File(layout.filesDir, it) }
        return allowed.any { path.startsWith(it.canonicalPath + File.separator) }
    }

    private fun generationDir(layout: Layout, name: String): File? =
        name.takeIf { SHA.matches(it) }?.let { File(layout.regions, it) }?.takeIf { it.isDirectory }

    /** A generation folder's size, less its temporary files (counted under [Kind.TEMPORARY]). */
    private fun installedBytes(layout: Layout, generation: String): Long =
        generationDir(layout, generation)?.let { dir ->
            val temporary = sizeOf(File(dir, "staging")) + (File(dir, "installed").listFiles()
                ?.filter { it.isFile && it.name.startsWith("map-region-") && it.name.endsWith(".part") }
                ?.sumOf { it.length() } ?: 0L)
            sizeOf(dir) - temporary
        } ?: 0L

    private fun generations(layout: Layout): List<File> =
        layout.regions.listFiles()?.filter { it.isDirectory && SHA.matches(it.name) }.orEmpty()

    private fun complete(dir: File) = File(dir, RegionalLiveMap.GENERATION_MANIFEST).isFile

    /** Complete generations that are neither current, recovery nor in use. */
    private fun oldGenerations(layout: Layout, p: Protection): List<File> {
        val keep = setOfNotNull(p.currentGeneration, p.recoveryGeneration, p.inUseGeneration)
        return generations(layout).filter { complete(it) && it.name !in keep }
    }

    /**
     * Partial downloads: every staging folder, interrupted install copies, and
     * whole generations that never finished (none of their files are in use).
     */
    private fun temporaryFiles(layout: Layout, p: Protection): List<File> {
        val keep = setOfNotNull(p.currentGeneration, p.recoveryGeneration, p.inUseGeneration)
        val out = ArrayList<File>()
        for (dir in generations(layout)) {
            if (!complete(dir) && dir.name !in keep) {
                out += dir
                continue
            }
            File(dir, "staging").takeIf { it.isDirectory }?.let { out += it }
            File(dir, "installed").listFiles()
                ?.filter { it.isFile && it.name.startsWith("map-region-") && it.name.endsWith(".part") }
                ?.let { out += it }
        }
        return out
    }

    private fun removableSquares(layout: Layout, p: Protection): List<File> {
        val keep = p.keepSquares.map { it.absoluteFile }.toSet()
        return SQUARE_DIRS.flatMap { name ->
            File(layout.filesDir, name).listFiles()?.filter { it.isFile && it.absoluteFile !in keep }.orEmpty()
        }
    }

    /** Where favourites, saved places, settings and trips live; measured only. */
    internal fun privateFiles(layout: Layout): List<File> = listOf(
        File(layout.dataDir, "shared_prefs"),
        File(layout.dataDir, "databases"),
        File(layout.filesDir, "datastore"),
        File(layout.noBackupDir, "journey-evidence"),
        File(layout.noBackupDir, "current-trip.json"),
    )

    fun sizeOf(f: File): Long = when {
        f.isFile -> f.length()
        f.isDirectory -> f.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        else -> 0L
    }

    /**
     * Free space an area download needs (WA23): the bytes still to fetch, plus
     * room for the checked copy of the largest piece (each piece is copied and
     * checked before it replaces anything), plus a safety reserve. The working
     * version stays on the phone until its replacement is checked, so it is
     * not counted as reusable space.
     */
    data class SpaceNeed(val downloadBytes: Long, val workingBytes: Long, val reserveBytes: Long) {
        val totalBytes: Long get() = downloadBytes + workingBytes + reserveBytes
    }

    /** @param pieces for each piece not yet installed: its size and bytes already saved. */
    fun spaceNeeded(pieces: List<Pair<Long, Long>>, reserveBytes: Long = RESERVE_BYTES): SpaceNeed {
        val download = pieces.sumOf { (bytes, saved) -> (bytes - saved.coerceIn(0, bytes)) }
        val working = pieces.maxOfOrNull { it.first } ?: 0L
        return SpaceNeed(download, if (pieces.isEmpty()) 0L else working, if (pieces.isEmpty()) 0L else reserveBytes)
    }

    fun enoughSpace(need: SpaceNeed, freeBytes: Long): Boolean = freeBytes >= need.totalBytes

    fun spaceText(need: SpaceNeed, freeBytes: Long): String {
        if (need.totalBytes == 0L) return "Already downloaded; no extra space needed."
        val base = "Needs ${mb(need.totalBytes)} free: ${mb(need.downloadBytes)} to download plus " +
            "${mb(need.workingBytes + need.reserveBytes)} working room to check it safely. You have ${mb(freeBytes)} free."
        return if (enoughSpace(need, freeBytes)) base
        else "$base Free up ${mb(need.totalBytes - freeBytes)} first (see Storage below)."
    }

    fun mb(bytes: Long): String = when {
        bytes < 1_000_000_000L -> "${maxOf(0L, (bytes + 500_000) / 1_000_000)} MB"
        else -> "%.1f GB".format(java.util.Locale.UK, bytes / 1e9)
    }
}
