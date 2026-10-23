package com.drivemate.app.nav

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin

/** A point on the ground: x metres to the right, z metres ahead of the car. */
private typealias G = Pt

/**
 * The immersive junction screen (owner-approved mock-up, October 2026) as plain shapes: a sky
 * above the horizon, pale land with faint cross roads, the road in dark grey perspective with
 * white edges and dashed lane lines, the junction ahead and the car arrow at the bottom. No
 * drawing library is used: the app paints these shapes full screen on a Compose canvas and the
 * JVM tests paint the very same shapes into PNG files, so the picture can be checked without a phone.
 *
 * The road is laid out on the ground in metres (x to the right, z ahead of the car) and seen by
 * a camera 9 m up, exactly as the mock-up's drawing code does. What it shows comes from data:
 *  - the lane count is the real one when known (no lane lines when it isn't);
 *  - the road you take follows the route's own geometry ([JunctionPlan.branch]): a gradual
 *    parallel peel-off or a sharp exit; a split bends both ways; roundabouts go clockwise in the
 *    UK with the exit at its real angle and one arm for each exit passed before it;
 *  - only the lane(s) to use are cyan, and only from real lane data ([RibbonSource.ARROWS]).
 *    With no lane data the way you go is a thin cyan line that starts at the junction, so it
 *    never seems to pick a lane.
 *
 * [build]'s `distance` (real metres to the junction) sets where it is drawn ([screenFraction]):
 * junctions 100-700 m ahead sit in the middle third of the road, coming nearer as you approach.
 */
object JunctionScene {
    data class P(val x: Float, val y: Float)

    sealed class Shape {
        /** A filled outline. */
        data class Fill(val points: List<P>, val argb: Long) : Shape()
        /** A line through [points]; [dash] = on/off lengths for a dashed line. */
        data class Line(val points: List<P>, val argb: Long, val width: Float, val dash: List<Float>? = null, val round: Boolean = false) : Shape()
        /** The sky: a top-to-bottom colour fade over the top [height] pixels. */
        data class Sky(val height: Float, val top: Long, val bottom: Long) : Shape()
        /** Plain background over the whole picture. */
        data class Background(val argb: Long) : Shape()
    }

    /** Colours from the mock-up (day) and a dim night version where cyan stays the brightest thing. */
    class Palette(night: Boolean) {
        val skyTop = if (night) 0xFF0A1220L else 0xFF96CDF0L
        val skyLow = if (night) 0xFF1E2B3EL else 0xFFE1F0FAL
        val land = if (night) 0xFF1D2127L else 0xFFE8EAE6L
        val faint = if (night) 0xFF2C3139L else 0xFFCDD0D4L
        val road = if (night) 0xFF464B55L else 0xFF464A52L
        val line = if (night) 0xFFBFC4CCL else 0xFFFAFAFAL
        /** Your route / recommended lane: the one route colour (see [RoutePalette]), a little translucent so paint shows through. */
        val cyan = RoutePalette.sceneFill(night)
        val island = if (night) 0xFF2E4A32L else 0xFF96BE8CL
        val car = if (night) 0xFF3C3CB4L else 0xFF28288CL
        val carEdge = 0xFFFFFFFFL
    }

    /** UK lane width (metres). */
    const val LANE_M = 3.65f
    private const val CAM_H = 9f
    private const val FAR = 420f
    private const val RING_R = 15f
    private const val RING_W = 7f
    private const val ARM_W = 7f
    /** The thin route line drawn with no lane data (metres): far narrower than a lane. */
    const val THIN_M = 0.9f
    /** The wide route line round a roundabout or into a turn when your lane is known (metres). */
    private const val WIDE_M = 1.2f
    /** The corridor round a verified continuous (spiral) lane: a little narrower than a lane so the edges show. */
    private const val CORRIDOR_M = 3.2f

    /** How much of the way in the scene still is: 1 when it comes on, 0 at the junction. */
    fun approachFraction(distance: Double, range: Double): Float =
        if (range <= 0.0) 0f else (distance / range).coerceIn(0.0, 1.0).toFloat()

    /**
     * How far down the road area (0 = the horizon, 1 = the car arrow's base) the junction is drawn
     * for a real [distance] in metres. A true camera would put anything past about 60 m on the
     * horizon, so distance is mapped to a believable place instead: 700 m sits a third of the way
     * down and 100 m two thirds (the middle third of the road), closing steadily in between
     * (even steps for each doubling of nearness), then on to just ahead of the car at the junction.
     */
    fun screenFraction(distance: Float): Float {
        val d = distance.coerceAtLeast(0f)
        return when {
            d <= NEAR_M -> AT_JUNCTION + (MID_NEAR - AT_JUNCTION) * (d / NEAR_M)
            else -> (MID_NEAR - (MID_NEAR - MID_FAR) * (ln(d / NEAR_M) / ln(FAR_M / NEAR_M))).coerceAtLeast(FARTHEST)
        }
    }

    /** The middle third of the road area: 100 m is drawn 2/3 of the way down, 700 m 1/3. */
    private const val NEAR_M = 100f
    private const val FAR_M = 700f
    private const val MID_NEAR = 2f / 3f
    private const val MID_FAR = 1f / 3f
    /** Never drawn nearer the horizon than this, however far (the junction stays readable). */
    private const val FARTHEST = 0.24f
    /** At the junction itself: just ahead of the car arrow. */
    private const val AT_JUNCTION = 0.9f

    /**
     * How far ahead (metres on the drawn ground) the junction is drawn for a real [distance], in a
     * [w] x [h] picture whose road area ends at [carBase] (the car arrow's base).
     */
    fun junctionDepth(distance: Float, w: Float, h: Float, carBase: Float? = null): Float {
        val cam = Camera(w, h, false)
        val base = carBase ?: cam.defaultBase
        return cam.depthAt(cam.horizon + (base - cam.horizon) * screenFraction(distance))
    }

    /**
     * The short line under the road signs: "Right lane · 3rd exit". [laneWords] is the lane
     * advice the voice uses ([Wording.laneWords]); it is only shown when the picture's lanes come
     * from real lane data. The exit number is the route's own. Empty when there is nothing to say.
     */
    fun hint(plan: JunctionPlan, laneWords: String): String {
        val lane = if (plan.source == RibbonSource.ARROWS) shortLane(laneWords) else ""
        val exit = if (plan.kind == JunctionKind.ROUNDABOUT && plan.roundaboutExit > 0) "${ordinal(plan.roundaboutExit)} exit" else ""
        return listOf(lane, exit).filter { it.isNotBlank() }.joinToString(" · ")
    }

    private fun shortLane(words: String): String = when {
        words.isBlank() -> ""
        words.startsWith("Use the ") -> words.removePrefix("Use the ").replaceFirstChar { it.uppercase() }
        words.startsWith("Use lane ") -> words.removePrefix("Use l").let { "L$it" }
        else -> words
    }

    private fun ordinal(n: Int) = when {
        n % 100 in 11..13 -> "${n}th"
        n % 10 == 1 -> "${n}st"
        n % 10 == 2 -> "${n}nd"
        n % 10 == 3 -> "${n}rd"
        else -> "${n}th"
    }

    /** The camera: the mock-up's maths (540 x 960 picture, horizon at 330, focal length 520), scaled. */
    private class Camera(w: Float, val h: Float, val mirror: Boolean) {
        val s = min(w / 540f, h / 960f)
        val f = 520f * s
        val horizon = h * (330f / 960f)
        val cx = w / 2f
        /** The nearest ground the bottom of the picture shows, a little closer so shapes run off the bottom. */
        val zBottom = f * CAM_H / (h - horizon) * 0.85f
        fun p(g: G): P = P(cx + f * (if (mirror) -g.x else g.x) / g.z, horizon + f * CAM_H / g.z)
        /** The car arrow's base when the caller gives none (just above the bottom bar). */
        val defaultBase = h - 95f * s
        /** The ground distance drawn at picture height [y] (below the horizon). */
        fun depthAt(y: Float): Float = f * CAM_H / (y - horizon).coerceAtLeast(1f)
    }

    /**
     * The scene's shapes for a [w] x [h] picture with the junction [distance] metres ahead (real,
     * from the route; 0 on the junction itself) and the car arrow's base at [carBase].
     */
    fun build(plan: JunctionPlan, w: Float, h: Float, night: Boolean, distance: Float = 300f, carBase: Float? = null, schematicRoundabout: Boolean = false): List<Shape> {
        val c = Palette(night)
        val out = ArrayList<Shape>()
        // Exits and splits are laid out with the way you go on the left, then mirrored for the right.
        val mirror = plan.kind == JunctionKind.EXIT && !plan.exitLeft
        val cam = Camera(w, h, mirror)
        out += Shape.Background(c.land)
        out += Shape.Sky(cam.horizon, c.skyTop, c.skyLow)
        // Faint cross roads for context (the mock-up's three lines across the land).
        for (z in listOf(90f, 170f, 260f)) {
            val y = cam.p(G(0f, z)).y
            out += Shape.Line(listOf(P(0f, y), P(w, y)), c.faint, 2f * cam.s)
        }
        val depth = junctionDepth(distance, w, h, carBase)
        // RV01: the road ahead follows the route's real approach line when the model has it; else it is drawn straight.
        val warp = ApproachWarp.of(plan.model, distance, depth, LANE_M * 2.5f)
        val d = Draw(cam, out, c, warp, distance)
        // MG08: roads that cross without joining (bridges, underpasses), from the same model as the overview.
        val crossModel = plan.model?.takeIf { it.armsTrusted && plan.kind != JunctionKind.EXIT }
        crossModel?.let { d.crossings(plan, it, depth, over = false) }
        when (plan.kind) {
            JunctionKind.EXIT -> d.exit(plan, depth)
            JunctionKind.ROUNDABOUT -> d.roundabout(plan, depth, schematicRoundabout)
            JunctionKind.TURN -> d.turn(plan, depth)
        }
        crossModel?.let { d.crossings(plan, it, depth, over = true) }
        // The car arrow, bottom centre, just above the bottom bar.
        val base = carBase ?: cam.defaultBase
        val s = cam.s
        val car = listOf(P(cam.cx, base - 75f * s), P(cam.cx - 34f * s, base), P(cam.cx, base - 20f * s), P(cam.cx + 34f * s, base))
        out += Shape.Fill(car, c.car)
        out += Shape.Line(car + car.first(), c.carEdge, 2f * s)
        return out
    }

    /**
     * The middle of the road you take from the split on, on the ground (x metres, to the left
     * negative, laid out with the exit on the left; z metres ahead): the route's real shape when
     * it gave one ([JunctionPlan.branch]), else a standard slip road (or split) curving away.
     */
    private fun takenAfterSplit(plan: JunctionPlan, n: Int, k: Int, x0: Float, zs: Float): List<G> {
        val start = G(x0, zs)
        if (plan.branch.size >= 3) {
            // The route line runs down the carriageway's middle: only sideways movement beyond
            // the lanes you take shows, eased in smoothly (no false kink).
            val toLanes = n * LANE_M / 2 - k * LANE_M / 2
            val pts = plan.branch.drop(1).map { (ahead, aside) ->
                val a = aside.coerceAtLeast(0f)
                val beyond = if (toLanes <= 0f) a else a * a / (a + toLanes)
                G(x0 - beyond, zs + ahead.coerceAtLeast(0f))
            }
            return extendLine(listOf(start) + pts, 400f)
        }
        val bend = if (plan.fork) 0.003f else 0.0045f
        return listOf(start) + (1..60).map { i -> val dz = i * 5f; G(x0 - bend * dz * dz, zs + dz) }
    }

    /** A polyline continued in its last direction for [extra] metres. */
    private fun extendLine(line: List<G>, extra: Float): List<G> {
        if (line.size < 2) return line
        val a = line[line.size - 2]; val b = line.last()
        val len = hypot(b.x - a.x, b.z - a.z).takeIf { it > 1e-3f } ?: return line
        val ux = (b.x - a.x) / len; val uz = (b.z - a.z) / len
        return line + (1..8).map { G(b.x + ux * extra * it / 8, b.z + uz * extra * it / 8) }
    }

    /**
     * The road you take at an exit, as (metres sideways from where its lanes start, metres
     * ahead of the split) pairs: negative sideways = out towards the exit's side. For tests and
     * checks of how the picture follows the real geometry.
     */
    fun takenRoad(plan: JunctionPlan): List<Pair<Float, Float>> {
        val n = (plan.lanes ?: 3).coerceAtLeast(2)
        val k = (plan.exitLanes ?: 1).coerceIn(1, n - 1)
        return takenAfterSplit(plan, n, k, 0f, 0f).map { it.x to it.z }
    }

    /** Lane numbers grouped into runs of neighbours: [0, 1, 3] -> 0..1, 3..3. */
    private fun runs(lanes: List<Int>): List<IntRange> {
        val out = ArrayList<IntRange>()
        for (i in lanes.distinct().sorted()) {
            val last = out.lastOrNull()
            if (last != null && last.last == i - 1) out[out.size - 1] = last.first..i else out += i..i
        }
        return out
    }

    /** Drawing on the ground with the camera. */
    private class Draw(val cam: Camera, val out: MutableList<Shape>, val c: Palette, val warp: ApproachWarp? = null, val distance: Float = 0f) {
        val zMin = 2f

        /** [line] moved onto the curved approach (RV01), or unchanged when the road is drawn straight. */
        fun bent(line: List<G>): List<G> = warp?.let { wp -> wp.densify(line).map { wp.apply(it, cam.mirror) } } ?: line

        /** [line] cut where it comes nearer than [zMin] (behind the picture's bottom edge). */
        fun clip(line: List<G>): List<G> {
            val r = ArrayList<G>()
            for (i in line.indices) {
                val g = line[i]
                if (i > 0) {
                    val a = line[i - 1]
                    if ((a.z < zMin) != (g.z < zMin)) {
                        val t = (zMin - a.z) / (g.z - a.z)
                        r += G(a.x + (g.x - a.x) * t, zMin)
                    }
                }
                if (g.z >= zMin) r += g
            }
            return r
        }

        /** [line] moved [off] metres to its right (negative = left), with mitre-limited corners (MG04). */
        fun offset(line: List<G>, off: Float): List<G> = RoadGeometry.offset(line, off)

        /** The ground between [from] and [to] metres right of [centre], filled. */
        fun band(centre: List<G>, from: Float, to: Float, argb: Long) {
            // Sharp corners in the survey are rounded first, so the edges never show wedges or cracks.
            val smooth = RoadGeometry.refine(centre)
            val l = clip(bent(offset(smooth, from)))
            val r = clip(bent(offset(smooth, to)))
            if (l.size < 2 || r.size < 2) return
            out += Shape.Fill((l + r.reversed()).map(cam::p), argb)
        }

        /** A painted line [width] metres wide, [off] metres right of [centre]; dashed 6 m on, 6 m off. */
        fun paint(centre: List<G>, off: Float, width: Float, dashed: Boolean, argb: Long = c.line) {
            val line = offset(centre, off)
            if (!dashed) return band(line, -width / 2, width / 2, argb)
            var run = ArrayList<G>()
            var along = 0f
            val dash = 6f
            fun on(a: Float) = ((a / dash).toInt() % 2) == 0
            for (i in line.indices) {
                if (i > 0) {
                    val a = line[i - 1]
                    val b = line[i]
                    val seg = hypot(b.x - a.x, b.z - a.z)
                    var t = 0f
                    while (t < seg) {
                        val next = minOf(seg, (((along + t) / dash).toInt() + 1) * dash - along)
                        val pa = G(a.x + (b.x - a.x) * t / seg, a.z + (b.z - a.z) * t / seg)
                        val pb = G(a.x + (b.x - a.x) * next / seg, a.z + (b.z - a.z) * next / seg)
                        if (on(along + (t + next) / 2)) {
                            if (run.isEmpty()) run += pa
                            run += pb
                        } else if (run.isNotEmpty()) { band(run, -width / 2, width / 2, argb); run = ArrayList() }
                        t = next
                    }
                    along += seg
                }
            }
            if (run.size >= 2) band(run, -width / 2, width / 2, argb)
        }

        /** MG11: the verified change, if it fits the [n] lanes drawn (otherwise no change is drawn). */
        fun validChange(advice: LaneAdvice, n: Int): LaneAdvice.ChangeLane? =
            (advice as? LaneAdvice.ChangeLane)?.takeIf { it.fromLane in 0 until n && it.toLane in 0 until n }

        /**
         * The recommended corridor on the approach, translucent so the lane lines stay visible
         * (they are painted over it). A continuous lane is a plain stretch of the lane; a verified required
         * change shows the corridor sliding from the lane that ends into the lane to use, finishing
         * where the data says the change must be done.
         */
        fun approachCorridor(advice: LaneAdvice, lit: List<Int>, n: Int, zEnd: Float, zJunction: Float, approach: List<G>) {
            val lw = LANE_M
            val change = validChange(advice, n)
            if (change == null) {
                for (r in runs(lit)) band(approach, -n * lw / 2 + r.first * lw + 0.25f, -n * lw / 2 + (r.last + 1) * lw - 0.25f, c.cyan)
                return
            }
            fun mid(i: Int) = -n * lw / 2 + (i + 0.5f) * lw
            val by = (change.byM ?: LaneGuide.DEFAULT_WINDOW_M).toFloat()
            val frac = if (distance <= 0f) 0f else (by / distance).coerceIn(0f, 1f)
            val zc = zJunction * (1f - frac)
            val span = (zJunction * 0.3f).coerceIn(3f, 30f)
            val xf = mid(change.fromLane); val xt = mid(change.toLane)
            val path = ArrayList<G>()
            var z = cam.zBottom
            while (z < zEnd) { path += G(laneShift(z, zc, span, xf, xt), z); z += 3f }
            path += G(laneShift(zEnd, zc, span, xf, xt), zEnd)
            band(path, -(lw - 0.5f) / 2, (lw - 0.5f) / 2, c.cyan)
        }

        private fun laneShift(z: Float, zc: Float, span: Float, xf: Float, xt: Float): Float {
            val t = ((z - (zc - span)) / span).coerceIn(0f, 1f)
            return xf + (xt - xf) * t * t * (3f - 2f * t)
        }

        /**
         * Roads that pass the junction without joining it, from the shared model (MG08/MG15): ones on a
         * lower level (or the same level, unconnected) drawn first and muted, higher ones (bridges) after
         * the junction with a pale edge. Placed by their real position round the ring or junction point.
         */
        fun crossings(plan: JunctionPlan, model: JunctionModel, zEntry: Float, over: Boolean) {
            val centre = model.centre.value ?: return
            val heading = (model.entryArm?.bearing ?: return) + 180.0
            val ringM = model.ring.value?.radiusM
            val (cz, k) = when {
                plan.kind == JunctionKind.ROUNDABOUT && ringM != null -> (zEntry + RING_R) to (RING_R / ringM).toFloat()
                plan.kind == JunctionKind.TURN -> (zEntry + 1.5f * LANE_M) to 1f
                else -> return
            }
            val routeLevel = model.routeLevel.value ?: 0
            val muted = ScenePresentation.blend(c.road, c.land, .55f)
            for (cr in model.crossingsInDrawOrder()) {
                val level = cr.level.value ?: 0
                if ((level > routeLevel) != over) continue
                fun at(p: LatLon): G { val (x, y) = JunctionModelBuilder.rotated(centre, p, heading); return G(x.toFloat() * k, cz + y.toFloat() * k) }
                val line = listOf(at(cr.a), at(cr.b))
                if (over) {
                    band(line, -ARM_W * 1.15f / 2, ARM_W * 1.15f / 2, c.line)
                    band(line, -ARM_W * .85f / 2, ARM_W * .85f / 2, c.road)
                } else band(line, -ARM_W * (if (level < routeLevel) .8f else .45f) / 2, ARM_W * (if (level < routeLevel) .8f else .45f) / 2, muted)
            }
        }

        fun straight(x: Float, z0: Float, z1: Float, step: Float = 0f): List<G> =
            if (step <= 0f) listOf(G(x, z0), G(x, z1)) else {
                val n = maxOf(1, ((z1 - z0) / step).toInt())
                (0..n).map { G(x, z0 + (z1 - z0) * it / n) }
            }

        /**
         * Motorway exit or split, drawn with the way you take on the left (mirrored for the right).
         * Lanes are numbered from that side: 0 = outermost lane of the road you take.
         */
        fun exit(plan: JunctionPlan, zs: Float) {
            val known = plan.lanes != null
            var n = plan.lanes ?: (if (plan.fork) 2 else 3)
            var k = plan.exitLanes ?: (if (plan.fork) maxOf(1, n / 2) else 1)
            if (n < 2) n = 2
            k = k.coerceIn(1, n - 1)
            val t = n - k
            val lw = LANE_M
            // An exit keeps the carriageway you leave in the middle (as the mock-up); a split is centred.
            val xLeft = if (plan.fork) -n * lw / 2 else -t * lw / 2 - k * lw
            val x0 = xLeft + k * lw / 2 // middle of the lanes you take
            val x1 = xLeft + k * lw + t * lw / 2 // middle of the other lanes
            val z0 = cam.zBottom
            // The road you take after the split: the route's real shape, or a standard slip road.
            val taken = listOf(G(x0, z0)) + takenAfterSplit(plan, n, k, x0, zs)
            val other: List<G> = if (plan.fork) listOf(G(x1, z0)) + (0..60).map { i -> val dz = i * 5f; G(x1 + 0.003f * dz * dz, zs + dz) }
                else straight(x1, z0, FAR)
            band(taken, -k * lw / 2, k * lw / 2, c.road)
            band(other, -t * lw / 2, t * lw / 2, c.road)
            // Only the lane(s) to use, from real lane data.
            val lit = plan.highlighted.filter { it in 0 until n }
            val lanesKnown = plan.source == RibbonSource.ARROWS && lit.isNotEmpty() && known
            if (lanesKnown) {
                // Lanes next to each other are one cyan stretch (lane lines are painted over it).
                for (r in runs(lit.filter { it < k })) band(taken, -k * lw / 2 + r.first * lw + 0.25f, -k * lw / 2 + (r.last + 1) * lw - 0.25f, c.cyan)
                for (r in runs(lit.filter { it >= k }.map { it - k })) band(other, -t * lw / 2 + r.first * lw + 0.25f, -t * lw / 2 + (r.last + 1) * lw - 0.25f, c.cyan)
            }
            if (known) {
                for (i in 1 until k) paint(taken, -k * lw / 2 + i * lw, 0.22f, dashed = true)
                for (j in 1 until t) paint(other, -t * lw / 2 + j * lw, 0.22f, dashed = true)
                paint(straight(xLeft + k * lw, z0, zs, 3f), 0f, 0.22f, dashed = true)
            }
            paint(taken, -k * lw / 2, 0.33f, dashed = false)
            paint(other, t * lw / 2, 0.33f, dashed = false)
            if (!lanesKnown) {
                // No lane data: a thin line from the split on, never across lanes.
                band(taken.drop(1), -THIN_M / 2, THIN_M / 2, c.cyan)
            }
        }

        fun roundabout(plan: JunctionPlan, zEntry: Float, schematicRoundabout: Boolean) {
            val n = (plan.lanes ?: 2).coerceAtLeast(1)
            val lw = LANE_M
            val cz = zEntry + RING_R
            fun ring(theta: Float, r: Float): G {
                val th = Math.toRadians(theta.toDouble())
                return G(r * sin(th).toFloat(), cz + r * cos(th).toFloat())
            }
            fun disc(r: Float, argb: Long) {
                val pts = clip(bent((0..180).map { ring(it * 2f, r) }))
                if (pts.size >= 3) out += Shape.Fill(pts.map(cam::p), argb)
            }
            // RV05: with a trusted shared model (real ring, arms and an exit number that agrees) the arms
            // are drawn at their real mapped bearings; otherwise the schematic below is used unchanged.
            val real = plan.model?.takeIf { it.armsTrusted && it.ring.known }
            val angle: Int? = real?.exitArm?.relative?.let { if (it > 180) it - 360 else it } ?: plan.roundaboutAngle
            val sweep: Float? = if (real != null) JunctionPlan.sweep(angle!!, plan.leftDriving) else plan.roundaboutSweep
            if (real != null) {
                for (arm in real.arms.filter { it.role != ArmRole.ENTRY }) {
                    band(listOf(ring(arm.relative.toFloat(), RING_R), ring(arm.relative.toFloat(), RING_R + 80f)), -ARM_W / 2, ARM_W / 2, c.road)
                }
            } else if (sweep != null && angle != null) {
                // Arms: one for each exit before yours (the exit number is the route's), spaced evenly
                // round the way you go, and your exit at its real angle.
                val exits = if (schematicRoundabout) 1 else plan.roundaboutExit.coerceAtLeast(1)
                for (i in 1..exits) {
                    val th = 180f + sweep * i / exits
                    band(listOf(ring(th, RING_R), ring(th, RING_R + 80f)), -ARM_W / 2, ARM_W / 2, c.road)
                }
            }
            val approach = straight(0f, cam.zBottom, cz - RING_R + 1f)
            band(approach, -n * lw / 2, n * lw / 2, c.road)
            disc(RING_R + RING_W / 2, c.road)
            disc(RING_R - RING_W / 2, c.island)
            val lit = plan.highlighted.filter { it in 0 until n }
            val lanesKnown = plan.source == RibbonSource.ARROWS && lit.isNotEmpty() && plan.lanes != null
            fun laneMid(i: Int) = -n * lw / 2 + (i + 0.5f) * lw
            val way = if (sweep != null && angle != null)
                (0..54).map { s -> ring(180f + sweep * s / 54, RING_R) } + ring(angle.toFloat(), RING_R + 70f)
                else emptyList()
            val advice = if (lanesKnown) LaneGuide.adviseFor(plan) else LaneAdvice.Unknown("no lane data")
            if (lanesKnown && schematicRoundabout) {
                // Approach arrows alone do not establish lane-to-lane continuity round the ring: only verified
                // connectivity (MG11) lets the corridor run on as a lane; otherwise a thin direction line.
                approachCorridor(advice, lit, n, cz - RING_R - 1f, zEntry, approach)
                val stay = advice is LaneAdvice.StayInLane
                if (way.isNotEmpty()) band(way, -(if (stay) CORRIDOR_M else THIN_M) / 2, (if (stay) CORRIDOR_M else THIN_M) / 2, c.cyan)
            } else if (lanesKnown) {
                val single = lit.size == 1
                if (single) {
                    // One lane: a wide line down its middle, round the ring and out (as the mock-up).
                    val x = laneMid(lit[0])
                    band(listOf(G(x, cam.zBottom), G(x, cz - RING_R - 1f)) + way, -WIDE_M / 2, WIDE_M / 2, c.cyan)
                } else {
                    approachCorridor(advice, lit, n, cz - RING_R - 1f, zEntry, approach)
                    if (way.isNotEmpty()) band(way, -WIDE_M / 2, WIDE_M / 2, c.cyan)
                }
            } else if (way.isNotEmpty()) {
                // No lane data: a thin line that starts at the ring.
                band(way, -THIN_M / 2, THIN_M / 2, c.cyan)
            }
            if (plan.lanes != null) for (i in 1 until n) paint(straight(-n * lw / 2 + i * lw, cam.zBottom, cz - RING_R - RING_W / 2, 3f), 0f, 0.22f, dashed = true)
        }

        fun turn(plan: JunctionPlan, depth: Float) {
            val n = (plan.lanes ?: 2).coerceAtLeast(1)
            val lw = LANE_M
            val stop = depth
            val cz = depth + 1.5f * lw // middle of the road you turn into
            val a = plan.turnAngle?.toFloat() ?: when {
                plan.straight -> 0f
                plan.exitLeft -> -90f
                else -> 90f
            }
            val rad = Math.toRadians(a.toDouble())
            val ux = sin(rad).toFloat(); val uz = cos(rad).toFloat()
            val centre = G(0f, cz)
            val crossW = 3f * lw
            // The road ahead carries on; the road you turn into goes off at its real angle with a
            // short stub on the other side (as the mock-up).
            band(straight(0f, cam.zBottom, 300f), -n * lw / 2, n * lw / 2, c.road)
            if (a != 0f) band(listOf(G(-ux * 12f, cz - uz * 12f), centre, G(ux * 140f, cz + uz * 140f)), -crossW / 2, crossW / 2, c.road)
            val approach = straight(0f, cam.zBottom, stop)
            val lit = plan.highlighted.filter { it in 0 until n }
            val lanesKnown = plan.source == RibbonSource.ARROWS && lit.isNotEmpty() && plan.lanes != null
            if (lanesKnown) approachCorridor(LaneGuide.adviseFor(plan), lit, n, stop, stop, approach)
            if (plan.lanes != null) for (i in 1 until n) paint(straight(-n * lw / 2 + i * lw, cam.zBottom, stop, 3f), 0f, 0.22f, dashed = true)
            paint(approach, -n * lw / 2, 0.33f, dashed = false)
            paint(approach, n * lw / 2, 0.33f, dashed = false)
            // The way you go: from your lanes (or the junction, with no lane data) into the
            // nearside lane of the new road (on the left in the UK).
            val startX = if (lanesKnown) (-n * lw / 2 + (lit.min() + lit.max() + 1) * lw / 2) else 0f
            val side = if (plan.leftDriving) -1f else 1f
            val laneOff = side * lw * 0.45f
            val entry = G(ux * 14f + laneOff * uz, cz + uz * 14f - laneOff * ux)
            val ctrl = G(startX, cz + (if (a == 0f) 0f else -laneOff * ux))
            val curve = (0..12).map { i ->
                val t = i / 12f; val m = 1 - t
                G(m * m * startX + 2 * m * t * ctrl.x + t * t * entry.x, m * m * stop + 2 * m * t * ctrl.z + t * t * entry.z)
            }
            val way = if (a == 0f) listOf(G(startX, stop), G(startX, stop + 60f)) else curve + G(entry.x + ux * 126f, entry.z + uz * 126f)
            if (lanesKnown) {
                val width = (lit.size * lw - 0.5f).coerceAtMost(lw * 0.95f)
                band(way, -width / 2, width / 2, c.cyan)
            } else band(way, -THIN_M / 2, THIN_M / 2, c.cyan)
        }
    }
}
