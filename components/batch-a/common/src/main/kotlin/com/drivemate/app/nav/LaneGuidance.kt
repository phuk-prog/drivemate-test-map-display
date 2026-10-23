package com.drivemate.app.nav

import kotlin.math.abs

/**
 * One verified lane-to-lane fact for the approach (lanes numbered left to right).
 * [fromLane] == [toLane]: the lane carries on (round a spiral roundabout, or through the junction) to
 * the exit with no need to cross a line. Otherwise traffic in [fromLane] has to move into [toLane] by
 * [byM] metres before the junction (null = timing not in the data).
 */
data class LaneLink(val fromLane: Int, val toLane: Int, val byM: Double? = null) {
    val sameLane get() = fromLane == toLane
}

/**
 * MG11: lane connectivity that a data source has checked. Nothing in the app invents it: a
 * lane count, an exit number or the Highway Code are never turned into links. [UNKNOWN] means
 * "not checked", and then no lane-change (or stay-in-lane) claim is made.
 */
data class LaneConnectivity(val links: List<LaneLink>, val source: DataSource, val confidence: Double) {
    val verified get() = source != DataSource.NONE && confidence >= MIN_CONFIDENCE && links.isNotEmpty()

    companion object {
        const val MIN_CONFIDENCE = 0.7
        val UNKNOWN = LaneConnectivity(emptyList(), DataSource.NONE, 0.0)
    }
}

/** What to tell the driver about lanes through the junction. */
sealed class LaneAdvice {
    /** A continuous lane (spiral): keep to it, no lane change. */
    data class StayInLane(val lanes: List<Int>) : LaneAdvice()
    /** A verified lane that ends or must be left: change from [fromLane] to [toLane] by [byM] metres before the junction. */
    data class ChangeLane(val fromLane: Int, val toLane: Int, val byM: Double?) : LaneAdvice()
    /** Not checked: direction only, no lane claim. */
    data class Unknown(val reason: String) : LaneAdvice()
}

/** MG11 / RV02: stay-in-lane versus a required lane change, only from verified connectivity. */
object LaneGuide {
    /** The cue shows this long (seconds of driving) before the change point, and at least [MIN_LEAD_M]. */
    const val LEAD_S = 8.0
    const val MIN_LEAD_M = 60.0
    /** If the data has no timing, the change cue shows from this far out. */
    const val DEFAULT_WINDOW_M = 150.0
    /** The cue is dropped once the change point is this far behind. */
    const val PASSED_M = 40.0

    fun advise(connectivity: LaneConnectivity, recommended: List<Int>): LaneAdvice {
        if (!connectivity.verified) return LaneAdvice.Unknown("lane connections not checked")
        if (recommended.isEmpty()) return LaneAdvice.Unknown("no lane to use")
        val wanted = recommended.distinct().sorted()
        val into = wanted.map { lane -> connectivity.links.filter { it.toLane == lane } }
        if (into.any { it.isEmpty() }) return LaneAdvice.Unknown("connections only partly known")
        val flat = into.flatten()
        // A required change is the one link into a recommended lane that comes from a different lane.
        val changes = flat.filter { !it.sameLane }
        return when {
            changes.isEmpty() -> LaneAdvice.StayInLane(wanted)
            changes.size == 1 && wanted.size == 1 && into.single().size == 1 ->
                changes.single().let { LaneAdvice.ChangeLane(it.fromLane, it.toLane, it.byM) }
            else -> LaneAdvice.Unknown("more than one possible change")
        }
    }

    /** Advice for a plan: only the model's verified links, and only where lanes are numbered left to right. */
    fun adviseFor(plan: JunctionPlan): LaneAdvice {
        val model = plan.model ?: return LaneAdvice.Unknown("no junction model")
        if (plan.kind == JunctionKind.EXIT) return LaneAdvice.Unknown("exit lanes are numbered from the exit side")
        if (plan.source != RibbonSource.ARROWS) return LaneAdvice.Unknown("no lane data")
        return advise(model.connectivity, plan.highlighted)
    }

    /** The distinct words for [advice], or null when nothing should be said yet (or at all). */
    fun cue(advice: LaneAdvice, distanceToJunctionM: Double, speedMps: Double): String? = when (advice) {
        is LaneAdvice.StayInLane -> "Stay in your lane"
        is LaneAdvice.Unknown -> null
        is LaneAdvice.ChangeLane -> {
            val lead = maxOf(MIN_LEAD_M, speedMps.coerceAtLeast(0.0) * LEAD_S)
            val by = advice.byM
            val ahead = distanceToJunctionM - (by ?: 0.0)
            val window = if (by == null) DEFAULT_WINDOW_M else lead
            if (ahead > window || ahead < -PASSED_M) null else {
                val n = abs(advice.toLane - advice.fromLane)
                val side = if (advice.toLane > advice.fromLane) "right" else "left"
                "Move $side ${if (n == 1) "one lane" else "$n lanes"}" +
                    if (by != null) " within ${(Math.round(distanceToJunctionM - by).coerceAtLeast(0) / 10 * 10)} m" else ""
            }
        }
    }
}
