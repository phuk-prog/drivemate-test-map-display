package com.drivemate.app.nav

import kotlin.math.acos
import kotlin.math.hypot

/** A point on the ground: [x] metres to the right, [z] metres ahead. */
internal data class Pt(val x: Float, val z: Float)

/**
 * Curve and join geometry for the junction picture (MG04). Smoothing only reshapes ONE
 * surveyed polyline between its own ends; it never links two separate roads, and the first
 * and last points stay exactly where the data put them.
 */
internal object RoadGeometry {
    /** Corners sharper than this (degrees) are rounded; gentler ones are already smooth enough. */
    const val CORNER_DEG = 6.0
    /** The widest an offset corner may stick out, as a multiple of the offset (stops spikes at sharp bends). */
    const val MITER_LIMIT = 2.2f
    private const val MAX_POINTS = 400

    private fun turnDeg(a: Pt, b: Pt, c: Pt): Double {
        val ux = b.x - a.x; val uz = b.z - a.z; val vx = c.x - b.x; val vz = c.z - b.z
        val lu = hypot(ux, uz); val lv = hypot(vx, vz)
        if (lu < 1e-4f || lv < 1e-4f) return 0.0
        val cos = ((ux * vx + uz * vz) / (lu * lv)).toDouble().coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cos))
    }

    /** The sharpest turn along [line], in degrees. */
    fun maxTurn(line: List<Pt>): Double =
        (1 until line.size - 1).maxOfOrNull { turnDeg(line[it - 1], line[it], line[it + 1]) } ?: 0.0

    /**
     * Rounds corners sharper than [CORNER_DEG] by corner-cutting (Chaikin), a few passes, never
     * moving the two ends. Straight and gently curved lines come back unchanged.
     */
    fun refine(line: List<Pt>, passes: Int = 3): List<Pt> {
        var cur = line
        repeat(passes) {
            if (cur.size < 3 || cur.size * 2 > MAX_POINTS || maxTurn(cur) <= CORNER_DEG) return cur
            val next = ArrayList<Pt>(cur.size * 2)
            next += cur.first()
            for (i in 0 until cur.size - 1) {
                val a = cur[i]; val b = cur[i + 1]
                if (i > 0) next += Pt(a.x * .75f + b.x * .25f, a.z * .75f + b.z * .25f)
                if (i < cur.size - 2) next += Pt(a.x * .25f + b.x * .75f, a.z * .25f + b.z * .75f)
            }
            next += cur.last()
            cur = next
        }
        return cur
    }

    /**
     * [line] moved [off] metres to its right (negative = left). Corners use the bisector, scaled
     * to keep the edge [off] away from both neighbouring segments but never beyond
     * [MITER_LIMIT] x [off], so sharp bends do not throw wedges or spikes.
     */
    fun offset(line: List<Pt>, off: Float): List<Pt> {
        if (off == 0f || line.size < 2) return line
        fun unit(a: Pt, b: Pt): Pair<Float, Float> {
            val len = hypot(b.x - a.x, b.z - a.z)
            return if (len < 1e-4f) 0f to 1f else (b.x - a.x) / len to (b.z - a.z) / len
        }
        return line.indices.map { i ->
            val inc = if (i == 0) unit(line[0], line[1]) else unit(line[i - 1], line[i])
            val out = if (i == line.lastIndex) inc else unit(line[i], line[i + 1])
            // The right-hand normal of a segment going (dx, dz) is (dz, -dx).
            var nx = inc.second + out.second
            var nz = -(inc.first + out.first)
            val nl = hypot(nx, nz)
            if (nl < 1e-4f) { nx = inc.second; nz = -inc.first } else { nx /= nl; nz /= nl }
            val cos = (nx * inc.second + nz * -inc.first).coerceAtLeast(1f / MITER_LIMIT)
            Pt(line[i].x + nx * off / cos, line[i].z + nz * off / cos)
        }
    }

    /** True when no two non-neighbouring edges of the closed [poly] cross (no bow-ties or wedges). */
    fun isSimple(poly: List<Pt>): Boolean {
        val n = poly.size
        fun cross(o: Pt, a: Pt, b: Pt) = (a.x - o.x) * (b.z - o.z) - (a.z - o.z) * (b.x - o.x)
        fun hit(p1: Pt, p2: Pt, p3: Pt, p4: Pt): Boolean {
            val d1 = cross(p3, p4, p1); val d2 = cross(p3, p4, p2)
            val d3 = cross(p1, p2, p3); val d4 = cross(p1, p2, p4)
            return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
        }
        for (i in 0 until n) for (j in i + 2 until n) {
            if (i == 0 && j == n - 1) continue
            if (hit(poly[i], poly[(i + 1) % n], poly[j], poly[(j + 1) % n])) return false
        }
        return true
    }
}
