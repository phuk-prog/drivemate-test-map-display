package com.drivemate.render

import com.drivemate.render.RenderJudge.Band
import com.drivemate.render.RenderJudge.Expect
import com.drivemate.render.RenderJudge.Geo
import com.drivemate.render.RenderJudge.Picture
import com.drivemate.render.RenderJudge.Px
import com.drivemate.render.RenderJudge.Sample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * Proves the judge can fail. Synthetic pictures only: two vertical carriageways 60 px apart on a
 * 400 x 800 px canvas at 2 px/dp. Each broken picture must be rejected for the stated reason.
 */
class RenderJudgeTest {
    private val bg = 0xFF182432.toInt()
    private val ref = 0xFF8C98A6.toInt()
    private val cyan = 0xFF00E5EE.toInt()
    private val navy = 0xFF0B3D56.toInt()
    private val amber = 0xFFF9AB00.toInt()
    private val red = 0xFFD93025.toInt()
    private val ownX = 170f
    private val oppX = 230f

    private class Canvas(val w: Int = 400, val h: Int = 800, fill: Int) {
        val px = IntArray(w * h) { fill }
        fun line(x: Float, y0: Int, y1: Int, widthPx: Float, color: Int) {
            for (y in y0 until y1) for (xi in 0 until w)
                if (hypot((xi + 0.5f - x).toDouble(), 0.0) <= widthPx / 2) px[y * w + xi] = color
        }
        fun picture() = Picture(w, h, 2.0) { x, y -> px[y * w + x] }
    }

    private fun samples(x: Float) = (0 until 80).map { i ->
        val y = 5f + i * 10f
        Sample(Geo(0.0, 0.0), y.toDouble(), Px(x, y))
    }

    /** A plausible picture: grey reference roads, then a route (navy edge, cyan core) on the own one. */
    private fun scene(routeX: Float = ownX, routeWidthPx: Float = 28f, core: (Int) -> Int = { cyan }): Canvas {
        val c = Canvas(fill = bg)
        c.line(ownX, 0, 800, 16f, ref)
        c.line(oppX, 0, 800, 16f, ref)
        c.line(routeX, 0, 800, routeWidthPx, navy)
        for (y in 0 until 800) c.line(routeX, y, y + 1, routeWidthPx - 8f, core(y))
        return c
    }

    private fun judge(c: Canvas) = RenderJudge.carriageway(c.picture(), samples(ownX), samples(oppX), bg, ref)

    @Test fun routeOnOwnCarriagewayLeavingOppositeVisiblePasses() {
        val v = judge(scene())
        assertTrue(v.reasons.toString(), v.pass)
        assertEquals(14.0, v.medianWidthDp, 0.6)
    }

    @Test fun routeDrawnOnTheOppositeCarriagewayFails() {
        val v = judge(scene(routeX = oppX))
        assertFalse(v.pass)
        assertTrue(v.reasons.any { it.startsWith("route covers") })
        assertTrue(v.reasons.any { it.startsWith("opposite carriageway visible") })
    }

    @Test fun routeBetweenTheCarriagewaysFails() {
        assertTrue(judge(scene(routeX = 200f, routeWidthPx = 20f)).reasons.any { it.startsWith("route covers") })
    }

    @Test fun routeSoWideItCoversTheOppositeCarriagewayFails() {
        val v = judge(scene(routeWidthPx = 150f))
        assertTrue(v.reasons.any { it.startsWith("opposite carriageway visible") })
    }

    @Test fun hairlineRouteCannotPassAsLeavingRoadVisible() {
        assertTrue(judge(scene(routeWidthPx = 8f)).reasons.any { it.contains("not legible") })
    }

    @Test fun carriagewaysTooCloseToTestAreNotAVacuousPass() {
        val c = Canvas(fill = bg).apply { line(ownX, 0, 800, 16f, ref); line(176f, 0, 800, 16f, ref); line(ownX, 0, 800, 20f, cyan) }
        val v = RenderJudge.carriageway(c.picture(), samples(ownX), samples(176f), bg, ref)
        assertTrue(v.reasons.any { it.contains("testable") && it.startsWith("only") })
    }

    @Test fun offScreenRouteHasTooFewSamples() {
        val far = samples(ownX).map { it.copy(px = Px(it.px.x, it.px.y + 2000f)) }
        val v = RenderJudge.carriageway(scene().picture(), far, samples(oppX), bg, ref)
        assertTrue(v.reasons.any { it.startsWith("only 0 own") })
    }

    @Test fun colourNamesFollowTheChecklistNotTheAppPalette() {
        assertTrue(RenderJudge.isAmber(amber)); assertFalse(RenderJudge.isRed(amber))
        assertTrue(RenderJudge.isRed(red)); assertFalse(RenderJudge.isAmber(red))
        for (neither in listOf(cyan, navy, ref, bg, 0xFFFFFFFF.toInt(), 0xFFFF6D00.toInt() /* orange */,
                0xFFFFD600.toInt() /* yellow */, 0xFF7A5A10.toInt() /* dull brown */, 0xFFE91E63.toInt() /* pink */))
            assertFalse("%08X".format(neither), RenderJudge.isAmber(neither) || RenderJudge.isRed(neither))
    }

    private val bands = listOf(Band(0.0, 250.0, Expect.NEITHER), Band(250.0, 500.0, Expect.AMBER),
        Band(500.0, 800.0, Expect.RED))

    @Test fun amberThenRedBandsPass() {
        val c = scene { y -> if (y < 250) cyan else if (y < 500) amber else red }
        val v = RenderJudge.traffic(c.picture(), samples(ownX), bands)
        assertTrue(v.reasons.toString(), v.pass)
    }

    @Test fun swappedTrafficColoursFail() {
        val c = scene { y -> if (y < 250) cyan else if (y < 500) red else amber }
        val v = RenderJudge.traffic(c.picture(), samples(ownX), bands)
        assertEquals(2, v.reasons.size)
    }

    @Test fun missingTrafficColourFails() {
        assertFalse(RenderJudge.traffic(scene().picture(), samples(ownX), bands).pass)
    }

    @Test fun trafficSpillingOntoFreeRoadFails() {
        val c = scene { y -> if (y < 500) amber else red }
        val v = RenderJudge.traffic(c.picture(), samples(ownX), bands)
        assertEquals(listOf("NEITHER"), v.reasons.map { it.substringBefore(' ') })
    }

    @Test fun densifyAndSliceFollowGroundDistance() {
        // 0.001 degrees of latitude is about 111 m.
        val line = listOf(Geo(53.420, -2.26), Geo(53.421, -2.26))
        val pts = RenderJudge.densify(line, 10.0)
        assertEquals(12, pts.size)
        assertEquals(111.2, RenderJudge.metres(line[0], line[1]), 0.3)
        assertTrue(RenderJudge.slice(line, 20.0, 40.0).all { RenderJudge.metres(line[0], it) in 19.9..40.1 })
    }
}
