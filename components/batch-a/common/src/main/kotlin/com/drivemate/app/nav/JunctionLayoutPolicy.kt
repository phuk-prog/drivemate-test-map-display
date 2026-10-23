package com.drivemate.app.nav

import com.drivemate.app.data.Lane

/**
 * Which lanes go where at a motorway exit or a fork, worked out from the lane arrows.
 * [exitLanes] is how many lanes, counted from the exit's side, lead into the exit.
 */
data class JunctionLayout(val exitLeft: Boolean, val lanes: Int, val exitLanes: Int, val takeExit: Boolean, val yours: List<Int>)

/**
 * The layout for a junction picture, or null when the lane data can't show it clearly (then
 * no picture: we never draw a guess). [modifier] is the move ("slight left", "right"…): the
 * side you leave or fork to.
 */
internal fun junctionLayout(lanes: List<Lane>, modifier: String): JunctionLayout? {
    val n = lanes.size
    if (n < 2) return null
    val left = modifier.contains("left")
    if (!left && !modifier.contains("right")) return null
    // Lanes in order from the exit's side.
    val fromSide = if (left) lanes else lanes.reversed()
    val sideWord = if (left) "left" else "right"
    val toExit = fromSide.takeWhile { l -> l.indications.any { it.contains(sideWord) && LaneDirections.angle(it) != null } }.size
    if (toExit == 0 || toExit == n) return null
    // Your lanes, counted from the exit's side.
    val yours = LanePlan.yours(fromSide)
    if (yours.isEmpty()) return null
    // The move goes to the [modifier] side, so your lanes must lead there; if the data says
    // otherwise, show no picture rather than a contradiction.
    if (yours.any { it >= toExit }) return null
    // Validate all usable lanes too: a preferred lane must not hide contradictory data.
    if (fromSide.indices.any { fromSide[it].valid && (it >= toExit || fromSide[it].bus) }) return null
    // Non-contiguous exit branches cannot be represented by this simple split picture.
    if (fromSide.drop(toExit).any { l -> l.indications.any { it.contains(sideWord) && LaneDirections.angle(it) != null } }) return null
    return JunctionLayout(left, n, toExit, takeExit = true, yours)
}
