package com.drivemate.app.nav

/** Deterministic source selection. Providers are selected, never averaged. Times are monotonic. */
class LocationSelectionPolicy {
    enum class Source { GOOGLE, GPS, NETWORK }
    data class Fix(
        val source: Source, val elapsedMs: Long, val latitude: Double, val longitude: Double,
        val accuracyM: Double, val speedMps: Double? = null, val heading: Double? = null,
    )

    private val latest = mutableMapOf<Source, Fix>()
    private var selected: Source? = null
    private var lastDeliveredAt = -1L
    private var googleRecoveryStarted = -1L
    private var googleRecoveryCount = 0
    var lastDelivered: Fix? = null
        private set

    fun reset() {
        latest.clear(); selected = null; lastDeliveredAt = -1
        googleRecoveryStarted = -1; googleRecoveryCount = 0; lastDelivered = null
    }

    fun accept(fix: Fix, nowMs: Long, navigation: Boolean): Boolean {
        if (!valid(fix, nowMs) || (navigation && (fix.source == Source.NETWORK || fix.accuracyM > 50))) return false
        val old = latest[fix.source]
        if (old != null && fix.elapsedMs <= old.elapsedMs) return false
        // Reject impossible movements before updating provider health: otherwise a stream of
        // recent but unusable primary fixes can starve a perfectly valid secondary feed.
        if (fix.elapsedMs <= lastDeliveredAt) return false
        val previous = lastDelivered
        if (previous != null) {
            val dt = (fix.elapsedMs - previous.elapsedMs) / 1_000.0
            val distance = Geo.distance(LatLon(previous.latitude, previous.longitude), LatLon(fix.latitude, fix.longitude))
            if (GpsFilter.rejects(dt, distance, fix.accuracyM, previous.accuracyM)) return false
        }
        latest[fix.source] = fix
        val google = latest[Source.GOOGLE]?.takeIf { fresh(it, nowMs) }
        val gps = latest[Source.GPS]?.takeIf { fresh(it, nowMs) }
        val googleGood = google != null && google.accuracyM <= 50 &&
            (gps == null || google.accuracyM <= maxOf(40.0, gps.accuracyM * 2))
        if (fix.source == Source.GOOGLE) {
            if (googleGood) {
                if (googleRecoveryStarted < 0) googleRecoveryStarted = fix.elapsedMs
                googleRecoveryCount++
            } else { googleRecoveryStarted = -1; googleRecoveryCount = 0 }
        }
        val preferred = when {
            selected == Source.GPS && googleGood &&
                (googleRecoveryCount < 3 || nowMs - googleRecoveryStarted < 2_000) -> Source.GPS
            googleGood -> Source.GOOGLE
            gps != null && gps.accuracyM <= 50 -> Source.GPS
            !navigation && fix.source == Source.NETWORK && google == null && gps == null -> Source.NETWORK
            !navigation && fix.source == Source.GOOGLE && gps == null -> Source.GOOGLE
            !navigation && fix.source == Source.GPS && google == null -> Source.GPS
            else -> return false
        }
        if (fix.source != preferred || fix.elapsedMs <= lastDeliveredAt) return false
        if (preferred == Source.GPS && selected != Source.GPS) {
            googleRecoveryStarted = -1
            googleRecoveryCount = 0
        }
        selected = preferred
        lastDeliveredAt = fix.elapsedMs
        lastDelivered = fix
        return true
    }

    fun primaryHealthy(nowMs: Long): Boolean = latest[Source.GOOGLE]?.let { google ->
        selected == Source.GOOGLE && lastDelivered?.let { it.source == Source.GOOGLE && fresh(it, nowMs) } == true && fresh(google, nowMs) && google.accuracyM <= 50 &&
            (latest[Source.GPS]?.takeIf { fresh(it, nowMs) }?.let { google.accuracyM <= maxOf(40.0, it.accuracyM * 2) } ?: true)
    } ?: false

    fun authoritative(nowMs: Long): Boolean = lastDelivered?.let { fresh(it, nowMs) && it.accuracyM <= 50 && it.source != Source.NETWORK } ?: false

    companion object {
        const val MAX_AGE_MS = 8_000L
        fun fresh(fix: Fix, nowMs: Long): Boolean = fix.elapsedMs > 0 && nowMs - fix.elapsedMs in -1_000L..MAX_AGE_MS
        fun valid(fix: Fix, nowMs: Long): Boolean =
            fresh(fix, nowMs) && fix.latitude.isFinite() && fix.latitude in -90.0..90.0 &&
                fix.longitude.isFinite() && fix.longitude in -180.0..180.0 && fix.accuracyM.isFinite() && fix.accuracyM in 0.1..200.0 &&
                (fix.speedMps == null || (fix.speedMps.isFinite() && fix.speedMps in 0.0..100.0)) &&
                (fix.heading == null || (fix.heading.isFinite() && fix.heading in 0.0..360.0))
    }
}

/** Browsing backs off after a minute stopped, and resumes immediately when movement returns. */
class TrackingMotionPolicy {
    private var stillSince: Long? = null
    private var stopped = false
    fun stationary(speedMps: Float, nowMs: Long): Boolean {
        if (!speedMps.isFinite() || speedMps < 0) return false
        if (speedMps >= 2) { stillSince = null; stopped = false; return false }
        if (stopped) return true
        if (speedMps < 0.8f && stillSince == null) stillSince = nowMs
        if (speedMps >= 0.8f) stillSince = null
        stopped = stillSince?.let { nowMs - it >= 60_000 } ?: false
        return stopped
    }
    fun reset() { stillSince = null; stopped = false }
}
