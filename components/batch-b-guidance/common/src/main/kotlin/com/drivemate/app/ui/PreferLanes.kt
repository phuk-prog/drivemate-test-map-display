package com.drivemate.app.ui
import com.drivemate.app.data.Lane

fun preferLanes(lanes: List<Lane>, nextTurn: Float?): List<Lane> {
    val usable = lanes.indices.filter { lanes[it].valid }
    if (usable.size < 2 || nextTurn == null || kotlin.math.abs(nextTurn) < 20f) return lanes
    val keep = maxOf(1, usable.size / 2)
    val chosen = (if (nextTurn < 0) usable.take(keep) else usable.takeLast(keep)).toSet()
    return lanes.mapIndexed { i, l -> if (l.valid) l.copy(preferred = i in chosen) else l }
}
