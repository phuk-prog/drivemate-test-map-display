package com.drivemate.app.controller

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TrafficRequestEpochTest {
    @Test fun freshTicketMayPublish() {
        val state = TrafficRequestEpoch()
        assertFalse(state.inFlight())
        val request = state.begin()!!
        assertTrue(state.inFlight())
        assertTrue(state.accepts(request))
    }
    @Test fun finishRevokesPublication() {
        val state = TrafficRequestEpoch()
        val request = state.begin()!!
        assertFalse(state.finish(request))
        assertFalse(state.accepts(request))
        assertFalse(state.inFlight())
    }
    @Test fun invalidationRevokesPublicationImmediately() {
        val state = TrafficRequestEpoch()
        val request = state.begin()!!
        state.invalidate()
        assertFalse(state.accepts(request))
        assertTrue(state.inFlight())
    }
    @Test fun invalidationWithoutActiveRequestDoesNotBlockNextRequest() {
        val state = TrafficRequestEpoch()
        state.invalidate()
        assertFalse(state.inFlight())
        val next = state.begin()!!
        assertTrue(state.accepts(next))
        assertFalse(state.finish(next))
    }
    @Test fun newRequestWaitsUntilOldBlockingWorkFinishes() {
        val state = TrafficRequestEpoch()
        val first = state.begin()!!
        state.invalidate()
        assertNull(state.begin())
        assertTrue(state.finish(first))
        assertNotNull(state.begin())
    }
    @Test fun keyAToBToADoesNotRestoreAnOldTicket() {
        val state = TrafficRequestEpoch()
        val fromA = state.begin()!!
        state.invalidate() // A to B
        state.invalidate() // B to A: authority is an epoch, not equality of key strings.
        assertFalse(state.accepts(fromA))
        assertTrue(state.finish(fromA))
        assertTrue(state.accepts(state.begin()!!))
    }
    @Test fun repeatedInvalidationsYieldOnePendingRefresh() {
        val state = TrafficRequestEpoch()
        val first = state.begin()!!
        repeat(5) { state.invalidate() }
        assertTrue(state.finish(first))
        assertFalse(state.finish(first))
        val next = state.begin()!!
        assertFalse(state.finish(next))
    }
    @Test fun foreignTicketCannotReleaseAReservation() {
        val owner = TrafficRequestEpoch()
        val other = TrafficRequestEpoch()
        val owned = owner.begin()!!
        val foreign = other.begin()!!
        assertFalse(owner.finish(foreign))
        assertTrue(owner.inFlight())
        assertTrue(owner.accepts(owned))
    }
    @Test fun oldFinallyCannotUnlockANewerRequest() {
        val state = TrafficRequestEpoch()
        val old = state.begin()!!
        state.finish(old)
        val fresh = state.begin()!!
        assertFalse(state.finish(old))
        assertNull(state.begin())
        assertTrue(state.accepts(fresh))
    }
    @Test fun completedOldTicketCannotConsumeANewerRetry() {
        val state = TrafficRequestEpoch()
        val old = state.begin()!!
        state.finish(old)
        val fresh = state.begin()!!
        state.invalidate()
        assertFalse(state.finish(old))
        assertTrue(state.finish(fresh))
    }
    @Test fun ordinaryFinishDoesNotScheduleAnotherFetch() {
        val state = TrafficRequestEpoch()
        val first = state.begin()!!
        assertFalse(state.finish(first))
        val next = state.begin()!!
        assertFalse(state.finish(next))
    }
    @Test fun cancellationKeepsReservationUntilBlockingWorkActuallyReturns() = runTest {
        val state = TrafficRequestEpoch()
        val request = state.begin()!!
        var published = false
        var retry = false
        val job = launch {
            try {
                withContext(NonCancellable) { delay(1_000) }
                if (state.accepts(request)) published = true
            } finally { retry = state.finish(request) }
        }
        runCurrent()
        state.invalidate()
        job.cancel()
        runCurrent()
        assertTrue(state.inFlight())
        assertNull(state.begin())
        advanceUntilIdle()
        job.join()
        assertFalse(published)
        assertFalse(state.inFlight())
        assertTrue(retry)
        assertTrue(state.accepts(state.begin()!!))
    }
    @Test fun nonCooperativeLateResultLosesAuthorityWithoutCancellation() = runTest {
        val state = TrafficRequestEpoch()
        val request = state.begin()!!
        var published = false
        var retry = false
        val job = launch {
            try {
                withContext(NonCancellable) { delay(1_000) }
                if (state.accepts(request)) published = true
            } finally { retry = state.finish(request) }
        }
        runCurrent()
        state.invalidate()
        advanceUntilIdle()
        job.join()
        assertFalse(published)
        assertTrue(retry)
        assertFalse(state.inFlight())
    }
    @Test fun concurrentAcquisitionHasExactlyOneOwner() {
        val state = TrafficRequestEpoch()
        val gate = java.util.concurrent.CountDownLatch(1)
        val winners = java.util.Collections.synchronizedList(mutableListOf<TrafficRequestEpoch.Ticket>())
        val workers = List(8) {
            kotlin.concurrent.thread {
                gate.await()
                state.begin()?.let { winners.add(it) }
            }
        }
        gate.countDown()
        workers.forEach { it.join(5_000) }
        assertTrue(workers.none { it.isAlive })
        assertEquals(1, winners.size)
        assertTrue(state.accepts(winners.single()))
    }
}
