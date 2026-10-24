package com.drivemate.app.controller
import com.drivemate.app.data.HazardType
import com.drivemate.app.nav.Geo
import com.drivemate.app.nav.LatLon

internal object SystemClock {
    var now = 100_000L
    fun elapsedRealtimeNanos() = now * 1_000_000
}
internal data class Hazard(val id: Long, val type: HazardType, val pos: LatLon, val fixed: Boolean = true,
    val limitKmh: Double? = null, val note: String? = null) {
    fun expired(now: Long) = false
    fun appliesTo(heading: Double?) = true
}
internal class Location(val latitude: Double = 0.0, val longitude: Double = 0.0,
    val speed: Float = 20f, val provider: String = "gps", val elapsedRealtimeNanos: Long = 100_000L * 1_000_000,
    val speedPresent: Boolean = true) {
    fun hasSpeed() = speedPresent
    fun hasBearing() = true
    val bearing = 0f
}
internal data class Progress(val along: Double)
internal class WarningWiringHarness {
    enum class Mode { NAVIGATING, IDLE }
    enum class SoundMode { ALL, ALERTS, OFF }
    private class Settings { var beepAlerts = false; var sound = SoundMode.ALL }
    private class Sink {
        val calls = mutableListOf<String>()
        fun say(text: String, alert: Boolean, mainAlert: Boolean = false) { calls += "speech:"+text+":"+mainAlert }
        fun beep(urgent: Boolean) { calls += "tone:"+urgent }
    }
    private object format { fun limit(kmh: Double) = kmh.toInt().toString() }
    private object storage { fun saveHazards(h: List<Hazard>) {} }
    private class Road(val segments: List<Pair<LatLon, LatLon>>)
    private fun roadHere(loc: Location): Road? = null
    private fun refreshVisibleHazards(force: Boolean) {}
    private fun recomputeHazardAlong() {}
    private fun isSchoolTime() = true
    private val settings = Settings()
    private val voice = Sink()
    private var arrivedAt: LatLon? = null
    private var hazards = emptyList<Hazard>()
    private var liveIncidents = emptyList<Hazard>()
    private var roadAlerts = emptyList<Hazard>()
    private var bends = emptyList<Hazard>()
    private var mode = Mode.NAVIGATING
    private var engine: Any? = Any()
    private var progress: Progress? = Progress(0.0)
    private var positionReliable = true
    private val alertedHazards = hashSetOf<Long>()
    private val cameraSpeedWarnings = hashSetOf<Long>()
    private val cameraApproaches = hashSetOf<Long>()
    private val hazardAlong = hashMapOf<Long, Double>()
    private var hazardAlert: Hazard? = null
    private var alertClosest = 0.0
    fun run(items: List<Hazard>, along: Map<Long, Double>, speed: Float = 20f, reliable: Boolean = true,
            ageMs: Long = 0, provider: String = "gps", at: Double = 0.0, beep: Boolean = false): List<String> {
        hazards = items; hazardAlong.clear(); hazardAlong.putAll(along); progress = Progress(at)
        positionReliable = reliable; settings.beepAlerts = beep; voice.calls.clear()
        checkHazards(Location(speed = speed, elapsedRealtimeNanos = (SystemClock.now - ageMs) * 1_000_000, provider = provider))
        return voice.calls.toList()
    }
    private fun checkHazards(loc: Location) {
        // Just arrived: no warnings around the destination (they come back once you drive on).
        arrivedAt?.let { a -> if (Geo.distance(a, LatLon(loc.latitude, loc.longitude)) < 300) return }
        val now = System.currentTimeMillis()
        if (hazards.any { it.expired(now) }) {
            hazards = hazards.filterNot { it.expired(now) }
            refreshVisibleHazards(force = true)
            storage.saveHazards(hazards)
        }
        // Live reports not re-confirmed by their source in time expire (they are only in memory).
        if (liveIncidents.any { com.drivemate.app.data.TrafficDetails.stale(it, now) }) {
            liveIncidents = liveIncidents.filterNot { com.drivemate.app.data.TrafficDetails.stale(it, now) }
            refreshVisibleHazards(force = true)
            recomputeHazardAlong()
        }
        val p = LatLon(loc.latitude, loc.longitude)
        val pr = progress
        val navigating = mode == Mode.NAVIGATING && engine != null && pr != null
        val speedAge = loc.elapsedRealtimeNanos.takeIf { it > 0 && it <= SystemClock.elapsedRealtimeNanos() }
            ?.let { (SystemClock.elapsedRealtimeNanos() - it) / 1_000_000 }
        val cameraSpeed = com.drivemate.app.nav.CameraApproachPolicy.freshSpeed(
            loc.speed.toDouble().takeIf { loc.hasSpeed() },
            com.drivemate.app.ui.PositionKind.of(loc.provider) == com.drivemate.app.ui.PositionKind.MEASURED, speedAge)
        for (h in hazards + liveIncidents + roadAlerts + bends) {
            val routeCamera = navigating && h.type.isCamera
            if (h.id in alertedHazards && !routeCamera) continue
            if (routeCamera && !positionReliable) continue
            val d = if (routeCamera) (hazardAlong[h.id]?.minus(pr!!.along) ?: continue) else Geo.distance(p, h.pos)
            val range = if (routeCamera) com.drivemate.app.nav.CameraApproachPolicy.approachMetres(cameraSpeed) else h.type.alertRange.toDouble()
            if (d > range) continue
            if (h.type.navOnly && !navigating) continue
            if (loc.speed < h.type.minSpeed) continue
            if (h.type == HazardType.SCHOOL && !isSchoolTime()) continue
            val heading = if (loc.hasBearing() && loc.speed > 3) loc.bearing.toDouble() else null
            val ahead = when {
                // Following a route: only what's on your route ahead (already checked to be on
                // your side of the road when the route was matched).
                navigating && pr != null -> (hazardAlong[h.id] ?: -1.0) > pr.along
                // No route, driving: only things ahead on the road you're on, for your direction.
                heading != null && loc.speed > 7 -> {
                    val road = roadHere(loc)
                    val inFront = d < 500 && Geo.angleDiff(heading, Geo.bearing(p, h.pos)) < 60 && h.appliesTo(heading)
                    if (road != null) {
                        val reach = if (h.type == HazardType.SCHOOL) 150.0 else 20.0
                        inFront && road.segments.any { (a, b) -> Geo.project(h.pos, a, b).distance < reach }
                    } else false // Unknown road: do not guess warnings on a parallel road.
                }
                // Parked or crawling without a route: only very close things.
                else -> roadHere(loc)?.segments?.any { (a, b) -> Geo.project(h.pos, a, b).distance < 20 } == true && d < 150 && h.appliesTo(heading)
            }
            if (!ahead) continue
            if (routeCamera && h.id in cameraApproaches) {
                if (h.id in cameraSpeedWarnings ||
                    d > com.drivemate.app.nav.CameraApproachPolicy.extraMetres(cameraSpeed) ||
                    !com.drivemate.app.nav.CameraApproachPolicy.isOver(cameraSpeed, h.limitKmh)) continue
                cameraSpeedWarnings += h.id
                voice.say("Speed camera ahead. You are over the limit", alert = true, mainAlert = true)
                break
            }
            alertedHazards += h.id
            if (routeCamera) cameraApproaches += h.id
            hazardAlert = h
            alertClosest = d
            // A run of speed bumps (each within ~150 m of the next): one warning, like Yandex.
            val run = if (h.type == HazardType.SPEED_BUMP) bumpRun(h, navigating, pr, loc) else emptyList()
            if (run.isNotEmpty()) {
                alertedHazards += run.map { it.id }
                voice.say("Several speed bumps ahead", alert = true)
                break
            }
            if (settings.beepAlerts && settings.sound == SoundMode.ALL && h.type != HazardType.SPEED_BUMP && !h.type.isCamera) {
                voice.beep(urgent = h.type.isCamera)
            } else {
                val limit = h.limitKmh?.let { ", limit ${format.limit(it)}" } ?: ""
                val what = h.note ?: h.type.spoken
                voice.say(if (h.fixed) "$what ahead$limit" else "$what reported ahead", alert = true,
                    mainAlert = com.drivemate.app.nav.VoicePlaybackPolicy.mainHazard(h.type.name))
            }
            break
        }
    }

    /** The speed bumps following [first] on your way, each within ~150 m of the one before. */
    private fun bumpRun(first: Hazard, navigating: Boolean, pr: Progress?, loc: Location): List<Hazard> {
        var bumps = (hazards + roadAlerts).filter { it.type == HazardType.SPEED_BUMP && it.id != first.id }
        if (!navigating || pr == null) {
            // No route: only bumps in front of you on the road you're on (never side streets).
            val p = LatLon(loc.latitude, loc.longitude)
            val heading = if (loc.hasBearing()) loc.bearing.toDouble() else null
            val road = roadHere(loc)
            bumps = bumps.filter { b ->
                (heading == null || Geo.angleDiff(heading, Geo.bearing(p, b.pos)) < 60) &&
                    road != null && road.segments.any { (a, c) -> Geo.project(b.pos, a, c).distance < 20 }
            }
        }
        val out = ArrayList<Hazard>()
        if (navigating && pr != null) {
            var at = hazardAlong[first.id] ?: return emptyList()
            val ahead = bumps.mapNotNull { b -> hazardAlong[b.id]?.let { b to it } }.filter { it.second > at }.sortedBy { it.second }
            for ((b, a) in ahead) {
                if (a - at > 150) break
                out += b
                at = a
            }
        } else {
            var last = first.pos
            val left = bumps.filter { Geo.distance(it.pos, first.pos) < 600 }.toMutableList()
            while (true) {
                val next = left.minByOrNull { Geo.distance(it.pos, last) }?.takeIf { Geo.distance(it.pos, last) < 150 } ?: break
                out += next
                left.remove(next)
                last = next.pos
            }
        }
        return out
    }


}
