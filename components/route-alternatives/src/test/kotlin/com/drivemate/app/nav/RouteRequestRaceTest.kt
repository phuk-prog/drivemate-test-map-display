package com.drivemate.app.nav
import com.drivemate.app.controller.LatestRouteRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RouteRequestRaceTest {
    @Test fun oldNonCancellableProviderCannotPublishAfterANewerRequest() = runTest {
        val owner=LatestRouteRequest(this);val output=mutableListOf<String>()
        owner.launch { ticket->withContext(NonCancellable){delay(100);if(ticket.isCurrent())output+="old"} };runCurrent()
        owner.launch { ticket->delay(10);if(ticket.isCurrent())output+="new" };advanceUntilIdle()
        assertEquals(listOf("new"),output)
    }
    @Test fun cancellingDestinationInvalidatesLateFallbackSuggestions() = runTest {
        val owner=LatestRouteRequest(this);var published=false
        owner.launch { ticket->withContext(NonCancellable){delay(100);if(ticket.isCurrent())published=true} };runCurrent()
        owner.cancel();advanceUntilIdle();assertFalse(published)
    }
    @Test fun oldFinallyCannotClearTheNewRequestsBusyState() = runTest {
        val owner=LatestRouteRequest(this);var busy=false
        owner.launch { ticket->try {withContext(NonCancellable){delay(100)}} finally {if(ticket.isCurrent())busy=false} };runCurrent()
        owner.launch {ticket->busy=true;delay(1000);if(ticket.isCurrent())busy=false};runCurrent()
        advanceTimeBy(101);runCurrent();assertTrue(busy);advanceUntilIdle();assertFalse(busy)
    }
    @Test fun aNewPlanningTicketCannotInheritTheOldTicketAuthority() = runTest {
        val owner=LatestRouteRequest(this);var old:LatestRouteRequest.Ticket?=null;var newer:LatestRouteRequest.Ticket?=null
        owner.launch {old=it};runCurrent();owner.launch {newer=it};runCurrent()
        assertFalse(old!!.isCurrent());assertTrue(newer!!.isCurrent());owner.cancel();assertFalse(newer!!.isCurrent())
    }
}
