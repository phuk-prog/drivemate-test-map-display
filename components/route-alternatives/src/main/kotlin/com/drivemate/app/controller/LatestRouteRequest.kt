package com.drivemate.app.controller

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * One route-changing request at a time. Use on the main thread with the ViewModel scope.
 * Cancellation alone cannot invalidate a blocking provider call: the generation also guards
 * its eventual result and its finally block, so an old request cannot clear the new busy state.
 */
class LatestRouteRequest(private val scope: CoroutineScope) {
    private var generation = 0L
    private var job: Job? = null

    class Ticket internal constructor(private val current: () -> Boolean) {
        fun isCurrent(): Boolean = current()
    }

    fun cancel() {
        generation++
        job?.cancel()
        job = null
    }

    fun launch(block: suspend (Ticket) -> Unit) {
        cancel()
        val id = generation
        val next = scope.launch(start = CoroutineStart.LAZY) {
            block(Ticket { generation == id })
        }
        job = next
        next.start()
    }
}
