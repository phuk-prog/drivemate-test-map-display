package com.drivemate.app.data

import java.io.File

/**
 * Where an area-map download is up to (backlog WA22), so the screen can say
 * honestly whether bytes are arriving, being checked or being installed.
 * Pure data; the wording lives in [RegionalDownloadText].
 */
data class RegionalDownloadStage(
    val step: Step,
    /** 1-based number of the map piece being worked on, 0 when not about one piece. */
    val piece: Int = 0,
    val pieces: Int = 0,
    val doneBytes: Long = 0,
    val totalBytes: Long = 0,
    /** Bytes already saved from an earlier, interrupted attempt of this exact piece. */
    val resumedFromBytes: Long = 0,
    /** Plain-English extra detail (a problem, or why a download restarted). */
    val note: String? = null,
) {
    enum class Step {
        /** Queued: waits for Wi-Fi and enough storage. */
        WAITING,
        DOWNLOADING,
        /** Checking size, file header and SHA-256 fingerprint. */
        VERIFYING,
        /** Moving a checked piece into place, then checking it again. */
        INSTALLING,
        /** Lane, speed-limit, road-information and places squares. */
        EXTRAS,
        PAUSED,
        DONE,
        FAILED,
    }

    val active: Boolean get() = step in setOf(Step.WAITING, Step.DOWNLOADING, Step.VERIFYING, Step.INSTALLING, Step.EXTRAS)
}

/**
 * What an interrupted download left behind (WA22). A partial piece is only
 * ever resumed when its file name carries the SAME package fingerprint as
 * the piece now wanted (see [RegionalMapDownloader]); anything else is stale
 * and the piece restarts, which is explained rather than hidden.
 */
internal object RegionalResume {
    private val SHA = Regex("[0-9a-f]{64}")

    data class Info(
        /** Bytes that will be carried on from, for the current map version. */
        val savedBytes: Long,
        /** Bytes left from an older map version that cannot be reused. */
        val staleBytes: Long,
    ) {
        /** A restart must be explained: old progress exists but none can be reused. */
        val restartExplained: Boolean get() = savedBytes == 0L && staleBytes > 0L
    }

    /**
     * @param expectedParts the `.part` names the current plan would resume
     *   (`<name>-<sha256>.pmtiles.part`), in [currentGeneration]'s staging folder.
     */
    fun inspect(regionsRoot: File, currentGeneration: String?, expectedParts: Set<String>): Info {
        var saved = 0L
        var stale = 0L
        root(regionsRoot).forEach { gen ->
            File(gen, "staging").listFiles()?.forEach { f ->
                if (!f.isFile || !f.name.endsWith(".part")) return@forEach
                if (gen.name == currentGeneration && f.name in expectedParts) saved += f.length() else stale += f.length()
            }
        }
        return Info(saved, stale)
    }

    private fun root(regionsRoot: File): List<File> =
        regionsRoot.listFiles()?.filter { it.isDirectory && SHA.matches(it.name) }.orEmpty()

    /** Bytes of one exact resumable piece, 0 when absent. */
    fun savedFor(stagingDir: File, expectedPart: String): Long =
        File(stagingDir, expectedPart).takeIf { it.isFile }?.length() ?: 0L
}

/** Plain-English lines for [RegionalDownloadStage]. Pure, no Android. */
object RegionalDownloadText {
    const val RESTART_NOTE =
        "The map data was updated since this download stopped, so it starts again. " +
            "Pieces from an older version are never mixed with the new one."

    private fun mb(bytes: Long): String = when {
        bytes < 1_000_000_000L -> "${maxOf(0L, (bytes + 500_000) / 1_000_000)} MB"
        else -> "%.1f GB".format(java.util.Locale.UK, bytes / 1e9)
    }

    private fun piece(s: RegionalDownloadStage): String =
        if (s.pieces > 1 && s.piece > 0) " piece ${s.piece} of ${s.pieces}" else ""

    fun line(s: RegionalDownloadStage): String = when (s.step) {
        RegionalDownloadStage.Step.WAITING -> buildString {
            append(if (s.note != null) "Waiting: ${s.note}." else "Waiting to start: needs Wi-Fi and enough free storage.")
            if (s.resumedFromBytes > 0) append(" ${mb(s.resumedFromBytes)} saved so far is kept.")
        }
        RegionalDownloadStage.Step.DOWNLOADING -> buildString {
            append("Downloading map").append(piece(s))
            if (s.totalBytes > 0) {
                val pct = (s.doneBytes.coerceIn(0, s.totalBytes) * 100 / s.totalBytes)
                append(" · $pct% (${mb(s.doneBytes)} of ${mb(s.totalBytes)})")
            }
            if (s.resumedFromBytes > 0) append(" · carrying on from ${mb(s.resumedFromBytes)} saved earlier")
        }
        RegionalDownloadStage.Step.VERIFYING ->
            "Checking map${piece(s)} is complete and undamaged…"
        RegionalDownloadStage.Step.INSTALLING ->
            "Installing map${piece(s)}…"
        RegionalDownloadStage.Step.EXTRAS ->
            "Downloading lane, speed-limit and place data for the area…"
        RegionalDownloadStage.Step.PAUSED ->
            if (s.resumedFromBytes > 0) "Paused. ${mb(s.resumedFromBytes)} saved; it carries on from there when you resume."
            else "Paused. Nothing partly downloaded is kept, so it starts from the beginning."
        RegionalDownloadStage.Step.DONE -> "Finished. Every piece was checked before use."
        RegionalDownloadStage.Step.FAILED ->
            "Download failed. Your maps already on the phone are unchanged."
    }
}
