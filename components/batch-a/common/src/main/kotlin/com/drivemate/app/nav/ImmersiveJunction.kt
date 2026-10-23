package com.drivemate.app.nav

/**
 * When the immersive junction screen is on. On the approach to a junction (motorway exits and
 * splits, plus turns and roundabouts where real lane data gives a lane choice) the screen shows
 * the drawn junction scene ([JunctionScene]) in place of the live map, and goes back to the map
 * after the junction. Pure rules only, so they can be tested without a phone.
 */
object ImmersiveJunction {
    /**
     * Where the mode is: [step] is the route step (junction) it is showing, or -1 when off.
     * [range] is how far ahead it was switched on, used for the leaving margin and for how near
     * the junction is drawn.
     */
    data class Mode(val step: Int = -1, val range: Double = 0.0) {
        val active get() = step >= 0
    }

    /**
     * One update a GPS fix.
     * @param hasPlan the junction scene would show for [step] right now (setting on, in range, a junction).
     * @param onManeuver you are on the junction itself (going round a roundabout, through the turn).
     * @param arrived the trip has ended.
     */
    fun next(prev: Mode, step: Int, distanceToNext: Double, hasPlan: Boolean, range: Double, onManeuver: Boolean, arrived: Boolean): Mode = when {
        arrived || step < 0 -> Mode()
        hasPlan -> if (prev.step == step) prev.copy(range = range) else Mode(step, range)
        // Keep it through the junction itself (the plan isn't worked out while on a roundabout)
        // and over GPS wobble at the edge of the range. Leaves once the route moves on to the next step.
        prev.active && prev.step == step && (onManeuver || distanceToNext <= prev.range * 1.25 + 30.0) -> prev
        else -> Mode()
    }
}

/**
 * What the immersive junction screen shows: the junction [plan] for route step [step] (kept while
 * on a roundabout, where no new plan is worked out), [distance] metres to it (0 once on it) and
 * the [range] it came on at, so the scene can draw the junction coming nearer.
 */
data class ImmersiveView(
    val step: Int, val plan: JunctionPlan, val distance: Double, val range: Double,
    /** MG09: the shared timestamped progress this view was made from (null in older callers/tests). */
    val progress: SharedProgress? = null,
    /** What the position can support right now (MG13). */
    val level: GuidanceLevel = GuidanceLevel.LANE_DETAIL,
    /** How far round the roundabout the car has measurably gone (0..1), once on it; null = not on it or not known. */
    val ringFraction: Float? = null,
) {
    /** 1 when the scene comes on, 0 at the junction. */
    val approach: Float get() = JunctionScene.approachFraction(distance, range)

    /**
     * Metres to the junction as of [nowMs] on the shared clock, so the picture flows with the car
     * between fixes and agrees with the map camera, the card and the voice. 0 once on the junction.
     */
    fun distanceAt(nowMs: Long): Double =
        if (distance <= 0.0) 0.0 else progress?.distanceAt(nowMs)?.coerceAtMost(distance) ?: distance
}

/**
 * RV11: where the car is relative to the junction, as the one value both panels read: the driving
 * perspective above draws the junction [distanceM] ahead and the overview below puts its marker at the
 * same progress ([approachProgress] along the approach, then [ringFraction] round the ring).
 */
data class JunctionMarker(
    val distanceM: Float,
    val rangeM: Float,
    val speedMps: Double = 0.0,
    val ringFraction: Float? = null,
) {
    /** 0 where the junction came into range, 1 at the junction. */
    val approachProgress: Float get() = if (rangeM <= 0f) 1f else (1f - distanceM / rangeM).coerceIn(0f, 1f)

    companion object {
        fun of(view: ImmersiveView, nowMs: Long) = JunctionMarker(
            view.distanceAt(nowMs).toFloat(), view.range.toFloat(),
            view.progress?.speedMps ?: 0.0, view.ringFraction,
        )
    }
}

/**
 * RV10: the drawn junction's fade in and out as one time-based value (0 map only, 1 scene only).
 * Reversing half way (the junction view is dropped just after it opened) turns round from where it
 * is, with no jump. A long frame gap moves it at most [MAX_STEP_MS] worth, never a lurch.
 */
class ImmersiveBlend(private val fadeMs: Long = 250L) {
    var alpha = 0f
        private set
    private var lastMs = Long.MIN_VALUE

    fun step(timeMs: Long, on: Boolean): Float {
        val dt = if (lastMs == Long.MIN_VALUE) 0L else (timeMs - lastMs).coerceIn(0L, MAX_STEP_MS)
        lastMs = timeMs
        val delta = dt.toFloat() / fadeMs
        alpha = if (on) minOf(1f, alpha + delta) else maxOf(0f, alpha - delta)
        return alpha
    }

    /** Only a fully drawn scene hides the map completely. */
    val opaque get() = alpha >= 1f
    val settled get() = alpha <= 0f || alpha >= 1f

    companion object { const val MAX_STEP_MS = 100L }
}

/** How often the screen is drawn around the immersive junction scene (battery). */
object SceneFrameRate {
    /** The drawn scene is redrawn at most this often (about 30 frames a second). */
    const val SCENE_FRAME_NANOS = 33_000_000L
    /** The live map hidden under the scene keeps only a trickle of frames (it is not seen). */
    const val COVERED_MAP_FPS = 2

    /**
     * MG13: whether the scene hides the map completely. Only a fully opaque scene does; while it
     * fades in or out (or any time the map can be seen) the map keeps its full frame rate.
     */
    fun covers(sceneAlpha: Float, level: GuidanceLevel) = sceneAlpha >= 1f && level.showsScene

    /** Frames a second for the live map: [easeOff] as before, and nearly none while the scene covers it. */
    fun mapFps(easeOff: Int, sceneCovering: Boolean, visibleOverview: Boolean = false): Int = when {
        sceneCovering -> COVERED_MAP_FPS
        easeOff == 1 -> 20
        easeOff == 2 -> 15
        visibleOverview -> 30
        else -> 60
    }

    /** True when a new scene frame is due: at least [SCENE_FRAME_NANOS] since the last one drawn. */
    fun due(lastNanos: Long, nowNanos: Long): Boolean = lastNanos == 0L || nowNanos - lastNanos >= SCENE_FRAME_NANOS
}
