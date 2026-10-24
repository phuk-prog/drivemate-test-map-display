package com.drivemate.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** One list a place search reads (the phone's postcode and place list, its business list), with how long we'll wait for it. */
class SearchSource(
    val name: String,
    val deadlineMs: Long,
    /** False for lists kept on the phone: they don't show whether the internet works. */
    val online: Boolean = true,
    val fetch: suspend () -> List<Place>,
)

/** What one source gave: places, an error, or nothing in time ([result] null). */
class SourceOutcome(val name: String, val online: Boolean, val result: Result<List<Place>>?) {
    val places: List<Place> get() = result?.getOrNull().orEmpty()
    val failed: Boolean get() = result == null || result.isFailure
}

/**
 * Asks every search source at the same time. As each one answers, [run]'s callback gets every
 * answer so far, so the first good results can be shown at once and the rest merged in. A source
 * that hasn't answered by its deadline is left out (its answer, if it comes later, still fills the
 * cache for the next letter typed). Blocking network calls run outside the caller, so typing on
 * cancels the wait immediately instead of waiting for a slow server.
 */
object ParallelSearch {
    internal val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val networkSlots = Semaphore(4)

    suspend fun run(sources: List<SearchSource>, onArrive: suspend (List<SourceOutcome>) -> Unit = {}): List<SourceOutcome> = coroutineScope {
        if (sources.isEmpty()) return@coroutineScope emptyList()
        val arrivals = Channel<SourceOutcome>(Channel.UNLIMITED)
        for (s in sources) launch {
            // Local index scans belong to this query: typing on cancels them, including
            // their keepGoing check. Network requests retain their existing shared cache life.
            val fetch: suspend () -> Result<List<Place>> = {
                try { Result.success(s.fetch()) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { Result.failure(e) }
            }
            val work = if (s.online) background.async { networkSlots.withPermit { fetch() } }
                else async(Dispatchers.IO) { fetch() }
            val result = try {
                withTimeoutOrNull(s.deadlineMs) {
                    try {
                        work.await()
                    } catch (e: CancellationException) {
                        // Our own wait was cancelled (typed on): stop. The source's own failure isn't ours.
                        if (!work.isCancelled) throw e
                        Result.failure(e)
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }
            } finally {
                // Cancelling the wrapper drops obsolete queued waits. A cached request that
                // already started remains owned by RequestCache and can still fill its cache.
                work.cancel()
            }
            arrivals.send(SourceOutcome(s.name, s.online, result))
        }
        val got = ArrayList<SourceOutcome>(sources.size)
        repeat(sources.size) {
            got += arrivals.receive()
            onArrive(got.toList())
        }
        got
    }
}

/**
 * Recent answers from free sources, kept in memory only for a few minutes, so typing a letter
 * and deleting it, or the second (TomTom) pass, doesn't ask the same server again. A request
 * still on its way is shared rather than sent twice. Failed answers are forgotten at once.
 * Active fetches are bounded; queued work with no subscribers is cancelled before contacting
 * a provider, while a fetch already started may finish for the next subscriber/cache hit.
 */
class RequestCache(
    private val max: Int = 200,
    private val ttlMs: Long = 10 * 60_000L,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val scope: CoroutineScope = ParallelSearch.background,
    maxConcurrent: Int = 4,
) {
    private class Entry(val createdAt: Long) {
        lateinit var request: Deferred<List<Place>>
        var subscribers = 0
        var started = false
    }
    private val slots = Semaphore(maxConcurrent.coerceAtLeast(1))
    private val map = object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>) = size > max
    }

    suspend fun get(key: String, fetch: suspend () -> List<Place>): List<Place> {
        val entry = synchronized(map) {
            val existing = map[key]?.takeIf { now() - it.createdAt < ttlMs && !it.request.isCancelled }
            (existing ?: Entry(now()).also { e ->
                // Lazy start ensures the entry and its first subscriber exist before admission.
                e.request = scope.async(start = CoroutineStart.LAZY) {
                    slots.withPermit {
                        synchronized(map) {
                            if (e.subscribers == 0) throw CancellationException("Obsolete queued search")
                            e.started = true
                        }
                        fetch()
                    }
                }
                map[key] = e
                e.request.invokeOnCompletion { error ->
                    if (error != null) synchronized(map) { if (map[key] === e) map.remove(key) }
                }
            }).also { it.subscribers++ }
        }
        try {
            return entry.request.await()
        } finally {
            synchronized(map) {
                entry.subscribers--
                if (entry.subscribers == 0 && !entry.started) entry.request.cancel()
            }
        }
    }

    fun clear() = synchronized(map) {
        map.values.filter { !it.started }.toList().forEach { it.request.cancel() }
        map.clear()
    }
}
