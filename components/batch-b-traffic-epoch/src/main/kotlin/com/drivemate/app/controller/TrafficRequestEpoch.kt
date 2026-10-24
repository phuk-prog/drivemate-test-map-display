package com.drivemate.app.controller

/** One blocking incident request at a time, with credential-change publication authority.
 * Tickets contain no key, location, route or provider response. Cancellation does not free
 * the reservation: only the request's own finally may do so after blocking work returns.
 */
class TrafficRequestEpoch {
    class Ticket internal constructor(internal val epoch: Long)
    private var epoch = 0L
    private var active: Ticket? = null
    private var refreshAfterFinish = false

    @Synchronized fun inFlight(): Boolean = active != null
    @Synchronized fun begin(): Ticket? {
        if (active != null) return null
        return Ticket(epoch).also { active = it }
    }
    @Synchronized fun accepts(ticket: Ticket): Boolean = active === ticket && ticket.epoch == epoch
    @Synchronized fun invalidate() {
        epoch++
        refreshAfterFinish = active != null
    }
    /** Returns one pending refresh only for the request that actually held the reservation. */
    @Synchronized fun finish(ticket: Ticket): Boolean {
        if (active !== ticket) return false
        active = null
        return refreshAfterFinish.also { refreshAfterFinish = false }
    }
}
