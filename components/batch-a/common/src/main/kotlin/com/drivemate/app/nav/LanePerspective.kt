package com.drivemate.app.nav

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot

/**
 * RV01: bends the straight-road scene along the route's own approach line. The scene is drawn
 * as if the road ahead were straight (x metres right, z metres ahead); [apply] moves every point
 * of it to where it lies when the road ahead follows the real centre line instead: z becomes the
 * distance along the curve and x the sideways offset from it. Lane edges, dashes and the corridor
 * are all drawn by offsetting from the road's middle, so they follow the curve smoothly together.
 *
 * It is used only when the junction model has the route's approach geometry with enough
 * confidence and the road really bends; otherwise [of] returns null and the old straight
 * schematic is drawn. The picture is scaled uniformly so the real approach fits the
 * scene's compressed distance, and a bend too tight for the road's width is not drawn.
 */
internal class ApproachWarp private constructor(
    private val c: List<Pt>,
    private val t: List<Pt>,
    private val step: Float,
    val depth: Float,
    /** Heading change over the approach, degrees (+ = bends right). */
    val bendDeg: Double,
) {
    /** [g] is a point of the straight scene. [mirror]: the scene is laid out for the left and flipped by the camera. */
    fun apply(g: Pt, mirror: Boolean): Pt {
        val lat = if (mirror) -g.x else g.x
        val rp: Pt = when {
            g.z <= 0f -> Pt(lat, g.z)
            g.z >= depth -> {
                val e = c.last(); val d = t.last()
                val n = Pt(d.z, -d.x)
                val over = g.z - depth
                Pt(e.x + d.x * over + n.x * lat, e.z + d.z * over + n.z * lat)
            }
            else -> {
                val f = g.z / step
                val i = f.toInt().coerceIn(0, c.size - 2)
                val u = f - i
                val px = c[i].x + (c[i + 1].x - c[i].x) * u; val pz = c[i].z + (c[i + 1].z - c[i].z) * u
                var dx = t[i].x + (t[i + 1].x - t[i].x) * u; var dz = t[i].z + (t[i + 1].z - t[i].z) * u
                val l = hypot(dx, dz).takeIf { it > 1e-4f } ?: 1f
                dx /= l; dz /= l
                Pt(px + dz * lat, pz - dx * lat)
            }
        }
        return Pt(if (mirror) -rp.x else rp.x, rp.z)
    }

    /** [line] with long pieces cut so the warp can curve them (a 2-point straight becomes a smooth curve). */
    fun densify(line: List<Pt>): List<Pt> {
        if (line.size < 2) return line
        val out = ArrayList<Pt>()
        out += line.first()
        for (i in 1 until line.size) {
            val a = line[i - 1]; val b = line[i]
            val n = (hypot(b.x - a.x, b.z - a.z) / step).toInt().coerceIn(1, 300)
            for (k in 1..n) out += Pt(a.x + (b.x - a.x) * k / n, a.z + (b.z - a.z) * k / n)
        }
        return out
    }

    companion object {
        const val MIN_CONFIDENCE = 0.6
        /** Roads that bend less than this over the approach, and wander less than [MIN_DEVIATION_M] from the chord, stay straight. */
        const val MIN_BEND_DEG = 3.0
        const val MIN_DEVIATION_M = 2.0
        const val MIN_LENGTH_M = 20.0
        private const val SAMPLES = 48

        /**
         * The warp for the last [distanceM] metres of the model's approach line, drawn [depth] scene
         * metres long, or null when the straight schematic should be used (no or weak geometry, a
         * straight road, or a bend too tight for [halfRoadM] either side of the centre line).
         */
        fun of(model: JunctionModel?, distanceM: Float, depth: Float, halfRoadM: Float): ApproachWarp? {
            val geo = model?.approach ?: return null
            val pts = geo.value ?: return null
            if (geo.confidence < MIN_CONFIDENCE || pts.size < 3 || distanceM < MIN_LENGTH_M || depth < 5f) return null
            val origin = pts.last()
            // Metres east/north of the junction; clip to the last distanceM metres of the line.
            val enu = pts.map { JunctionModelBuilder.local(origin, it) }
            val seg = (1 until enu.size).map { hypot(enu[it].first - enu[it - 1].first, enu[it].second - enu[it - 1].second) }
            val total = seg.sum()
            if (total < MIN_LENGTH_M) return null
            val use = minOf(distanceM.toDouble(), total)
            val keep = ArrayList<Pair<Double, Double>>()
            var skip = total - use
            for (i in seg.indices) {
                if (skip >= seg[i]) { skip -= seg[i]; continue }
                if (keep.isEmpty()) {
                    val f = if (seg[i] > 0) skip / seg[i] else 0.0
                    keep += (enu[i].first + (enu[i + 1].first - enu[i].first) * f) to (enu[i].second + (enu[i + 1].second - enu[i].second) * f)
                }
                keep += enu[i + 1]
                skip = 0.0
            }
            if (keep.size < 2) return null
            // Heading at the car: the first ~15 m of the line.
            var acc = 0.0
            var probe = keep.last()
            for (i in 1 until keep.size) {
                acc += hypot(keep[i].first - keep[i - 1].first, keep[i].second - keep[i - 1].second)
                if (acc >= 15.0) { probe = keep[i]; break }
            }
            val h0 = Math.toDegrees(Math.atan2(probe.first - keep[0].first, probe.second - keep[0].second))
            val hr = Math.toRadians(h0)
            val real = keep.map {
                val e = it.first - keep[0].first; val n = it.second - keep[0].second
                Pt((e * Math.cos(hr) - n * Math.sin(hr)).toFloat(), (e * Math.sin(hr) + n * Math.cos(hr)).toFloat())
            }
            val smooth = RoadGeometry.refine(real)
            val len = (1 until smooth.size).sumOf { hypot(smooth[it].x - smooth[it - 1].x, smooth[it].z - smooth[it - 1].z).toDouble() }
            if (len < MIN_LENGTH_M) return null
            // Is it really curved?
            val chordEnd = smooth.last()
            val chordLen = hypot(chordEnd.x, chordEnd.z).takeIf { it > 1e-3f } ?: return null
            val deviation = smooth.maxOf { abs(it.x * chordEnd.z - it.z * chordEnd.x) / chordLen }
            // Resample at an even spacing along the curve, scaled to fit [depth].
            val k = depth / len
            val step = depth / SAMPLES
            val scaled = smooth.map { Pt((it.x * k).toFloat(), (it.z * k).toFloat()) }
            val cum = DoubleArray(scaled.size)
            for (i in 1 until scaled.size) cum[i] = cum[i - 1] + hypot(scaled[i].x - scaled[i - 1].x, scaled[i].z - scaled[i - 1].z)
            val c = ArrayList<Pt>(SAMPLES + 1)
            var j = 0
            for (n in 0..SAMPLES) {
                val s = (cum.last() * n / SAMPLES)
                while (j < scaled.size - 2 && cum[j + 1] < s) j++
                val span = (cum[j + 1] - cum[j]).takeIf { it > 1e-6 } ?: 1.0
                val u = ((s - cum[j]) / span).coerceIn(0.0, 1.0).toFloat()
                c += Pt(scaled[j].x + (scaled[j + 1].x - scaled[j].x) * u, scaled[j].z + (scaled[j + 1].z - scaled[j].z) * u)
            }
            // Smooth tangents (central differences over two steps), ends one-sided.
            val t = c.indices.map { i ->
                val a = c[maxOf(0, i - 2)]; val b = c[minOf(c.lastIndex, i + 2)]
                val l = hypot(b.x - a.x, b.z - a.z).takeIf { it > 1e-4f } ?: 1f
                Pt((b.x - a.x) / l, (b.z - a.z) / l)
            }
            val bend = signedBend(t.first(), t.last())
            if (abs(bend) < MIN_BEND_DEG && deviation < MIN_DEVIATION_M) return null
            // Too tight a bend for the road's width would fold the edges over each other.
            val sdx = cum.last() / SAMPLES
            for (i in 1 until t.size) {
                val turn = Math.toRadians(abs(signedBend(t[i - 1], t[i])))
                if (turn > 1e-6 && sdx / turn < 2.0 * halfRoadM) return null
            }
            // Pin the car at the origin looking straight ahead (the scene's own camera).
            val t0 = t.first()
            fun rot(p: Pt) = Pt(p.x * t0.z - p.z * t0.x, p.x * t0.x + p.z * t0.z)
            val c2 = c.map { rot(Pt(it.x - c[0].x, it.z - c[0].z)) }
            val t2 = t.map { rot(it) }
            return ApproachWarp(c2, t2, (cum.last() / SAMPLES).toFloat(), cum.last().toFloat(), bend)
        }

        private fun signedBend(a: Pt, b: Pt): Double {
            val dot = (a.x * b.x + a.z * b.z).toDouble().coerceIn(-1.0, 1.0)
            val deg = Math.toDegrees(acos(dot))
            val cross = a.x * b.z - a.z * b.x // < 0: turning to the right in (x right, z ahead)
            return if (cross < 0) deg else -deg
        }
    }
}
