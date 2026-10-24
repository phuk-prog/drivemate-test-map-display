package com.drivemate.app.nav

import com.drivemate.app.data.Lane
import com.drivemate.app.data.RouteStep

/** How directions are put into words (shared by the voice and the turn card). */
object Wording {
    /** "Use the left lane", "Use the middle lane", "Avoid the right lane"… or "" if any lane will do. */
    fun laneWords(lanes: List<Lane>): String {
        val n = lanes.size
        val best = LanePlan.yours(lanes)
        if (n < 2 || best.isEmpty() || best.size == n) return ""
        // "the left lane", "the right two lanes", "either of the middle lanes"…
        val count = when (best.size) {
            1 -> "lane"
            2 -> "2 lanes"
            3 -> "3 lanes"
            else -> "${best.size} lanes"
        }
        // Separated usable lanes must not be described as a continuous left/middle/right group.
        if (best.zipWithNext().any { (a, b) -> b != a + 1 }) {
            return "Use lane " + best.joinToString(" or ") { (it + 1).toString() } + " from the left"
        }
        // 12.2: "left"/"right" lanes only when they start at that edge, so the words never send
        // you to the edge lane when the picture shows the one next to it (four lanes, the second
        // from the left is yours: "the second lane from the left", not "the left lane").
        if (best.first() == 0) return "Use the left $count"
        if (best.last() == n - 1) return "Use the right $count"
        if (best.first() == n - 1 - best.last()) return "Use the middle $count"
        val fromLeft = best.first() <= n - 1 - best.last()
        if (best.size == 1) {
            val k = if (fromLeft) best.first() + 1 else n - best.first()
            return "Use the ${spokenOrdinal(k)} lane from the ${if (fromLeft) "left" else "right"}"
        }
        return "Use lanes " + best.joinToString(" and ") { (it + 1).toString() } + " from the left"
    }

    /**
     * A direction with the lane to use put first: roundabouts say "use the right lane to enter
     * the roundabout, then take the fifth exit onto X"; other moves "use the left lane, then
     * turn left onto X". With no lane advice, just the direction.
     */
    fun withLane(step: RouteStep, instr: String, use: String): String {
        if (use.isBlank()) return instr
        val lane = use.replaceFirstChar { it.lowercase() }
        if (step.isRoundabout && step.exit > 0) {
            val onto = if (step.name.isNotBlank()) " onto ${step.name}" else ""
            return "$lane to enter the roundabout, then take the ${spokenOrdinal(step.exit)} exit$onto"
        }
        if (step.modifier in setOf("straight", "through") && instr.startsWith("Continue straight")) {
            return "$use to ${instr.replaceFirstChar { it.lowercase() }}"
        }
        // Explicit route direction, not a guess from the generic Continue wording.
        if (step.type in setOf("continue", "new name", "notification") &&
            step.modifier in setOf("straight", "through") &&
            (instr == "Continue" || instr.startsWith("Continue onto "))) {
            return "$use to continue straight" + instr.removePrefix("Continue")
        }
        return "$lane, then $instr"
    }

    private fun spokenOrdinal(n: Int) =
        listOf("", "first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth").getOrElse(n) { "${n}th" }

    /**
     * Leaving a motorway by the very next exit: "Take the next exit, J3, towards M60 Ring Rd
     * E & N" (junction number and road sign as the route gives them; either may be missing).
     */
    fun nextExit(step: RouteStep): String {
        val jn = step.exitNumber.trim().removePrefix("J").removePrefix("j").trim()
        // The sign text; else the road it leads to (not a motorway name, which slip roads off
        // that same motorway often carry in the map).
        val sign = step.towards.ifBlank { step.name.takeUnless { isMotorway(it) } ?: "" }.trim()
        val side = when {
            step.modifier.contains("right") -> " on the right"
            else -> ""
        }
        return ("Take the next exit$side" + (if (jn.isNotEmpty()) ", J$jn," else "") +
            (if (sign.isNotEmpty()) " towards $sign" else "")).removeSuffix(",")
    }

    /** A motorway (M60, A1(M)) by its name or number. */
    fun isMotorway(name: String?): Boolean =
        name != null && Regex("(^|[^A-Z])(M\\d+|A\\d+\\(M\\))").containsMatchIn(name)

    /**
     * True when the lanes to use are just the lanes on the side you're leaving by (the left
     * lane for a left exit). This can suppress repetitive speech, but must never hide
     * verified lane guidance from the screen. A split where you must be in, say, lanes 2–3 isn't obvious and is kept.
     */
    fun obviousExitLanes(lanes: List<Lane>, maneuver: String): Boolean {
        val side = when {
            maneuver.contains("left") -> -1
            maneuver.contains("right") -> 1
            else -> return false
        }
        val best = LanePlan.yours(lanes)
        if (best.isEmpty() || best.size == lanes.size) return false
        // The lanes to use start at that edge and run inwards without a gap.
        val fromEdge = if (side < 0) (0 until best.size).toList() else ((lanes.size - best.size) until lanes.size).toList()
        return best == fromEdge
    }

    /**
     * Going round a roundabout where lanes split or merge: which lane to keep to there, from
     * the lane data ("Keep to the right lane", "Keep to the left two lanes"), or "" if any will do.
     */
    fun keepWords(lanes: List<Lane>): String {
        val use = laneWords(lanes)
        return when {
            use.startsWith("Use the ") -> "Keep to the " + use.removePrefix("Use the ")
            else -> use
        }
    }

    /** The lane to leave a roundabout from: "from the left lane", or "" if any will do. */
    fun exitFrom(lanes: List<Lane>): String {
        val use = laneWords(lanes)
        return if (use.startsWith("Use the ")) "from the " + use.removePrefix("Use the ") else ""
    }
}
