package com.drivemate.app.nav

import com.drivemate.app.data.Lane

/**
 * 12.2 and 12.3 (owner, 20 Oct 2026): one answer to "which lanes are yours", used by the lane
 * picture, the spoken words, the junction view and Android Auto alike, so what you see and what
 * you hear always name the same lanes.
 */
object LanePlan {
    /**
     * The lanes to use, left to right: the lanes you may use, narrowed to the preferred ones when
     * some are preferred. Never a bus lane, and never a lane whose markings are unknown (it is
     * drawn with a question mark and no recommendation). Empty when there are none.
     */
    fun yours(lanes: List<Lane>): List<Int> {
        val usable = lanes.indices.filter { i ->
            lanes[i].valid && !lanes[i].bus && lanes[i].indications.any { LaneDirections.angle(it) != null }
        }
        return usable.filter { lanes[it].preferred }.ifEmpty { usable }
    }

    /**
     * How far after a move the next one still decides which lane to take for it (12.3). Off a
     * slip road the next junction (often a roundabout) can be most of a kilometre away and there
     * is little room to change lanes on the slip, so the exit lane is chosen for it too.
     */
    fun followingWindow(type: String): Double = if (type in setOf("off ramp", "on ramp", "fork")) 1_000.0 else 300.0

    /**
     * The lanes for a move from map data: the route's own lane arrows (not when the map shows a
     * roundabout entry has more lanes than they list), else our lane database. Set up for the
     * move after it ([followingTurn], degrees). Corrections are chosen before this and are never
     * re-planned: the lane the driver picked stays the one shown and said.
     */
    fun turnLanes(routeLanes: List<Lane>, roundabout: Boolean, entryCount: Int, followingTurn: Float?, ownLanes: () -> List<Lane>?): List<Lane>? {
        val source = routeLanes.takeIf { it.size > 1 && !(roundabout && entryCount > it.size) }
            ?: ownLanes()?.takeIf { it.size > 1 }
            ?: return null
        return com.drivemate.app.ui.preferLanes(source, followingTurn)
    }
}
