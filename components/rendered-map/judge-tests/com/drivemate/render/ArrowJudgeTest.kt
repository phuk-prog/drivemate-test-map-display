package com.drivemate.render

import com.drivemate.render.RenderJudge.Geo
import com.drivemate.render.RenderJudge.Picture
import com.drivemate.render.RenderJudge.Px
import com.drivemate.render.RenderJudge.Sample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * Arrow judge self-tests. A synthetic road runs at [roadDeg] across a 600 x 600 px picture at
 * 4 px per metre, sampled every 5 m; the arrow is the chevron outline of a typical navigation
 * arrow (points 24,3 / 38.5,40 / 24,32 / 9.5,40 in a 48-unit box), filled, rotated and placed.
 */
class ArrowJudgeTest {
    private val pxPerM = 4.0
    private val centre = Px(300f, 300f)

    private fun own(roadDeg: Double): List<Sample> {
        val t = Math.toRadians(roadDeg)
        val ux = sin(t); val uy = -cos(t)
        return (-12..12).map { i ->
            val m = i * 5.0
            Sample(Geo(0.0, 0.0), m + 60.0, Px((centre.x + ux * m * pxPerM).toFloat(), (centre.y + uy * m * pxPerM).toFloat()))
        }
    }

    private val outline = listOf(24.0 to 3.0, 38.5 to 40.0, 24.0 to 32.0, 9.5 to 40.0)

    private fun inside(x: Double, y: Double, poly: List<Pair<Double, Double>>): Boolean {
        var c = false
        for (i in poly.indices) {
            val (xi, yi) = poly[i]; val (xj, yj) = poly[(i + poly.size - 1) % poly.size]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) c = !c
        }
        return c
    }

    /** Pixels of an arrow [widthM] wide pointing [headingDeg], shifted [acrossM] to its right. */
    private fun arrow(widthM: Double, headingDeg: Double, acrossM: Double = 0.0, alongM: Double = 0.0): List<Px> {
        val unit = widthM * pxPerM / 29.0                       // outline is 29 units wide
        val t = Math.toRadians(headingDeg)
        val ux = sin(t); val uy = -cos(t)                       // forward on screen
        val rx = cos(t); val ry = sin(t)                        // right on screen
        val ox = centre.x + rx * acrossM * pxPerM + ux * alongM * pxPerM
        val oy = centre.y + ry * acrossM * pxPerM + uy * alongM * pxPerM
        val out = ArrayList<Px>()
        for (y in 0 until 600) for (x in 0 until 600) {
            val dx = x + 0.5 - ox; val dy = y + 0.5 - oy
            // Back into the outline's frame: u = right, v = down the outline (tip at small v).
            val u = (dx * rx + dy * ry) / unit + 24.0
            val v = -(dx * ux + dy * uy) / unit + 24.0
            if (inside(u, v, outline)) out += Px(x.toFloat(), y.toFloat())
        }
        return out
    }

    @Test fun arrowOnCentrelineAlongTheRoadPasses() {
        val v = RenderJudge.arrow(arrow(8.0, 0.0), own(0.0), lanes = 3)
        assertTrue(v.reasons.toString(), v.pass)
        assertEquals(8.0, v.widthM, 0.5)
        assertEquals(0.0, v.offsetM, 0.3)
    }

    @Test fun rotatedRoadAndArrowPassWhenTheyAgree() {
        for (deg in listOf(26.0, 135.0, 250.0)) {
            val v = RenderJudge.arrow(arrow(6.0, deg), own(deg), lanes = 3)
            assertTrue("$deg: ${v.reasons}", v.pass)
            assertTrue("$deg: heading ${v.headingDeg}", RenderJudge.angleBetween(v.headingDeg, deg) < 3.0)
        }
    }

    @Test fun arrowPointingAgainstTrafficFails() {
        val v = RenderJudge.arrow(arrow(8.0, 206.0), own(26.0), lanes = 3)
        assertTrue(v.reasons.toString(), v.reasons.any { it.contains("away from the road") })
    }

    @Test fun arrowTurnedThirtyDegreesFails() {
        assertTrue(RenderJudge.arrow(arrow(8.0, 56.0), own(26.0), lanes = 3).reasons.any { it.contains("away from the road") })
    }

    @Test fun arrowBesideTheCentrelineFails() {
        val v = RenderJudge.arrow(arrow(8.0, 0.0, acrossM = 2.0), own(0.0), lanes = 3)
        assertEquals(2.0, v.offsetM, 0.3)
        assertTrue(v.reasons.any { it.contains("off the carriageway centreline") })
    }

    @Test fun arrowAheadOnTheCentrelineIsStillCentred() {
        assertTrue(RenderJudge.arrow(arrow(8.0, 0.0, alongM = 6.0), own(0.0), lanes = 3).pass)
    }

    @Test fun arrowWiderThanItsCarriagewayFails() {
        // The original arrow at zoom 16: about 15 m on a 3-lane, 10.95 m carriageway.
        assertTrue(RenderJudge.arrow(arrow(15.4, 0.0), own(0.0), lanes = 3).reasons.any { it.contains("wider than its 3-lane") })
        assertTrue(RenderJudge.arrow(arrow(8.0, 0.0), own(0.0), lanes = 2).reasons.any { it.contains("wider than its 2-lane") })
    }

    @Test fun arrowNarrowerThanALaneFails() {
        assertTrue(RenderJudge.arrow(arrow(2.5, 0.0), own(0.0), lanes = 3).reasons.any { it.contains("narrower than one lane") })
    }

    @Test fun missingArrowFails() {
        assertTrue(RenderJudge.arrow(arrow(8.0, 0.0).take(10), own(0.0), lanes = 3).reasons.single().startsWith("no arrow found"))
    }

    @Test fun lopsidedShapeIsNotTakenForAnArrow() {
        val l = ArrayList<Px>()
        for (y in 260 until 340) for (x in 280 until 300) l += Px(x.toFloat(), y.toFloat())
        for (y in 320 until 340) for (x in 300 until 325) l += Px(x.toFloat(), y.toFloat())
        assertTrue(RenderJudge.arrow(l, own(0.0), lanes = 3).reasons.any { it.contains("no clear arrow shape") })
    }

    @Test fun addedPixelsAreExactlyWhatChanged() {
        val before = IntArray(100) { 0xFF000000.toInt() }
        val after = before.copyOf().also { it[5] = 0xFFFFFFFF.toInt(); it[42] = 0xFF00FF00.toInt(); it[7] = 0xFF030303.toInt() }
        val px = RenderJudge.added(Picture(10, 10, 1.0) { x, y -> before[y * 10 + x] }, Picture(10, 10, 1.0) { x, y -> after[y * 10 + x] })
        assertEquals(listOf(Px(5f, 0f), Px(2f, 4f)), px)
        assertFalse(px.contains(Px(7f, 0f)))   // within tolerance: GPU dithering, not a new mark
    }
}
