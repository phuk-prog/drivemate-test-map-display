package com.drivemate.app.data

import android.content.Context
import com.drivemate.app.dmn.Dmn
import com.drivemate.app.dmn.DmnTiles
import com.drivemate.app.nav.LatLon
import java.io.File

/**
 * Android glue for the offline package list, storage breakdown and cleanup
 * (MX08 / WA21 / WA23). Every function hashes files or reads manifests:
 * call on an IO thread only. Nothing here switches the map renderer.
 */
object OfflinePackagesService {
    /** What the offline settings screen shows. Plain strings, ready to display. */
    data class Overview(
        val versionLine: String,
        val installedLines: List<Pair<String, String>>,
        val availableLines: List<String>,
        val availableSummary: String,
        val notes: List<String>,
    )

    data class StorageRow(
        val kind: String,
        val label: String,
        val size: String,
        val removable: Boolean,
        val reason: String?,
        val consequence: String,
    )

    private fun installedGeneration(ctx: Context, scan: RegionalLiveMap.Installed?): OfflineInventory.InstalledGeneration? {
        val installed = scan ?: return null
        val file = File(File(RegionalMaps.root(ctx), installed.generation), RegionalLiveMap.GENERATION_MANIFEST)
        return runCatching {
            val m = RegionalMapManifest.parse(file.readText(Charsets.UTF_8), requirePublished = true)
            // Compatibility: the manifest copy must describe exactly the verified generation.
            require(m.catalog.sourceSha256 == installed.generation)
            OfflineInventory.InstalledGeneration(m, installed.verified, file.lastModified())
        }.getOrNull()
    }

    private fun companionState(ctx: Context, name: String): OfflineInventory.Have {
        val file = RegionalMapManifest.companionTarget(ctx.filesDir, name) ?: return OfflineInventory.Have.MISSING
        if (!file.isFile) return OfflineInventory.Have.MISSING
        return if (MapData.freshness(name, file) == true) OfflineInventory.Have.READY else OfflineInventory.Have.NOT_CHECKED
    }

    private fun latestManifest(ctx: Context): RegionalMapManifest? = runCatching { RegionalMaps.manifest(ctx) }.getOrNull()

    fun overview(ctx: Context): Overview {
        val root = RegionalMaps.root(ctx)
        val installed = installedGeneration(ctx, RegionalLiveMap.scan(root))
        val latest = latestManifest(ctx)
        val latestDir = latest?.let { File(File(root, it.catalog.sourceSha256), "staging") }
        val inventory = OfflineInventory.build(
            latest = latest,
            installed = installed,
            companionState = { companionState(ctx, it) },
            savedBytes = { pack -> latestDir?.let { RegionalResume.savedFor(it, "${pack.name}-${pack.sha256}.pmtiles.part") } ?: 0L },
            areaRoutes = { b -> OfflineInventory.areaRoutes(b, Dmn.available()) { have(DmnTiles.offlineArea(dmnDir(ctx), it)) } },
            uncoveredPoints = RegionalMaps.status.value.uncoveredPoints,
        )
        val date = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.UK)
        val version = buildString {
            if (inventory.installedGeneration == null) append("No area maps installed.")
            else {
                append("Installed area maps: version ${inventory.installedGeneration.take(8)}")
                inventory.installedAtMs?.let { append(", checked and installed ${date.format(java.util.Date(it))}") }
                append(inventory.installedBuiltDate?.let { ", map data from $it." } ?: ". Map build date not published.")
            }
            if (latest != null && latest.catalog.sourceSha256 != inventory.installedGeneration) {
                append(" Newer version available")
                inventory.availableBuiltDate?.let { append(" (map data from $it)") }
                append(".")
            }
        }
        val installedLines = inventory.installed.map { OfflineInventory.areaLine(it) to OfflineInventory.partsLine(it.parts) }
        val availableLines = inventory.available.map { OfflineInventory.areaLine(it) }
        val summary = if (inventory.available.isEmpty()) "" else
            "${inventory.available.size} more areas available · ${OfflineStorage.mb(inventory.availableBytes)} in total"
        val generations = DataGenerations.note(DataGenerations.dmnMatchesMap(OwnMap.activeSnapshotTag(), DmnTiles.localSource(dmnDir(ctx))))
        val areas = DataGenerations.areaNote(DataGenerations.areaMapsMatchMap(MapData.mapArchiveSha256(), installed?.manifest?.catalog?.sourceSha256))
        return Overview(version, installedLines, availableLines, summary, inventory.notes + listOfNotNull(generations, areas, OwnMap.detailGenerationNote()))
    }

    /** IO thread: offline coverage of a planned route, for "before you go" disclosure. */
    fun routeCoverage(ctx: Context, points: List<LatLon>): String? = runCatching {
        val installed = installedGeneration(ctx, RegionalLiveMap.scan(RegionalMaps.root(ctx)))
        val cached = File(RegionalMaps.root(ctx), RegionalMaps.MANIFEST).takeIf { it.isFile }
            ?.let { runCatching { RegionalMapManifest.parse(it.readText(Charsets.UTF_8)) }.getOrNull() }
        OfflineInventory.routeCoverage(cached, installed, points,
            wholeUkMap = MapPack.installed() != null,
            // DMN plans routes; the Valhalla road pack only matches the car to the road (5.2).
            dmnRoutes = OfflineInventory.journeyRoutes(points, Dmn.available()) { have(DmnTiles.offline(dmnDir(ctx), it)) },
        )?.let(OfflineInventory::routeCoverageText)
    }.getOrNull()

    /** Where DMN keeps its downloaded road pieces (as Dmn.routerFor). */
    private fun dmnDir(ctx: Context) = File(ctx.noBackupFilesDir, "dmn")

    private fun have(o: DmnTiles.Offline) = when (o) {
        DmnTiles.Offline.READY -> OfflineInventory.Have.READY
        DmnTiles.Offline.PARTIAL -> OfflineInventory.Have.PARTIAL
        DmnTiles.Offline.MISSING -> OfflineInventory.Have.MISSING
    }

    data class AreaStorageRow(val id: String, val label: String, val bytes: Long, val affectsRoute: Boolean, val removable: Boolean)
    private fun cleanupAreas(ctx: Context, points: List<LatLon>): Pair<File, List<OfflineAreaCleanup.Area>>? {
        val installed = installedGeneration(ctx, RegionalLiveMap.scan(RegionalMaps.root(ctx))) ?: return null
        val dir = File(File(RegionalMaps.root(ctx), installed.generation), "installed")
        val areas = installed.manifest.catalog.packages.filter { it.name != "base" && it.name in installed.verified }.map { p ->
            // No synthetic last-use timestamp: tracking for legacy packages is unknown.
            OfflineAreaCleanup.Area(p.name, OfflineInventory.label(installed.manifest.bounds[p.name]),
                File(dir, "${p.name}-${p.sha256}.pmtiles"), null,
                OfflineAreaCleanup.affectsRoute(installed.manifest.bounds[p.name], points.map { it.lat to it.lon }))
        }
        return dir to OfflineAreaCleanup.suggested(areas)
    }
    fun areaStorage(ctx: Context, navigating: Boolean, points: List<LatLon>): List<AreaStorageRow> {
        val protection = protection(ctx, navigating)
        val removable = !navigating && !protection.downloadRunning && protection.inUseGeneration != protection.currentGeneration
        return cleanupAreas(ctx, points)?.second.orEmpty().map { AreaStorageRow(it.id,it.label,it.bytes,it.affectsRoute,removable) }
    }
    fun cleanAreas(ctx: Context, selected: Set<String>, navigating: Boolean, points: List<LatLon>, confirmed: Boolean): String {
        val result = RegionalMaps.whenIdle {
            runCatching {
                val (dir, areas) = cleanupAreas(ctx, points) ?: return@runCatching "Nothing removed."
                val p = protection(ctx, navigating)
                val freed = OfflineAreaCleanup.remove(dir, areas, selected,
                    navigating || p.downloadRunning || p.inUseGeneration == p.currentGeneration, confirmed)
                "${OfflineStorage.mb(freed)} freed."
            }.getOrElse { "Not removed: ${it.message}" }
        } ?: return "Not removed: a download is running."
        RegionalMaps.refresh(ctx)
        return result
    }

    private fun layout(ctx: Context) = OfflineStorage.Layout(ctx.filesDir, ctx.noBackupFilesDir, ctx.dataDir)

    private fun protection(ctx: Context, navigating: Boolean): OfflineStorage.Protection {
        val root = RegionalMaps.root(ctx)
        val current = installedGeneration(ctx, RegionalLiveMap.scan(root))
        val recovery = current?.let { installedGeneration(ctx, RegionalLiveMap.scan(root, excludedGeneration = it.generation)) }
        // Data squares that installed area packages rely on stay, so no downloaded area becomes incomplete.
        val keep = listOfNotNull(current, recovery).flatMap { gen ->
            gen.verified.flatMap { gen.manifest.companions[it].orEmpty() }
        }.mapNotNull { RegionalMapManifest.companionTarget(ctx.filesDir, it) }.toSet()
        return OfflineStorage.Protection(
            currentGeneration = current?.generation,
            recoveryGeneration = recovery?.generation,
            inUseGeneration = RegionalLiveMap.inUseGeneration,
            downloadRunning = RegionalMaps.downloadRunning,
            navigating = navigating,
            keepSquares = keep,
        )
    }

    fun storage(ctx: Context, navigating: Boolean): Pair<List<StorageRow>, String> {
        val rows = OfflineStorage.breakdown(layout(ctx), protection(ctx, navigating)).map {
            StorageRow(it.kind.name, it.kind.label, OfflineStorage.mb(it.bytes), it.removable && it.kind != OfflineStorage.Kind.AREA_MAPS, it.reason, it.consequence)
        }
        return rows to "${OfflineStorage.mb(ctx.filesDir.usableSpace)} free on this phone"
    }

    /** Removes one user-chosen item; returns a plain-English result line. */
    fun clean(ctx: Context, kind: String, navigating: Boolean): String {
        val k = runCatching { OfflineStorage.Kind.valueOf(kind) }.getOrNull() ?: return "Nothing removed."
        val result = RegionalMaps.whenIdle {
            try {
                val freed = OfflineStorage.clean(layout(ctx), k, protection(ctx, navigating))
                "Removed ${k.label.lowercase()}: ${OfflineStorage.mb(freed)} freed."
            } catch (e: IllegalStateException) {
                "Not removed: ${e.message}"
            }
        } ?: return "Not removed: a download is running."
        RegionalMaps.refresh(ctx)
        return result
    }
}
