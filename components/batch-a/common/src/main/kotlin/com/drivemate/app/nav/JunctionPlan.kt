package com.drivemate.app.nav

import com.drivemate.app.data.Lane

/** A road number on a UK-style sign: motorway blue, primary (A road) green, anything else white. */
data class Shield(val ref: String, val kind: Kind) {
    enum class Kind { MOTORWAY, PRIMARY, OTHER }
}

/**
 * Where the lane highlight came from. NONE = no highlight, no lane claim. ARROWS = the lanes the
 * lane strip already uses (your corrections, the route's lanes or our lane database). There is
 * deliberately no "from lane counts" source: a highlight is never worked out from a lane count.
 */
enum class RibbonSource { NONE, ARROWS }

/** What kind of junction is drawn. */
enum class JunctionKind { EXIT, TURN, ROUNDABOUT }

/**
 * What the immersive junction view should draw before a junction: a motorway exit or split, a
 * slip road, a roundabout or a town turn. Pure data: the drawing code does no thinking of its own.
 *
 * For an [JunctionKind.EXIT] (exit or split) lanes are numbered from the exit's side (0 = the
 * lane next to the exit); [exitLeft] says which way to mirror it. For turns and roundabouts,
 * lanes are numbered left to right and [exitLeft] / [straight] give the branch side.
 *
 * Data only: [lanes] is null when the number of lanes is not known (draw a neutral road shape,
 * no lane dashes). [highlighted] and [arrows] are empty unless real lane data (arrows) says
 * which lanes to use; a lane count alone only shapes the road, it never picks a lane.
 */
data class JunctionPlan(
    val exitLeft: Boolean,
    val lanes: Int?,
    val exitLanes: Int?,
    val highlighted: List<Int>,
    val source: RibbonSource,
    val shields: List<Shield>,
    val destination: String,
    val exitNumber: String,
    val kind: JunctionKind = JunctionKind.EXIT,
    /** A turn that goes straight on (no side arm). */
    val straight: Boolean = false,
    /** Roundabout exit number (0 = not given). */
    val roundaboutExit: Int = 0,
    /** Where the exit arm leaves the roundabout, degrees clockwise from straight ahead; null = not drawn. */
    val roundaboutAngle: Int? = null,
    /** Each lane's arrows, in the same order as the lane numbers above; empty = draw no arrows. */
    val arrows: List<List<String>> = emptyList(),
    /** The move the lanes are for ("left", "slight right"…), so the right arrow can be picked out. */
    val maneuver: String = "",
    /** Drive on the left (UK): roundabouts go clockwise. */
    val leftDriving: Boolean = true,
    /**
     * The real shape of the road you take, from the route geometry: (metres ahead, metres off
     * to the side you go) every 15 m after the split; empty = not known (a standard shape is
     * drawn). See NavigationEngine.branchProfile.
     */
    val branch: List<Pair<Float, Float>> = emptyList(),
    /** A motorway split (both carriageways bend away), not an exit off a road that carries on. */
    val fork: Boolean = false,
    /** For a turn: how sharply the road you turn into leaves, degrees clockwise from straight ahead; null = not known. */
    val turnAngle: Int? = null,
    /** The shared geographical junction model (MG08) when map data gave one; null = draw the schematic. */
    val model: JunctionModel? = null,
) {
    /**
     * True when the road you take runs alongside before peeling away (a slip road with a long
     * diverge lane): less than 12 m to the side 120 m after the split. Null when not known.
     */
    val gradual: Boolean? get() {
        if (branch.size < 3) return null
        val far = branch.lastOrNull { it.first <= 120f } ?: return null
        if (far.first < 60f) return false // turned away within 60 m: not a parallel lane
        return far.second < 12f
    }

    val lanesUnknown get() = lanes == null

    /**
     * How far round the roundabout you go, in degrees from where you come in (the bottom of the
     * picture): positive = clockwise (UK), negative = anticlockwise. Null when no exit arm is drawn.
     */
    val roundaboutSweep: Float? get() = roundaboutAngle?.let { sweep(it, leftDriving) }

    /** What the picture shows, in words (for screen readers). */
    fun describe(distanceText: String): String {
        val side = if (exitLeft) "left" else "right"
        val what = when (kind) {
            JunctionKind.EXIT -> "exit on the $side"
            JunctionKind.ROUNDABOUT -> if (roundaboutExit > 0) "roundabout, exit $roundaboutExit" else "roundabout"
            JunctionKind.TURN -> if (straight) "straight on" else "turn $side"
        }
        val lanesText = when {
            source == RibbonSource.ARROWS -> "lane${if (highlighted.size == 1) "" else "s"} to use highlighted"
            lanes != null -> "$lanes lanes"
            else -> "no lane information"
        }
        return "Junction view: $what in $distanceText" +
            (if (destination.isNotBlank()) ", towards $destination" else "") + ", $lanesText"
    }

    companion object {
        private val refPattern = Regex("^(M\\d+[A-Z]?|A\\d+[A-Z]?(\\(M\\))?|B\\d+)$")

        /** Steps the junction view is for: any junction with a manoeuvre. */
        fun applies(type: String): Boolean =
            type in setOf("turn", "end of road", "fork", "off ramp", "on ramp", "roundabout", "rotary")

        /**
         * How far ahead (m) to show it: about 600 m on motorways and fast dual carriageways; in
         * towns 250 m on 40 mph+ roads, 150 m on 20 mph streets and 200 m otherwise.
         * [limitKmh] is the speed limit where you are, if known.
         */
        fun range(fastRoad: Boolean, limitKmh: Double? = null): Double = when {
            fastRoad -> 600.0
            limitKmh != null && limitKmh >= 63.0 -> 250.0
            limitKmh != null && limitKmh <= 33.0 -> 150.0
            else -> 200.0
        }

        /** See [roundaboutSweep]. [angle] is where the exit leaves, clockwise from straight ahead. */
        fun sweep(angle: Int, leftDriving: Boolean): Float {
            val a = ((angle % 360) + 360) % 360 // 0..359, clockwise from the top
            return if (leftDriving) ((a - 180 + 360) % 360).toFloat().let { if (it == 0f) 360f else it }
            else -((180 - a + 360) % 360).toFloat().let { if (it == 0f) 360f else it }
        }

        fun shieldKind(ref: String) = when {
            ref.startsWith("M") || ref.endsWith("(M)") -> Shield.Kind.MOTORWAY
            ref.startsWith("A") -> Shield.Kind.PRIMARY
            else -> Shield.Kind.OTHER
        }

        /**
         * Road numbers and destination text from what the route gave. [towards] is the sign text
         * ("A34 Oxford, Birmingham"); [ref] is the road number of the way you join ("A34;M40").
         * Anything not in the data is simply left out.
         */
        fun signs(ref: String, towards: String): Pair<List<Shield>, String> {
            val tokens = towards.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            val leading = tokens.takeWhile { refPattern.matches(it) }
            val destination = tokens.drop(leading.size).joinToString(" ")
            val refs = (ref.split(";", ",").map { it.trim().replace(" ", "") } + leading)
                .filter { refPattern.matches(it) }.distinct().take(3)
            return refs.map { Shield(it, shieldKind(it)) } to destination
        }

        /**
         * @param modifier the step's modifier ("slight left", "right"...). With no side given, a
         *   UK exit is drawn on the left (the drawing only; never a lane claim).
         * @param lanes the lane result already used for the lane strip (arrows), or empty.
         * @param countBefore lanes on the carriageway approaching the split, from the lane DB.
         * @param countMain lanes continuing on the carriageway beyond the split.
         * @param countSlip lanes on the exit / branch road.
         * @param fastRoad on a motorway or a 60 mph+ dual carriageway (a fork there is a split).
         */
        fun build(
            type: String, modifier: String, leftDriving: Boolean,
            lanes: List<Lane>, countBefore: Int?, countMain: Int?, countSlip: Int?,
            exitNumber: String, ref: String, towards: String,
            roundaboutExit: Int = 0, exitAngle: Int? = null,
            maneuver: String = modifier,
            branch: List<Pair<Float, Float>> = emptyList(),
            fastRoad: Boolean = true,
        ): JunctionPlan? {
            // A slip road onto a motorway (or one with a junction number) is drawn as an exit.
            val slipToMotorway = type == "on ramp" && (exitNumber.isNotBlank() ||
                ref.split(";", ",").any { Wording.isMotorway(it.trim()) } || Wording.isMotorway(towards.substringBefore(" ")))
            val kind = when {
                type == "roundabout" || type == "rotary" -> JunctionKind.ROUNDABOUT
                type == "off ramp" || type == "fork" || slipToMotorway -> JunctionKind.EXIT
                else -> JunctionKind.TURN
            }
            if (!applies(type) || modifier.contains("uturn")) return null
            // A fork on an ordinary town road (not a motorway or fast dual carriageway, no junction
            // number, not towards a motorway) is an ordinary junction: like turns, it only gets a
            // view where real lane data gives a lane choice.
            val townFork = type == "fork" && !fastRoad && exitNumber.isBlank() &&
                ref.split(";", ",").none { Wording.isMotorway(it.trim()) } && !Wording.isMotorway(towards.substringBefore(" "))
            if (townFork) {
                if (lanes.size < 2) return null
                val usable = lanes.indices.filter { lanes[it].valid && !lanes[it].bus }
                if (usable.isEmpty() || usable.size == lanes.size) return null
            }
            val (shields, destination) = signs(ref, towards)
            val angle = exitAngle?.takeIf { roundaboutExit > 0 }

            when (kind) {
                JunctionKind.ROUNDABOUT, JunctionKind.TURN -> {
                    val left = when (kind) {
                        JunctionKind.ROUNDABOUT -> (angle ?: 0) < 0
                        else -> modifier.contains("left")
                    }
                    val straight = kind == JunctionKind.TURN && !modifier.contains("left") && !modifier.contains("right")
                    fun plan(n: Int?, hi: List<Int>, src: RibbonSource) = JunctionPlan(
                        left, n, null, hi, src, shields, destination, exitNumber.trim(), kind, straight,
                        if (kind == JunctionKind.ROUNDABOUT) roundaboutExit else 0,
                        if (kind == JunctionKind.ROUNDABOUT) angle else null,
                        arrows = if (src == RibbonSource.ARROWS) lanes.map { it.indications.ifEmpty { listOf("straight") } } else emptyList(),
                        maneuver = maneuver, leftDriving = leftDriving,
                        turnAngle = if (kind == JunctionKind.TURN) exitAngle else null,
                    )
                    // Owner rule: ordinary turns and roundabouts get no junction view. Only where
                    // real lane data gives two or more lanes and a lane choice (some lanes are not
                    // for you) is the view worth showing; motorway exits and splits always are.
                    if (lanes.size < 2) return null
                    val usable = lanes.indices.filter { lanes[it].valid && !lanes[it].bus }
                    if (usable.isEmpty() || usable.size == lanes.size) return null
                    return plan(lanes.size, LanePlan.yours(lanes), RibbonSource.ARROWS)
                }
                JunctionKind.EXIT -> Unit
            }
            val exitLeft = when {
                modifier.contains("left") -> true
                modifier.contains("right") -> false
                type == "off ramp" || type == "on ramp" -> leftDriving
                else -> return null // a fork with no side: nothing safe to draw
            }

            fun plan(n: Int?, k: Int?, hi: List<Int>, src: RibbonSource, arrows: List<List<String>> = emptyList()) =
                JunctionPlan(exitLeft, n, k, hi, src, shields, destination, exitNumber.trim(), arrows = arrows, maneuver = maneuver,
                    leftDriving = leftDriving, branch = branch, fork = type == "fork")

            // (a) Lane arrows: exactly the lanes the lane strip uses, numbered from the exit's side.
            val layout = if (modifier.contains("left") || modifier.contains("right")) junctionLayout(lanes, modifier) else null
            if (layout != null) {
                val fromSide = if (exitLeft) lanes else lanes.reversed()
                return plan(layout.lanes, layout.exitLanes, layout.yours, RibbonSource.ARROWS,
                    fromSide.map { it.indications.ifEmpty { listOf("straight") } })
            }
            // Arrows exist but don't give a clear picture: the count is data, the highlight is not.
            if (lanes.size >= 2) return plan(lanes.size, null, emptyList(), RibbonSource.NONE)

            // (b) Lane counts from our lane database only shape the road (how wide the exit is
            //     drawn) when they add up at the split: before = continuing + exit. They never
            //     pick a lane: no highlight from counts.
            if (countBefore != null && countMain != null && countSlip != null) {
                return if (countBefore in 2..6 && countMain >= 1 && countSlip >= 1 && countBefore == countMain + countSlip) {
                    plan(countBefore, countSlip, emptyList(), RibbonSource.NONE)
                } else {
                    // Inconsistent: don't trust any of the numbers.
                    plan(null, null, emptyList(), RibbonSource.NONE)
                }
            }
            // Counts missing: show the approach lanes only if that one number is known.
            return plan(countBefore?.takeIf { it in 2..6 }, null, emptyList(), RibbonSource.NONE)
        }
    }
}
