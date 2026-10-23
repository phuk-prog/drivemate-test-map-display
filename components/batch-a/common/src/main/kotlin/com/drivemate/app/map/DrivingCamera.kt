package com.drivemate.app.map

import com.drivemate.app.data.MapView
import com.drivemate.app.nav.Geo
import com.drivemate.app.nav.HeadingEstimator
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln

/** Camera presentation policy, independent of MapLibre style/overlay rendering. */
internal data class DrivingCameraTarget(val zoom: Double, val tilt: Double, val bearing: Double, val topPaddingFraction: Double)
internal object DrivingCamera {
    /** Measured overlay occlusion in physical pixels, not assumed fixed card sizes. */
    data class ViewportInsets(val top: Int = 0, val bottom: Int = 0, val left: Int = 0, val right: Int = 0)

    fun padding(width: Int, height: Int, insets: ViewportInsets, aheadFraction: Double): DoubleArray {
        val w = width.coerceAtLeast(1).toDouble()
        val h = height.coerceAtLeast(1).toDouble()
        // Keep a usable viewport even while a large-text panel is being measured/resized.
        val verticalScale = minOf(1.0, h * 0.75 / maxOf(1.0, insets.top.coerceAtLeast(0).toDouble() + insets.bottom.coerceAtLeast(0)))
        val horizontalScale = minOf(1.0, w * 0.75 / maxOf(1.0, insets.left.coerceAtLeast(0).toDouble() + insets.right.coerceAtLeast(0)))
        val top = insets.top.coerceAtLeast(0) * verticalScale
        val bottom = insets.bottom.coerceAtLeast(0) * verticalScale
        val usableHeight = h - top - bottom
        // MapLibre centres the target within padding: extra top moves it down within usable space.
        return doubleArrayOf(insets.left.coerceAtLeast(0) * horizontalScale,
            top + usableHeight * aheadFraction.coerceIn(0.0, 0.5),
            insets.right.coerceAtLeast(0) * horizontalScale, bottom)
    }

    fun attributionBottomMargin(height: Int, insets: ViewportInsets, density: Float): Int {
        val gap = (12 * density.coerceAtLeast(0.5f)).toInt()
        val bottom = padding(1, height, insets, 0.0)[3].toInt()
        return (bottom + gap).coerceIn(gap, maxOf(gap, height - gap * 3))
    }

    /**
     * Finish ordinary motion before the next fix, and settle a changed road heading quickly.
     * Animating for the entire fix interval leaves the camera perpetually behind at turns.
     */
    fun animationDuration(requestedMs: Int, currentBearing: Double, targetBearing: Double): Int {
        if (requestedMs <= 0) return 0
        val ordinary = (requestedMs * 0.7).toInt().coerceIn(1, 750)
        if (!currentBearing.isFinite() || !targetBearing.isFinite()) return ordinary
        return if (Geo.angleDiff(currentBearing, targetBearing) >= 20.0) minOf(ordinary, 180) else ordinary
    }

    /**
     * At most [maxStep] zoom levels a fix, so a big zoom change glides over a couple of seconds instead of jumping.
     */
    fun limitZoomStep(current: Double, target: Double, maxStep: Double = 1.2): Double =
        if (!current.isFinite()) target else target.coerceIn(current - maxStep, current + maxStep)

    /** Zoom levels a second the driving camera may glide (1.2 a fix at the usual one fix a second). */
    const val ZOOM_RATE_PER_SECOND = 1.2

    /** [limitZoomStep] by elapsed time, so five fixes a second glide no faster than one. */
    fun limitZoomStepForElapsed(current: Double, target: Double, elapsedMs: Long): Double =
        limitZoomStep(current, target, ZOOM_RATE_PER_SECOND * elapsedMs.coerceIn(100L, 1_500L) / 1000.0)

    /**
     * How sharply the route bends ahead: the largest turn (degrees, 0-180) between the road's
     * direction here ([bearings] first) and its direction further on.
     */
    fun bendDegrees(bearings: List<Double>): Double {
        val usable = bearings.filter { it.isFinite() }
        val first = usable.firstOrNull() ?: return 0.0
        return usable.maxOf { Geo.angleDiff(first, it) }
    }

    /**
     * Closest zoom at which a point [metres] straight ahead of the car still fits in the road
     * shown above it ([aheadDp] screen points; conservative, as the tilted view shows more).
     * MapLibre uses 512-point tiles.
     */
    fun zoomShowing(metres: Double, latitude: Double, aheadDp: Double = 600.0): Double {
        val c = cos(Math.toRadians(latitude.coerceIn(-85.0, 85.0)))
        return ln(EARTH_CIRCUMFERENCE * c * aheadDp / (512.0 * metres.coerceAtLeast(1.0))) / ln(2.0)
    }

    private const val EARTH_CIRCUMFERENCE = 40_075_016.686

    /** Share of the driving view above the car (it sits low on the screen, the road ahead above it). */
    const val ABOVE_CAR_SHARE = 0.7

    /**
     * 11.4: the zoom for a turn view. Nearing the same turn it only ever zooms in (the stretch from
     * the car through the turn keeps shrinking); a new turn, or a second turn coming into the view,
     * may zoom out. Never closer than street level, so the road after the turn is still visible.
     */
    fun turnFrameZoom(previous: Double?, fitted: Double, sameTurn: Boolean): Double {
        val z = if (sameTurn && previous != null && previous.isFinite()) maxOf(previous, fitted) else fitted
        return z.coerceIn(15.5, 17.8)
    }

    /** Fixed-rate zoom bound; long GPS gaps don't turn into an abrupt jump. */
    fun limitZoomOverTime(current: Double, target: Double, elapsedMs: Long): Double =
        limitZoomStep(current, target, 1.2 * elapsedMs.coerceIn(0L, 1500L) / 1000.0)

    fun target(view: MapView, zoom: Double, tilt: Double, bearing: Double, frameZoom: Double?,
               continuousGuidance: Boolean = false, blend: Double = if (continuousGuidance) 1.0 else 0.0): DrivingCameraTarget {
        val flat = view != MapView.TILTED
        val amount = if (continuousGuidance && blend.isFinite()) blend.coerceIn(0.0, 1.0) else 0.0
        // A single perspective projection enlarges the near road and retains distant context.
        // Geographic route framing stays authoritative; no second map or invented lane geometry.
        val perspective = if (amount > 0.0) (tilt + 10.0 * amount).coerceIn(0.0, 55.0) else tilt
        return DrivingCameraTarget(frameZoom ?: zoom, if (flat) 0.0 else perspective,
            if (view == MapView.NORTH_UP) 0.0 else bearing,
            if (view == MapView.NORTH_UP) 0.1 else if (flat) 0.4 else 0.5)
    }
}

/**
 * MG12 panel split: the guidance/journey panels change height when a junction card opens or closes.
 * Following that exactly would jump the car on the screen, so the insets glide toward the
 * measured size at no more than [RATE_PX_PER_S], by elapsed time, and changes under [DEAD_PX]
 * (a line of text wrapping) are ignored.
 */
internal class InsetGlide {
    var current = DrivingCamera.ViewportInsets()
        private set
    private var lastMs = Long.MIN_VALUE

    fun update(target: DrivingCamera.ViewportInsets, timeMs: Long): DrivingCamera.ViewportInsets {
        val dt = if (lastMs == Long.MIN_VALUE) 1_000L else (timeMs - lastMs).coerceIn(0L, 1_500L)
        lastMs = timeMs
        val step = (RATE_PX_PER_S * dt / 1000.0).toInt()
        fun move(from: Int, to: Int) = when {
            Math.abs(to - from) < DEAD_PX && to != 0 -> from
            else -> from + (to - from).coerceIn(-step, step)
        }
        current = DrivingCamera.ViewportInsets(move(current.top, target.top), move(current.bottom, target.bottom),
            move(current.left, target.left), move(current.right, target.right))
        return current
    }

    /** Start from [insets] exactly (first measurement), so the first frame is not a slide from zero. */
    fun snap(insets: DrivingCamera.ViewportInsets, timeMs: Long) { current = insets; lastMs = timeMs }

    fun settled(target: DrivingCamera.ViewportInsets) = current == target ||
        (Math.abs(current.top - target.top) < DEAD_PX && Math.abs(current.bottom - target.bottom) < DEAD_PX &&
            Math.abs(current.left - target.left) < DEAD_PX && Math.abs(current.right - target.right) < DEAD_PX)

    companion object {
        const val RATE_PX_PER_S = 600.0
        const val DEAD_PX = 12
    }
}

/**
 * MG12 low-speed jitter: when nearly stopped, GPS wander of a few metres must not shuffle the
 * map under the car. Holds the last camera centre until the car has clearly moved
 * ([HOLD_M], or faster than [MOVING_MPS]).
 */
internal class CameraAnchor {
    private var held: com.drivemate.app.nav.LatLon? = null

    fun anchor(at: com.drivemate.app.nav.LatLon, speedMps: Double): com.drivemate.app.nav.LatLon {
        val h = held
        val moving = speedMps.isFinite() && speedMps >= MOVING_MPS
        if (h == null || moving || Geo.distance(h, at) >= HOLD_M) held = at
        return held!!
    }

    fun reset() { held = null }

    companion object {
        const val MOVING_MPS = 1.0
        const val HOLD_M = 4.0
    }
}

/**
 * RV04 adaptive driving zoom: further out the faster you go, a little closer on a winding road,
 * gliding in for a turn while keeping the turn itself in view.
 *
 * All smoothing is by elapsed time, not by number of fixes, so one fix a second and five fixes a
 * second give the same zoom. A speed band changes only once the smoothed speed is clearly past
 * its edge, and tiny zoom differences are ignored, so a wobbly speed never pumps the map.
 */
internal class AdaptiveZoom(
    /** Seconds over which the speed is averaged (about ten fixes at one a second, as before). */
    private val speedTauSeconds: Double = 8.0,
) {
    /** Smoothed speed in m/s (negative until the first fix). */
    var smoothedSpeed = -1.0
        private set
    /** Current speed band: 0 slow streets, 1 town, 2 main road, 3 motorway. */
    var band = -1
        private set
    private var curvy = false
    private var lastMs = Long.MIN_VALUE
    private var output = Double.NaN

    /**
     * Zoom for a fix at [timeMs] (elapsed-realtime milliseconds). [distanceToNext] is metres to
     * the next manoeuvre, [turnAhead] whether it is a real turn (not arrive/depart),
     * [bendDegrees] how sharply the route bends over the next stretch ([DrivingCamera.bendDegrees]).
     */
    fun update(
        timeMs: Long, speedMps: Double, distanceToNext: Double, turnAhead: Boolean, onRoundabout: Boolean,
        bendDegrees: Double = 0.0, latitude: Double = 53.4,
    ): Double {
        val raw = if (speedMps.isFinite()) speedMps.coerceAtLeast(0.0) else smoothedSpeed.coerceAtLeast(0.0)
        val first = lastMs == Long.MIN_VALUE
        val dt = if (first) 0.0 else (timeMs - lastMs) / 1000.0
        if (first || dt > 0) lastMs = timeMs
        smoothedSpeed = when {
            smoothedSpeed < 0 || dt > GAP_SECONDS -> raw
            dt <= 0 -> smoothedSpeed
            else -> smoothedSpeed + (raw - smoothedSpeed) * (1 - exp(-dt / speedTauSeconds))
        }
        val now = EDGES.count { smoothedSpeed > it }
        band = when {
            band < 0 -> now
            now > band && smoothedSpeed > EDGES[now - 1] + MARGIN -> now
            now < band && smoothedSpeed < EDGES[band - 1] - MARGIN -> now
            else -> band
        }
        // Winding road: enter at a clear bend, leave only once it has clearly straightened.
        curvy = if (curvy) bendDegrees > BEND_LEAVE else bendDegrees >= BEND_ENTER
        // A long way to the next turn on a fast straight road: ease out a little more.
        var target = if (band >= 2 && distanceToNext > 3000 && !curvy) 14.6 else CRUISE[band]
        if (curvy) target = maxOf(target, CRUISE[(band - 1).coerceAtLeast(0)])
        if (turnAhead) {
            if (onRoundabout) target = maxOf(target, 17.2)
            else if (distanceToNext < maxOf(300.0, smoothedSpeed * 12.0)) {
                // Glide in for the turn, but never so close that the turn itself is off the top
                // (and no further out than the road's own cruising view needs).
                val showTurn = DrivingCamera.zoomShowing(distanceToNext + TURN_MARGIN, latitude)
                target = minOf(17.4, showTurn).coerceAtLeast(minOf(target, 15.0))
            }
        }
        val prev = output
        output = if (prev.isFinite() && Math.abs(target - prev) < DEAD_BAND) prev else target
        return output
    }

    companion object {
        /** Band edges in m/s (about 20, 38 and 56 mph). */
        private val EDGES = doubleArrayOf(9.0, 17.0, 25.0)
        private const val MARGIN = 1.5
        /** Slow streets, town (the next two or three streets), main road, motorway (about 1.5 miles). Town at most
         *  16.35 (test 2.9, 18 Oct 2026: closer in, 3D buildings hid the road ahead in narrow streets). */
        private val CRUISE = doubleArrayOf(16.35, 16.35, 15.8, 15.0)
        private const val GAP_SECONDS = 30.0
        const val BEND_ENTER = 60.0
        const val BEND_LEAVE = 35.0
        /** Road beyond the turn point that should still show (the start of the new road). */
        private const val TURN_MARGIN = 40.0
        /** Zoom changes smaller than this are not worth moving the map for. */
        const val DEAD_BAND = 0.15
    }
}

/**
 * RV06 camera heading: follows bends smoothly and promptly, and holds still when the heading
 * can't be trusted (stopped, or a poor-accuracy bearing), so the map never spins at lights.
 * Always turns the short way round (359 to 1 degree is two degrees, not 358). Smoothing is by
 * elapsed time, so the GPS fix rate doesn't change it.
 */
internal class CameraHeading {
    /** Heading the camera faces (degrees clockwise from north), or null before any usable fix. */
    var heading: Double? = null
        private set
    private var lastMs = Long.MIN_VALUE

    fun update(timeMs: Long, bearing: Double?, speedMps: Double, bearingAccuracyDeg: Double? = null): Double? {
        val first = lastMs == Long.MIN_VALUE
        val dt = if (first) 0.0 else (timeMs - lastMs) / 1000.0
        if (first || dt > 0) lastMs = timeMs
        val target = bearing?.takeIf { it.isFinite() }?.let { HeadingEstimator.norm(it) } ?: return heading
        val current = heading ?: run { heading = target; return target }
        val speed = if (speedMps.isFinite()) speedMps else 0.0
        val poor = bearingAccuracyDeg != null && bearingAccuracyDeg.isFinite() && bearingAccuracyDeg > POOR_ACCURACY
        if (speed < STOPPED || poor || dt <= 0) return current
        if (dt > GAP_SECONDS) { heading = target; return target }
        // Slow: the GPS direction wanders, so follow gently. Driving: keep up with the bend.
        val f = ((speed - STOPPED) / (FAST - STOPPED)).coerceIn(0.0, 1.0)
        val tau = SLOW_TAU + (FAST_TAU - SLOW_TAU) * f
        var step = HeadingEstimator.signedDiff(target, current) * (1 - exp(-dt / tau))
        if (speed < CRAWL) step = step.coerceIn(-CRAWL_RATE * dt, CRAWL_RATE * dt)
        return HeadingEstimator.norm(current + step).also { heading = it }
    }

    fun reset() { heading = null; lastMs = Long.MIN_VALUE }

    /** Before any usable GPS direction (e.g. parked at the start), face [bearing]; never overrides one. */
    fun seed(bearing: Double): Double? {
        if (heading == null && bearing.isFinite()) heading = HeadingEstimator.norm(bearing)
        return heading
    }

    companion object {
        /** m/s below which the car counts as stopped and the heading is held (about 3 mph). */
        const val STOPPED = 1.5
        /** Below this (about 7 mph) the camera turns no faster than [CRAWL_RATE] degrees a second. */
        const val CRAWL = 3.0
        const val CRAWL_RATE = 45.0
        private const val FAST = 10.0
        private const val SLOW_TAU = 1.0
        private const val FAST_TAU = 0.25
        /** Bearing accuracy (degrees) beyond which a fix's direction is ignored. */
        const val POOR_ACCURACY = 45.0
        /** After a gap this long (e.g. a tunnel), face the new direction straight away. */
        private const val GAP_SECONDS = 5.0
    }
}

/**
 * Direction of the route's road nearest [at] (degrees from north), when the car is within
 * [maxMetres] of it; null otherwise. Used so the driving map faces along the route at the start
 * instead of north while the GPS has no direction yet.
 */
internal fun routeBearingNear(route: List<com.drivemate.app.nav.LatLon>, at: com.drivemate.app.nav.LatLon, maxMetres: Double = 60.0): Double? {
    var best: Double? = null
    var bestDistance = maxMetres
    for (i in 0 until route.size - 1) {
        val a = route[i]; val b = route[i + 1]
        if (Geo.distance(a, b) < 1.0) continue
        val d = Geo.project(at, a, b).distance
        if (d <= bestDistance) { bestDistance = d; best = Geo.bearing(a, b) }
    }
    return best
}

/**
 * RV03 adaptive tilt: a lower, more immersive view on an open fast road with nothing coming up,
 * more from above at roundabouts and close to a turn so every exit is in view. Changes glide at
 * no more than [MAX_RATE] degrees a second (by elapsed time, so fix rate doesn't matter) and the
 * open-road view needs [OPEN_SPEED] for a while, so traffic-light stops don't bob the camera.
 */
internal class AdaptiveTilt {
    var tilt: Double = NORMAL
        private set
    private var lastMs = Long.MIN_VALUE
    private var fastSinceMs: Long? = null

    fun target(timeMs: Long, speedMps: Double, distanceToTurn: Double, turnAhead: Boolean, onRoundabout: Boolean): Double {
        val speed = if (speedMps.isFinite()) speedMps else 0.0
        fastSinceMs = if (speed >= OPEN_SPEED) fastSinceMs ?: timeMs else null
        val openRoad = fastSinceMs?.let { timeMs - it >= OPEN_AFTER_MS } == true && (!turnAhead || distanceToTurn > OPEN_CLEAR_M)
        return when {
            onRoundabout || (turnAhead && distanceToTurn < JUNCTION_M) -> JUNCTION
            openRoad -> OPEN
            else -> NORMAL
        }
    }

    fun update(timeMs: Long, speedMps: Double, distanceToTurn: Double, turnAhead: Boolean, onRoundabout: Boolean): Double {
        val goal = target(timeMs, speedMps, distanceToTurn, turnAhead, onRoundabout)
        val dt = if (lastMs == Long.MIN_VALUE) 0.0 else ((timeMs - lastMs) / 1000.0).coerceIn(0.0, 5.0)
        lastMs = timeMs
        val step = MAX_RATE * dt
        tilt += (goal - tilt).coerceIn(-step, step)
        return tilt
    }

    companion object {
        const val OPEN = 55.0
        const val NORMAL = 45.0
        const val JUNCTION = 35.0
        const val MAX_RATE = 8.0
        /** About 30 mph, held for [OPEN_AFTER_MS], with no turn within [OPEN_CLEAR_M]. */
        const val OPEN_SPEED = 13.0
        const val OPEN_AFTER_MS = 8_000L
        const val OPEN_CLEAR_M = 600.0
        const val JUNCTION_M = 150.0
    }
}
