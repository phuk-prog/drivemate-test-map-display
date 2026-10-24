package com.drivemate.render

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pass/fail rules for a rendered route, written independently of DriveMate.
 *
 * It never reads the app's palette, widths or geometry helpers: it only sees pixels, the screen
 * positions of independently sourced OSM centrelines, and the colour names in the owner's
 * checklist ("amber", "red"). Pure Kotlin so the same rules run on the emulator and in plain
 * JVM tests with deliberately broken pictures (negative controls).
 */
object RenderJudge {
    data class Geo(val lat: Double, val lon: Double)
    data class Px(val x: Float, val y: Float)
    /** A point along a centreline: where it is on the ground, how far along, and where on screen. */
    data class Sample(val geo: Geo, val station: Double, val px: Px)

    /** Minimum legible route line, in dp. A thinner line would "leave the road visible" trivially. */
    const val MIN_ROUTE_WIDTH_DP = 6.0
    /** Clearance beyond that half-width, in dp, before the opposite carriageway "can" stay visible. */
    const val CLEARANCE_DP = 2.0
    /** Per-channel tolerance for "this pixel is the reference road / background", absorbing GPU dithering. */
    const val TOLERANCE = 6
    const val MIN_SHARE = 0.95

    // ---- colour -------------------------------------------------------------------------------

    fun r(c: Int) = (c shr 16) and 0xFF
    fun g(c: Int) = (c shr 8) and 0xFF
    fun b(c: Int) = c and 0xFF

    fun close(a: Int, b: Int, tol: Int = TOLERANCE) =
        abs(r(a) - r(b)) <= tol && abs(g(a) - g(b)) <= tol && abs(b(a) - b(b)) <= tol

    /** Hue in degrees [0, 360), saturation and value in [0, 1]. */
    fun hsv(c: Int): Triple<Double, Double, Double> {
        val rf = r(c) / 255.0; val gf = g(c) / 255.0; val bf = b(c) / 255.0
        val mx = max(rf, max(gf, bf)); val mn = min(rf, min(gf, bf)); val d = mx - mn
        val h = when {
            d == 0.0 -> 0.0
            mx == rf -> 60.0 * (((gf - bf) / d) % 6.0)
            mx == gf -> 60.0 * ((bf - rf) / d + 2.0)
            else -> 60.0 * ((rf - gf) / d + 4.0)
        }
        return Triple((h + 360.0) % 360.0, if (mx == 0.0) 0.0 else d / mx, mx)
    }

    /**
     * "Amber": a strong colour with hue 30–50 degrees, between orange (about 25) and yellow (about 55).
     * An engineering definition for this test, not a legal colour standard.
     */
    fun isAmber(c: Int): Boolean { val (h, s, v) = hsv(c); return h in 30.0..50.0 && s >= 0.6 && v >= 0.6 }

    /** "Red": a strong colour with hue within 15 degrees of pure red. */
    fun isRed(c: Int): Boolean { val (h, s, v) = hsv(c); return (h >= 345.0 || h <= 15.0) && s >= 0.6 && v >= 0.45 }

    // ---- geometry (independent of the app's Geo) ----------------------------------------------

    fun metres(a: Geo, b: Geo): Double {
        val p1 = Math.toRadians(a.lat); val p2 = Math.toRadians(b.lat)
        val dp = p2 - p1; val dl = Math.toRadians(b.lon - a.lon)
        val h = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * 6_371_008.8 * asin(sqrt(h))
    }

    /** Points every [stepM] metres along [line], with their distance from its start. */
    fun densify(line: List<Geo>, stepM: Double): List<Pair<Geo, Double>> {
        val out = ArrayList<Pair<Geo, Double>>()
        var station = 0.0
        var next = 0.0
        for ((a, b) in line.zipWithNext()) {
            val len = metres(a, b)
            while (next <= station + len && len > 0) {
                val t = (next - station) / len
                out += Geo(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t) to next
                next += stepM
            }
            station += len
        }
        return out
    }

    /** Points of [line] between [from] and [to] metres along it, inclusive, as a drawable line. */
    fun slice(line: List<Geo>, from: Double, to: Double): List<Geo> =
        densify(line, 2.0).filter { it.second in from..to }.map { it.first }

    fun distanceToPolyline(p: Px, line: List<Px>): Double = line.zipWithNext().minOf { (a, b) ->
        val dx = (b.x - a.x).toDouble(); val dy = (b.y - a.y).toDouble()
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (((p.x - a.x) * dx + (p.y - a.y) * dy) / len2).coerceIn(0.0, 1.0)
        hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
    }

    // ---- rendered pictures --------------------------------------------------------------------

    /** A rendered picture: [pixel] returns ARGB, [width]/[height] in px, [density] px per dp. */
    class Picture(val width: Int, val height: Int, val density: Double, val pixel: (Int, Int) -> Int) {
        fun inside(p: Px, marginPx: Int) =
            p.x >= marginPx && p.y >= marginPx && p.x < width - marginPx && p.y < height - marginPx
        fun at(p: Px) = pixel(p.x.toInt(), p.y.toInt())
    }

    /**
     * Route ink width across the line at [p], in px: walk both ways along the screen normal of the
     * local centreline direction until the picture shows the reference road or the background again.
     */
    fun inkWidth(pic: Picture, p: Px, dir: Px, background: Int, reference: Int, maxPx: Int = 300): Int {
        val len = hypot(dir.x.toDouble(), dir.y.toDouble()).takeIf { it > 0 } ?: return 0
        val nx = -dir.y / len; val ny = dir.x / len
        fun ink(x: Double, y: Double): Boolean {
            val xi = x.toInt(); val yi = y.toInt()
            if (xi !in 0 until pic.width || yi !in 0 until pic.height) return false
            val c = pic.pixel(xi, yi)
            return !close(c, background) && !close(c, reference)
        }
        if (!ink(p.x.toDouble(), p.y.toDouble())) return 0
        var w = 1
        for (sign in listOf(1, -1)) {
            var k = 1
            while (k < maxPx && ink(p.x + sign * nx * k, p.y + sign * ny * k)) k++
            w += k - 1
        }
        return w
    }

    data class CarriagewayVerdict(
        val ownInView: Int, val ownInked: Int, val medianWidthDp: Double,
        val oppositeInView: Int, val oppositeQualified: Int, val oppositeVisible: Int,
        val reasons: List<String>,
    ) { val pass get() = reasons.isEmpty() }

    /**
     * 02.01. The route must cover the driver's own carriageway centreline, be legibly wide, and leave
     * the opposite carriageway's centreline showing as road wherever the two are far enough apart on
     * screen for a legible line to fit between them.
     */
    fun carriageway(
        pic: Picture, own: List<Sample>, opposite: List<Sample>, background: Int, reference: Int,
        marginPx: Int = 24, minSamples: Int = 20,
    ): CarriagewayVerdict {
        val reasons = ArrayList<String>()
        val ownView = own.filter { pic.inside(it.px, marginPx) }
        val inked = ownView.count { val c = pic.at(it.px); !close(c, background) && !close(c, reference) }
        val widths = ownView.zipWithNext().map { (a, b) ->
            inkWidth(pic, a.px, Px(b.px.x - a.px.x, b.px.y - a.px.y), background, reference) / pic.density
        }.sorted()
        val median = if (widths.isEmpty()) 0.0 else widths[widths.size / 2]
        val ownLine = own.map { it.px }
        val minGapPx = (MIN_ROUTE_WIDTH_DP / 2 + CLEARANCE_DP) * pic.density
        val oppView = opposite.filter { pic.inside(it.px, marginPx) }
        val qualified = oppView.filter { distanceToPolyline(it.px, ownLine) >= minGapPx }
        val visible = qualified.count { close(pic.at(it.px), reference) }

        if (ownView.size < minSamples) reasons += "only ${ownView.size} own-carriageway samples in view"
        if (inked < MIN_SHARE * ownView.size) reasons += "route covers $inked/${ownView.size} own-carriageway samples"
        if (median < MIN_ROUTE_WIDTH_DP) reasons += "route median width %.1f dp is not legible".format(median)
        if (oppView.size < minSamples) reasons += "only ${oppView.size} opposite-carriageway samples in view"
        // Guard against a vacuous pass: most of the opposite carriageway must be testable here.
        if (qualified.size < 0.8 * oppView.size) reasons += "only ${qualified.size}/${oppView.size} opposite samples testable"
        if (visible < MIN_SHARE * qualified.size) reasons += "opposite carriageway visible at $visible/${qualified.size} testable samples"
        return CarriagewayVerdict(ownView.size, inked, median, oppView.size, qualified.size, visible, reasons)
    }

    data class Band(val from: Double, val to: Double, val expect: Expect)
    enum class Expect { AMBER, RED, NEITHER }

    data class TrafficVerdict(val counts: Map<Band, Pair<Int, Int>>, val reasons: List<String>) {
        val pass get() = reasons.isEmpty()
    }

    /**
     * 02.03. Inside each band (keeping [edgeM] metres clear of its ends, where round caps meet),
     * the route centreline must read as the expected colour; outside the bands it must be neither
     * amber nor red.
     */
    fun traffic(pic: Picture, own: List<Sample>, bands: List<Band>, edgeM: Double = 25.0,
                marginPx: Int = 24, minSamples: Int = 5): TrafficVerdict {
        val reasons = ArrayList<String>()
        val counts = LinkedHashMap<Band, Pair<Int, Int>>()
        for (band in bands) {
            val inside = own.filter { it.station in (band.from + edgeM)..(band.to - edgeM) && pic.inside(it.px, marginPx) }
            val ok = inside.count {
                val c = pic.at(it.px)
                when (band.expect) {
                    Expect.AMBER -> isAmber(c) && !isRed(c)
                    Expect.RED -> isRed(c) && !isAmber(c)
                    Expect.NEITHER -> !isAmber(c) && !isRed(c)
                }
            }
            counts[band] = ok to inside.size
            if (inside.size < minSamples) reasons += "${band.expect} ${band.from.toInt()}–${band.to.toInt()} m: only ${inside.size} samples in view"
            else if (ok < MIN_SHARE * inside.size) reasons += "${band.expect} ${band.from.toInt()}–${band.to.toInt()} m: $ok/${inside.size} samples have the expected colour"
        }
        return TrafficVerdict(counts, reasons)
    }

    // ---- car arrow (02.04) --------------------------------------------------------------------

    /** One UK traffic lane, metres: the standard lane width in DMRB CD 127 (all-purpose roads). */
    const val LANE_M = 3.65
    /** Furthest the arrow's centre may sit from its carriageway centreline: about a quarter lane. */
    const val MAX_ARROW_OFFSET_M = 1.0
    /** Largest angle between the arrow and the road it is on. */
    const val MAX_ARROW_HEADING_DEG = 10.0

    /** Pixels that differ between two renders of the same scene: what the second one added. */
    fun added(without: Picture, with: Picture, tol: Int = 12): List<Px> {
        require(without.width == with.width && without.height == with.height)
        val out = ArrayList<Px>()
        for (y in 0 until with.height) for (x in 0 until with.width)
            if (!close(without.pixel(x, y), with.pixel(x, y), tol)) out += Px(x.toFloat(), y.toFloat())
        return out
    }

    /** Screen bearing of a direction: 0 is up the screen, 90 is right. */
    fun screenBearing(dx: Double, dy: Double) = (Math.toDegrees(kotlin.math.atan2(dx, -dy)) + 360.0) % 360.0

    fun angleBetween(a: Double, b: Double): Double { val d = abs(a - b) % 360.0; return if (d > 180) 360 - d else d }

    data class ArrowShape(val centre: Px, val headingDeg: Double, val widthPx: Double, val symmetry: Double, val pixels: Int)

    /**
     * Finds an arrow's pointing direction from its pixels alone: the mirror axis that maps the
     * most pixels onto other pixels, then the end of that axis reaching furthest from the centre
     * (the tip). Width is the extent across that axis.
     */
    fun arrowShape(mask: List<Px>): ArrowShape? {
        if (mask.size < 30) return null
        val cx = mask.sumOf { it.x.toDouble() } / mask.size
        val cy = mask.sumOf { it.y.toDouble() } / mask.size
        val set = HashSet<Long>(mask.size * 2)
        fun key(x: Int, y: Int) = (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)
        mask.forEach { set += key(it.x.toInt(), it.y.toInt()) }
        var best = 0.0; var bestScore = -1.0
        for (deg in 0 until 180) {
            val t = Math.toRadians(deg.toDouble())
            val ux = sin(t); val uy = -cos(t)            // axis direction, screen bearing [deg]
            var hit = 0
            for (p in mask) {
                val dx = p.x - cx; val dy = p.y - cy
                val along = dx * ux + dy * uy
                // Mirror across the axis: keep the along-axis part, flip the across part.
                val rx = cx + 2 * along * ux - dx; val ry = cy + 2 * along * uy - dy
                if (key(Math.round(rx).toInt(), Math.round(ry).toInt()) in set) hit++
            }
            val score = hit.toDouble() / mask.size
            if (score > bestScore) { bestScore = score; best = deg.toDouble() }
        }
        val t = Math.toRadians(best)
        val ux = sin(t); val uy = -cos(t)
        var fwd = 0.0; var back = 0.0; var lo = Double.MAX_VALUE; var hi = -Double.MAX_VALUE
        for (p in mask) {
            val dx = p.x - cx; val dy = p.y - cy
            val along = dx * ux + dy * uy
            fwd = max(fwd, along); back = max(back, -along)
            val across = dx * -uy + dy * ux
            lo = min(lo, across); hi = max(hi, across)
        }
        val heading = if (fwd >= back) best else (best + 180.0) % 360.0
        return ArrowShape(Px(cx.toFloat(), cy.toFloat()), heading, hi - lo + 1.0, bestScore, mask.size)
    }

    data class ArrowVerdict(
        val widthM: Double, val offsetM: Double, val headingDeg: Double, val roadDeg: Double,
        val symmetry: Double, val pixels: Int, val reasons: List<String>,
    ) { val pass get() = reasons.isEmpty() }

    /**
     * 02.04 on a top-down render. The arrow must sit on its carriageway's centreline, point along
     * the road the way traffic flows (the order of [own]'s samples), and be between one lane and
     * the whole carriageway ([lanes] × [LANE_M]) wide on the ground. Scale comes from [own]'s
     * samples: their known ground spacing against their screen spacing.
     */
    fun arrow(mask: List<Px>, own: List<Sample>, lanes: Int): ArrowVerdict {
        val shape = arrowShape(mask)
            ?: return ArrowVerdict(0.0, 0.0, 0.0, 0.0, 0.0, mask.size, listOf("no arrow found (${mask.size} px)"))
        val near = own.zipWithNext().minByOrNull { (a, b) -> distanceToPolyline(shape.centre, listOf(a.px, b.px)) }!!
        val (a, b) = near
        val pxPerM = hypot((b.px.x - a.px.x).toDouble(), (b.px.y - a.px.y).toDouble()) / (b.station - a.station)
        val road = screenBearing((b.px.x - a.px.x).toDouble(), (b.px.y - a.px.y).toDouble())
        val widthM = shape.widthPx / pxPerM
        val offsetM = distanceToPolyline(shape.centre, own.map { it.px }) / pxPerM
        val carriageway = lanes * LANE_M
        val reasons = ArrayList<String>()
        if (shape.symmetry < 0.8) reasons += "no clear arrow shape (mirror match %.2f)".format(shape.symmetry)
        if (widthM < LANE_M) reasons += "arrow %.1f m wide, narrower than one lane (%.2f m)".format(widthM, LANE_M)
        if (widthM > carriageway) reasons += "arrow %.1f m wide, wider than its %d-lane carriageway (%.2f m)".format(widthM, lanes, carriageway)
        if (offsetM > MAX_ARROW_OFFSET_M) reasons += "arrow centre %.1f m off the carriageway centreline".format(offsetM)
        val err = angleBetween(shape.headingDeg, road)
        if (err > MAX_ARROW_HEADING_DEG) reasons += "arrow points %.0f° away from the road".format(err)
        return ArrowVerdict(widthM, offsetM, shape.headingDeg, road, shape.symmetry, shape.pixels, reasons)
    }
}
