package com.drivemate.app.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** HTTP transfer validation; package integrity is checked separately before installation. */
internal object ResumableDownload {
    // Publisher splits at 1900 MiB (1,992,294,400 bytes), below GitHub's 2 GB limit.
    private const val MAX_PART_BYTES = 2_000_000_000L
    private val range = Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+)")

    fun fetch(url: String, dest: File, onBytes: (Long) -> Unit) {
        val have = if (dest.isFile) dest.length() else 0L
        if (have > MAX_PART_BYTES) {
            if (!dest.delete()) throw IOException("Couldn't remove oversized download")
            throw IOException("Oversized download removed; will restart")
        }
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        conn.setRequestProperty("User-Agent", "DriveMate/1.0 (Android navigation app)")
        // Range offsets refer to the stored bytes, not a transparent HTTP compression layer.
        conn.setRequestProperty("Accept-Encoding", "identity")
        if (have > 0) conn.setRequestProperty("Range", "bytes=$have-")
        try {
            val code = conn.responseCode
            if (code == 416) {
                // A completed chunk may have survived an interrupted multi-part update.
                // Server size proves transfer length only; the final graph checksum still
                // gates installation. Never accept an unqualified rejected range.
                val total = Regex("bytes \\*/([0-9]+)").matchEntire(
                    conn.getHeaderField("Content-Range") ?: "")?.groupValues?.get(1)?.toLongOrNull()
                if (have > 0 && total == have) {
                    onBytes(have)
                    return
                }
                if (dest.exists() && !dest.delete()) throw IOException("Couldn't restart rejected download")
                throw IOException("Download range rejected; will restart")
            }
            if (code != 200 && code != 206) throw IOException("Download HTTP $code")
            val encoding = conn.getHeaderField("Content-Encoding")
            if (encoding != null && !encoding.equals("identity", ignoreCase = true)) {
                throw IOException("Unexpected download encoding")
            }
            val length = conn.contentLengthLong
            val expected: Long
            val append: Boolean
            if (code == 206) {
                val fields = range.matchEntire(conn.getHeaderField("Content-Range") ?: "")
                    ?.groupValues ?: throw IOException("Missing or invalid download range")
                val start = fields[1].toLongOrNull() ?: throw IOException("Invalid range start")
                val end = fields[2].toLongOrNull() ?: throw IOException("Invalid range end")
                val total = fields[3].toLongOrNull() ?: throw IOException("Invalid range size")
                if (start != have || end < start || end != total - 1 || total > MAX_PART_BYTES) {
                    throw IOException("Download range does not match saved bytes")
                }
                expected = end - start + 1
                if (length >= 0 && length != expected) throw IOException("Download length disagrees with range")
                append = have > 0
            } else {
                if (length > MAX_PART_BYTES) throw IOException("Download part too large")
                expected = length
                append = false // Server ignored Range: replace, never append a full response.
            }
            var received = 0L
            val offset = if (append) have else 0L
            conn.inputStream.use { input ->
                FileOutputStream(dest, append).use { out ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        received += count
                        if (offset + received > MAX_PART_BYTES || (expected >= 0 && received > expected)) {
                            throw IOException("Download body exceeds declared size")
                        }
                        out.write(buffer, 0, count)
                        onBytes(offset + received)
                    }
                }
            }
            if (expected >= 0 && received != expected) throw IOException("Download ended before all bytes arrived")
        } finally {
            conn.disconnect()
        }
    }
}
