package com.drivemate.app.nav

import kotlin.math.pow

/**
 * One meaning for the route colour everywhere (MG07): the live map, the junction scene and the
 * lower overview all draw "your route" as the same bright turquoise, edged in deep navy and (on the
 * map) a pale halo, so it reads against white day roads, dark night roads and the scene's grey tarmac.
 * Purple is no longer used for the route. The turquoise sits in the widest gap between the road
 * colours (OKLCH hue 200: at least 45 degrees from blue motorways, green A roads, purple roads and
 * traffic red; the old cyan was 43 degrees from motorway blue), test 2.16.
 */
object RoutePalette {
    /** The colours of the route stack, outermost first: halo, casing, fill; plus the driven part. */
    data class Colors(val halo: Long, val casing: Long, val fill: Long, val driven: Long, val drivenCasing: Long)

    private val dayColors = Colors(
        halo = 0xFFFFFFFFL, casing = 0xFF0B3D56L, fill = 0xFF00E5EEL,
        driven = 0xFFA0A4AAL, drivenCasing = 0xFF80868BL,
    )
    private val nightColors = Colors(
        halo = 0xFFE8EAEDL, casing = 0xFF062433L, fill = 0xFF00E5EEL,
        driven = 0xFF5F6368L, drivenCasing = 0xFF3C4148L,
    )

    fun colors(night: Boolean): Colors = if (night) nightColors else dayColors

    /**
     * Route line widths (px) at zoom 12, 16 and 19, growing like the map style's roads (exponential 1.6),
     * so the route stays wider than a motorway with its casing at every driving zoom (test 2.16).
     */
    enum class Line(val z12: Float, val z16: Float, val z19: Float) {
        HALO(17f, 19f, 46f), CASING(14f, 16f, 42f), FILL(9f, 11f, 34f),
    }

    /** The width of [line] at [zoom], as the map interpolates it. */
    fun width(line: Line, zoom: Double): Double = interpolate(zoom, listOf(12.0 to line.z12, 16.0 to line.z16, 19.0 to line.z19))

    /** MapLibre's exponential (base 1.6) interpolation between zoom stops, clamped at both ends. */
    fun interpolate(zoom: Double, stops: List<Pair<Double, Float>>): Double {
        if (zoom <= stops.first().first) return stops.first().second.toDouble()
        val i = stops.indexOfFirst { it.first >= zoom }
        if (i < 0) return stops.last().second.toDouble()
        val (z0, w0) = stops[i - 1]; val (z1, w1) = stops[i]
        val t = (1.6.pow(zoom - z0) - 1) / (1.6.pow(z1 - z0) - 1)
        return w0 + (w1 - w0) * t
    }

    /** How solid the route is in the scene (0..255): road paint shows through the rest. */
    const val SCENE_ALPHA = 0xD2L

    /** The route fill as the scene draws it: the same colour, translucent so road paint stays visible. */
    fun sceneFill(night: Boolean): Long = (SCENE_ALPHA shl 24) or (colors(night).fill and 0xFFFFFFL)

    /** `#RRGGBB` for the map style. */
    fun hex(argb: Long): String = "#%06X".format(argb and 0xFFFFFFL)

    private fun channel(c: Long, shift: Int): Double {
        val v = ((c shr shift) and 0xFF) / 255.0
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    /** WCAG relative luminance of the colour (alpha ignored). */
    fun luminance(c: Long): Double = 0.2126 * channel(c, 16) + 0.7152 * channel(c, 8) + 0.0722 * channel(c, 0)

    /** WCAG contrast ratio between two colours, 1 (none) to 21. */
    fun contrast(a: Long, b: Long): Double {
        val la = luminance(a); val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** [top] with its alpha laid over opaque [under]. */
    fun over(top: Long, under: Long): Long {
        val a = ((top shr 24) and 0xFF) / 255.0
        fun ch(s: Int) = Math.round(((top shr s) and 0xFF) * a + ((under shr s) and 0xFF) * (1 - a)).toLong()
        return 0xFF000000L or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
