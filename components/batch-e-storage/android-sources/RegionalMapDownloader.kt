package com.drivemate.app.data

import java.io.File
import java.io.IOException

/**
 * Downloads and installs a planned set of regional map packages and their
 * companion data squares. Call on an IO worker only.
 *
 * - Each package downloads to `<name>-<sha256>.part`, so an interrupted
 *   transfer resumes only into bytes from the same immutable generation.
 * - A complete part is installed only through [VerifiedRegionalMapStore],
 *   which checks size, PMTiles header and SHA-256 before an atomic rename.
 *   A part that fails verification is deleted so the retry starts clean
 *   rather than resuming corrupt bytes forever.
 * - Packages already installed and verified are skipped without network use.
 * - Companion squares go through [fetchCompanion] (normally
 *   [MapData.downloadWeekly], which verifies them against the same release
 *   manifest) into the exact cache files the existing consumers read.
 *
 * Nothing here switches the renderer: activation stays with the viewport
 * coverage gate and style composer, so a partially downloaded set can never
 * replace the working whole-UK map.
 */
internal class RegionalMapDownloader(
    private val store: VerifiedRegionalMapStore,
    private val catalog: RegionMapCatalog,
    private val stagingDir: File,
    private val urlFor: (RegionMapCatalog.Pack) -> String,
    private val fetch: (url: String, dest: File, onBytes: (Long) -> Unit) -> Unit = ResumableDownload::fetch,
    private val companionTarget: (String) -> File?,
    private val fetchCompanion: (name: String, dest: File) -> Unit = MapData::downloadWeekly,
    private val attempts: Int = 1,
) {
    init {
        require(attempts == 1) { "Failed downloads require explicit user retry" }
        require(store.matchesCatalog(catalog)) { "Store and download plan belong to different generations" }
    }

    data class Result(
        val installed: List<String>,
        val failed: Map<String, String>,
        val companionsFetched: Int,
        val companionsFailed: List<String>,
    ) {
        val complete: Boolean get() = failed.isEmpty() && companionsFailed.isEmpty()
    }

    fun download(
        plan: RegionDownloadPlanner.Plan,
        onProgress: (doneBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
        checkpoint: () -> Unit = {},
        onStage: (RegionalDownloadStage) -> Unit = {},
    ): Result {
        val byName = catalog.packages.associateBy { it.name }
        val packs = plan.packages.map {
            byName[it] ?: throw IllegalArgumentException("Plan names a package outside this generation")
        }
        if ((!stagingDir.exists() && !stagingDir.mkdirs()) || !stagingDir.isDirectory) {
            throw IOException("Cannot create regional download directory")
        }
        val total = packs.sumOf { it.bytes }
        var finished = 0L
        val installed = ArrayList<String>()
        val failed = LinkedHashMap<String, String>()
        val count = packs.size
        for ((index, pack) in packs.withIndex()) {
            checkpoint()
            fun stage(step: RegionalDownloadStage.Step, resumed: Long = 0) = onStage(
                RegionalDownloadStage(step, index + 1, count, finished, total, resumedFromBytes = resumed),
            )
            if (store.verifiedFileForPackage(pack.name, checkpoint) != null) {
                installed += pack.name
                finished += pack.bytes
                onProgress(finished, total)
                continue
            }
            val part = File(stagingDir, "${store.expectedFileName(pack.name)}.part")
            var problem: String? = null
            for (attempt in 1..attempts) {
                checkpoint()
                problem = try {
                    // The part's name carries this package's fingerprint, so saved
                    // bytes are only ever resumed for the identical package.
                    stage(RegionalDownloadStage.Step.DOWNLOADING, if (part.isFile) part.length() else 0L)
                    fetch(urlFor(pack), part) { have -> onProgress(finished + have, total) }
                    if (part.length() != pack.bytes) throw IOException("Download incomplete")
                    stage(RegionalDownloadStage.Step.VERIFYING)
                    store.installCandidate(pack.name, part, checkpoint) { phase ->
                        if (phase == VerifiedRegionalMapStore.InstallPhase.ACTIVATING) {
                            stage(RegionalDownloadStage.Step.INSTALLING)
                        }
                    }
                    null
                } catch (e: IOException) {
                    // An oversize or checksum-failing part cannot become valid by
                    // appending more bytes: restart it. A short part resumes.
                    if (part.length() >= pack.bytes) part.delete()
                    e.message ?: "Download failed"
                }
                if (problem == null) break
            }
            if (problem == null) {
                part.delete()
                installed += pack.name
            } else {
                failed[pack.name] = problem
            }
            finished += pack.bytes
            onProgress(finished, total)
        }
        var fetched = 0
        val companionFailures = ArrayList<String>()
        if (plan.companions.isNotEmpty()) onStage(RegionalDownloadStage(RegionalDownloadStage.Step.EXTRAS, 0, count, finished, total))
        for (name in plan.companions) {
            checkpoint()
            val dest = companionTarget(name)
            if (dest == null) {
                companionFailures += name
                continue
            }
            val ok = runCatching {
                dest.parentFile?.mkdirs()
                fetchCompanion(name, dest)
            }.isSuccess
            if (ok) fetched++ else companionFailures += name
        }
        removeAbandonedParts(packs)
        return Result(installed, failed, fetched, companionFailures)
    }

    /** Remove parts from older generations; keep this plan's parts for resume. */
    private fun removeAbandonedParts(current: List<RegionMapCatalog.Pack>) {
        val keep = current.map { "${store.expectedFileName(it.name)}.part" }.toSet()
        stagingDir.listFiles()?.forEach { f ->
            if (f.isFile && f.name.endsWith(".part") && f.name !in keep) f.delete()
        }
    }
}
