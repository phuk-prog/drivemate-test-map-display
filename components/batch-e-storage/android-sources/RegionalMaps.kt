package com.drivemate.app.data

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Downloads regional map detail plus its lane / speed-limit / places squares
 * for a chosen area, using only a *published* regional manifest from the
 * current authenticated release.
 *
 * Trust chain: latest.json pins the release manifest SHA-256 → that manifest
 * pins `regional-manifest.json` → which pins every package. Each package's
 * release-manifest entry must also agree with the regional manifest, so
 * neither document alone can redirect a download.
 *
 * Unavailable (returns null / does nothing) until the data repository
 * publishes regional maps. Never switches the renderer; activation is gated
 * separately behind the viewport coverage check.
 */
object RegionalMaps {
    private const val WORK = "regional-map-area"
    private const val CHANNEL = "downloads"
    internal const val MANIFEST = "regional-manifest.json"

    data class Status(
        val available: Boolean = false,
        val progress: Float? = null,
        val installedPackages: Int = 0,
        val installedGeneration: String? = null,
        val installedBytes: Long = 0,
        val installedRevision: String? = null,
        val recoveryGeneration: String? = null,
        val uncoveredPoints: Int = 0,
        val problem: String? = null,
        /** Download / check / install stage (WA22); null when idle with nothing to report. */
        val stage: RegionalDownloadStage? = null,
        val failedSinceMs: Long? = null,
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status
    private val lock = Mutex()
    private const val FAILED_RETENTION_MS = 2L * 24 * 60 * 60 * 1000
    @Volatile private var networkRegistered = false
    fun mobileDownloadsAllowed(ctx: Context) = prefs(ctx).getBoolean("mobile_allowed", false)
    fun setMobileDownloadsAllowed(ctx: Context, allowed: Boolean) {
        prefs(ctx).edit().putBoolean("mobile_allowed", allowed).apply()
        if (prefs(ctx).getBoolean("paused", false) && allowed) resume(ctx)
    }
    /** Network recovery resumes paused work, never failed work. No daily data cap. */
    private fun observeNetwork(ctx: Context) {
        if (networkRegistered) return
        synchronized(this) {
            if (networkRegistered) return
            val app = ctx.applicationContext
            val cm = app.getSystemService(android.net.ConnectivityManager::class.java)
            cm.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: android.net.Network, caps: android.net.NetworkCapabilities) {
                    val permitted = caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
                        (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) || mobileDownloadsAllowed(app))
                    if (permitted && prefs(app).getBoolean("paused", false) &&
                        !prefs(app).contains("failed_since")) resume(app)
                }
            })
            networkRegistered = true
        }
    }
    private fun recordFailure(ctx: Context, message: String) {
        val p = prefs(ctx)
        val since = if (p.contains("failed_since")) p.getLong("failed_since", 0) else System.currentTimeMillis()
        p.edit().putLong("failed_since", since).apply()
        _status.value = _status.value.copy(progress = null, problem = message, failedSinceMs = since,
            stage = RegionalDownloadStage(RegionalDownloadStage.Step.FAILED))
    }
    /** True while the worker holds [lock] and may be writing map files. */
    @Volatile internal var downloadRunning = false
        private set

    /** Runs [block] only when no download holds the lock (cleanup must never race one); else null. */
    internal fun <T> whenIdle(block: () -> T): T? {
        if (!lock.tryLock()) return null
        return try { block() } finally { lock.unlock() }
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("regional_maps", Context.MODE_PRIVATE)
    internal fun root(ctx: Context) = File(ctx.filesDir, "regions")

    /**
     * URL for one package, refusing it unless the release manifest's own entry
     * for that asset has the identical SHA-256 and size.
     */
    internal fun verifiedUrl(
        pack: RegionMapCatalog.Pack,
        lookup: (String) -> MapData.Download?,
    ): String {
        val name = RegionalMapManifest.assetName(pack.name)
        val download = lookup(name) ?: throw IOException("Regional package not in this release")
        if (download.asset.sha256 != pack.sha256 || download.asset.bytes != pack.bytes) {
            throw IOException("Release and regional manifests disagree")
        }
        return download.url
    }

    /** IO thread. Null when the current release has no published regional maps. */
    internal fun manifest(ctx: Context): RegionalMapManifest? {
        MapData.init(ctx)
        if (MapData.assetDownload(MANIFEST) == null) return null
        val file = File(root(ctx).apply { mkdirs() }, MANIFEST)
        MapData.downloadWeekly(MANIFEST, file)
        return RegionalMapManifest.parse(file.readText(Charsets.UTF_8), requirePublished = true)
    }

    /** Remember the area and start a Wi-Fi download of it. */
    fun requestArea(ctx: Context, lat: Double, lon: Double, radiusKm: Double) {
        require(lat.isFinite() && lon.isFinite() && radiusKm.isFinite() && radiusKm in 1.0..300.0)
        prefs(ctx).edit().putFloat("lat", lat.toFloat()).putFloat("lon", lon.toFloat())
            .putFloat("radius", radiusKm.toFloat()).apply()
        enqueue(ctx)
    }

    /** True when an area was chosen before, so a paused download can resume. */
    fun canResume(ctx: Context): Boolean = prefs(ctx).contains("lat")

    /**
     * Pause (WA22): stops the transfer. Saved bytes stay in this generation's
     * staging folder under the package's fingerprinted name, so resuming
     * carries on only for the identical package.
     */
    fun pause(ctx: Context) {
        prefs(ctx).edit().putBoolean("paused", true).apply()
        WorkManager.getInstance(ctx).cancelUniqueWork(WORK)
        val st = _status.value
        _status.value = st.copy(progress = null, stage = RegionalDownloadStage(
            RegionalDownloadStage.Step.PAUSED, resumedFromBytes = st.stage?.let { maxOf(it.doneBytes, it.resumedFromBytes) } ?: 0L))
    }

    /** Resume the remembered area; unchanged pieces carry on from their saved bytes. */
    fun resume(ctx: Context) {
        if (!canResume(ctx)) return
        enqueue(ctx)
    }

    private fun enqueue(ctx: Context) {
        observeNetwork(ctx)
        prefs(ctx).edit().putBoolean("paused", false).remove("failed_since").apply()
        _status.value = _status.value.copy(problem = null, failedSinceMs = null,
            stage = RegionalDownloadStage(RegionalDownloadStage.Step.WAITING))
        val c = Constraints.Builder().setRequiredNetworkType(if (mobileDownloadsAllowed(ctx)) NetworkType.CONNECTED else NetworkType.UNMETERED)
            .setRequiresStorageNotLow(true).build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<Worker>().setConstraints(c).build())
    }

    /** IO thread: refresh [status] without network transfers of map data. */
    fun refresh(ctx: Context) {
        observeNetwork(ctx)
        val failed = prefs(ctx).getLong("failed_since", 0)
        if (failed > 0) {
            if (System.currentTimeMillis() - failed >= FAILED_RETENTION_MS) {
                // Only a continuously failed item expires; installed map bytes remain.
                prefs(ctx).edit().remove("failed_since").remove("lat").remove("lon").remove("radius").apply()
                _status.value = _status.value.copy(stage = null, progress = null, problem = null, failedSinceMs = null)
            } else {
                _status.value = _status.value.copy(stage = RegionalDownloadStage(RegionalDownloadStage.Step.FAILED),
                    failedSinceMs = failed)
            }
        }
        val available = runCatching { MapData.assetDownload(MANIFEST) != null }.getOrDefault(false)
        val installed = RegionalLiveMap.scan(root(ctx))
        val previous = installed?.let { RegionalLiveMap.scan(root(ctx), excludedGeneration = it.generation) }
        _status.value = _status.value.copy(
            available = available,
            installedPackages = installed?.verified?.size ?: 0,
            installedGeneration = installed?.generation,
            installedBytes = installed?.catalog?.packages?.filter { it.name in installed.verified }?.sumOf { it.bytes } ?: 0,
            installedRevision = installed?.let { it.generation + ":" + it.verified.sorted().joinToString(",") },
            recoveryGeneration = previous?.generation,
        )
        if (!downloadRunning && canResume(ctx) && _status.value.stage?.active != true && !prefs(ctx).contains("failed_since")) {
            // After an app restart: show saved progress of an interrupted download honestly.
            val saved = savedPartBytes(ctx)
            val paused = prefs(ctx).getBoolean("paused", false)
            if (saved > 0 || paused) {
                _status.value = _status.value.copy(stage = RegionalDownloadStage(
                    RegionalDownloadStage.Step.PAUSED, resumedFromBytes = saved))
            }
        }
    }

    /** Bytes saved in the staging folder of the most recently fetched manifest's generation. */
    private fun savedPartBytes(ctx: Context): Long = runCatching {
        val cached = File(root(ctx), MANIFEST)
        if (!cached.isFile) return 0L
        val m = RegionalMapManifest.parse(cached.readText(Charsets.UTF_8), requirePublished = true)
        val expected = m.catalog.packages.map { "${it.name}-${it.sha256}.pmtiles.part" }.toSet()
        RegionalResume.inspect(root(ctx), m.catalog.sourceSha256, expected).savedBytes
    }.getOrDefault(0L)

    /** Space an area needs, counting room to check each piece before it replaces anything. */
    internal fun spaceNeed(ctx: Context, manifest: RegionalMapManifest, plan: RegionDownloadPlanner.Plan): OfflineStorage.SpaceNeed {
        val generation = File(root(ctx), manifest.catalog.sourceSha256)
        val byName = manifest.catalog.packages.associateBy { it.name }
        val pieces = plan.packages.mapNotNull { byName[it] }.filter { pack ->
            File(File(generation, "installed"), "${pack.name}-${pack.sha256}.pmtiles").length() != pack.bytes
        }.map { pack ->
            pack.bytes to RegionalResume.savedFor(File(generation, "staging"), "${pack.name}-${pack.sha256}.pmtiles.part")
        }
        return OfflineStorage.spaceNeeded(pieces)
    }

    /** IO thread: download size and plain free-space check for an area, or null when unknown. */
    fun spaceCheck(ctx: Context, lat: Double, lon: Double, radiusKm: Double): Pair<Long, String>? = runCatching {
        val m = manifest(ctx) ?: return null
        val plan = RegionDownloadPlanner.around(m, lat, lon, radiusKm)
        val need = spaceNeed(ctx, m, plan)
        plan.downloadBytes to OfflineStorage.spaceText(need, ctx.filesDir.usableSpace)
    }.getOrNull()

    private suspend fun download(ctx: Context, approved: () -> Boolean = { true }, onProgress: (Float) -> Unit): RegionalMapDownloader.Result {
        val downloadContext = currentCoroutineContext()
        fun checkpoint() { downloadContext.ensureActive(); if (!approved()) throw CancellationException("Approval revoked") }
        checkpoint()
        val p = prefs(ctx)
        if (!p.contains("lat")) throw IOException("No download area chosen")
        val manifest = manifest(ctx) ?: throw IOException("Regional maps are not published yet")
        val plan = RegionDownloadPlanner.around(manifest, p.getFloat("lat", 0f).toDouble(),
            p.getFloat("lon", 0f).toDouble(), p.getFloat("radius", 25f).toDouble())
        val generation = File(root(ctx), manifest.catalog.sourceSha256)
        val free = ctx.filesDir.usableSpace
        val need = spaceNeed(ctx, manifest, plan)
        if (!OfflineStorage.enoughSpace(need, free)) {
            throw IOException("not enough free space. " + OfflineStorage.spaceText(need, free))
        }
        val expected = plan.packages.mapNotNull { n -> manifest.catalog.packages.firstOrNull { it.name == n } }
            .map { "${it.name}-${it.sha256}.pmtiles.part" }.toSet()
        val resume = RegionalResume.inspect(root(ctx), manifest.catalog.sourceSha256, expected)
        val restartNote = if (resume.restartExplained) RegionalDownloadText.RESTART_NOTE else null
        val store = VerifiedRegionalMapStore(File(generation, "installed"), manifest.catalog)
        val downloader = RegionalMapDownloader(
            store, manifest.catalog, File(generation, "staging"),
            urlFor = { verifiedUrl(it, MapData::assetDownload) },
            companionTarget = { RegionalMapManifest.companionTarget(ctx.filesDir, it) },
        )
        // An area map you picked: its area files come with it (1.14: never fetched as you drive).
        val result = WholeUk.userDownload { downloader.download(plan, { done, total ->
            _status.value.stage?.takeIf { it.step == RegionalDownloadStage.Step.DOWNLOADING }?.let {
                _status.value = _status.value.copy(stage = it.copy(doneBytes = done, totalBytes = total))
            }
            if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
        }, checkpoint = ::checkpoint, onStage = { stage ->
            _status.value = _status.value.copy(stage = stage.copy(note = restartNote))
        }) }
        _status.value = _status.value.copy(uncoveredPoints = plan.uncoveredPoints)
        // An incomplete new generation never costs the driver the previous one.
        if (result.complete) {
            checkpoint()
            markComplete(ctx, generation, manifest.catalog.sourceSha256)
            removeOtherGenerations(ctx, manifest.catalog.sourceSha256)
            refresh(ctx)
        }
        return result
    }

    /** Automatic workers share the download lock; a revoked approval cancels at every checkpoint. */
    internal suspend fun updateApproved(ctx: Context, approved: () -> Boolean) {
        if (!approved() || !canResume(ctx) || prefs(ctx).contains("failed_since")) return
        lock.withLock {
            if (!approved()) return@withLock
            downloadRunning = true
            try {
                val context = currentCoroutineContext()
                val result = download(ctx, approved) { f ->
                    context.ensureActive()
                    if (!approved()) throw CancellationException("Approval revoked")
                    _status.value = _status.value.copy(progress = f, problem = null)
                }
                if (!result.complete) recordFailure(ctx, "Download failed")
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { recordFailure(ctx, "Download failed"); throw e
            } finally { downloadRunning = false }
        }
    }

    /**
     * Saves this generation's own manifest copy. Its presence marks the generation as
     * completely installed, and lets the live map ([RegionalLiveMap.scan]) re-verify the
     * packages later even after a newer manifest has replaced the shared one.
     */
    private fun markComplete(ctx: Context, generation: File, sourceSha256: String) {
        val text = File(root(ctx), MANIFEST).readText(Charsets.UTF_8)
        // Never label a generation with a manifest of another generation.
        if (RegionalMapManifest.parse(text, requirePublished = true).catalog.sourceSha256 != sourceSha256) return
        val target = File(generation, RegionalLiveMap.GENERATION_MANIFEST)
        val temp = File(generation, "${target.name}.part")
        java.io.FileOutputStream(temp).use { it.write(text.toByteArray(Charsets.UTF_8)); it.fd.sync() }
        if (!temp.renameTo(target)) { temp.delete(); throw IOException("Could not record installed regional maps") }
    }

    /** Keep current, previous verified recovery and any active renderer generation. */
    private fun removeOtherGenerations(ctx: Context, keep: String) {
        // The live map may still be reading an older generation; keep that one until next time.
        val previous = RegionalLiveMap.scan(root(ctx), excludedGeneration = keep)?.generation
        val retained = RegionalLiveMap.retainedGenerations(keep, previous, RegionalLiveMap.inUseGeneration)
        root(ctx).listFiles()?.forEach { f ->
            if (f.isDirectory && f.name !in retained && Regex("[0-9a-f]{64}").matches(f.name)) f.deleteRecursively()
        }
    }

    class Worker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
        override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
            val ctx = applicationContext
            runCatching { setForeground(info(ctx, 0f)) }
            try {
                val result = lock.withLock {
                    downloadRunning = true
                    try {
                        download(ctx) { f ->
                            coroutineContext.ensureActive()
                            _status.value = _status.value.copy(progress = f, problem = null)
                        }
                    } finally {
                        downloadRunning = false
                    }
                }
                val problem = if (result.complete) null else "Download failed"
                _status.value = _status.value.copy(progress = null, problem = problem,
                    stage = if (result.complete) RegionalDownloadStage(RegionalDownloadStage.Step.DONE)
                    else RegionalDownloadStage(RegionalDownloadStage.Step.FAILED, note = problem))
                if (!result.complete) recordFailure(ctx, "Download failed")
                Result.success()
            } catch (e: CancellationException) {
                // Stopped by the user (Pause) or by Android (Wi-Fi lost): saved bytes are kept.
                val saved = _status.value.stage?.let { maxOf(it.doneBytes, it.resumedFromBytes) } ?: 0L
                val userPaused = prefs(ctx).getBoolean("paused", false)
                _status.value = _status.value.copy(progress = null, stage = if (userPaused)
                    RegionalDownloadStage(RegionalDownloadStage.Step.PAUSED, resumedFromBytes = saved)
                else RegionalDownloadStage(RegionalDownloadStage.Step.WAITING, resumedFromBytes = saved,
                    note = "interrupted; carries on when Wi-Fi is back"))
                throw e
            } catch (e: Exception) {
                val message = e.message ?: "download failed"
                recordFailure(ctx, message)
                Result.failure()
            }
        }

        private fun info(ctx: Context, f: Float): ForegroundInfo {
            val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(android.app.NotificationChannel(CHANNEL, "Downloads",
                    android.app.NotificationManager.IMPORTANCE_LOW))
            }
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Area map for no signal")
                .setContentText("Offline map download active")
                .setOngoing(true).setSilent(true).build()
            return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(4209, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else ForegroundInfo(4209, n)
        }
    }
}
