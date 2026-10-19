package com.javzzap.laxbrowser.data

import com.javzzap.laxbrowser.domain.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Caches serialized Gecko session state and writes it to the database with a debounce, because
 * Gecko reports state changes very frequently (scrolling, form input). Main-thread only.
 */
class SessionStateStore(
    private val repository: BrowserRepository,
    private val queue: PersistenceQueue,
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val debounceMs: Long = 2_000L,
) {
    private val cache = HashMap<String, String>()
    private val dirty = HashSet<String>()
    private var flushJob: Job? = null

    fun update(tabId: String, state: String) {
        cache[tabId] = state
        dirty += tabId
        if (flushJob?.isActive != true) {
            flushJob = scope.launch {
                delay(debounceMs)
                flushNow()
            }
        }
    }

    /** The latest state for [tabId], from memory or the database. */
    suspend fun get(tabId: String): String? {
        cache[tabId]?.let { return it }
        return runCatching { repository.loadSessionState(tabId) }.getOrNull()
    }

    /** The cached state for [tabId], without touching the database. */
    fun peek(tabId: String): String? = cache[tabId]

    /** Forgets cached state. The database rows are removed by the same diff that triggered this. */
    fun discard(tabIds: Set<String>) {
        cache.keys.removeAll(tabIds)
        dirty.removeAll(tabIds)
    }

    /** Forgets every cached state, for example after a backup replaced all tabs. */
    fun discardAll() {
        flushJob?.cancel()
        flushJob = null
        cache.clear()
        dirty.clear()
    }

    fun flushNow() {
        flushJob?.cancel()
        flushJob = null
        if (dirty.isEmpty()) return
        val snapshot = dirty.associateWith { cache.getValue(it) }
        dirty.clear()
        val now = clock.now()
        queue.enqueue { repository.saveSessionStates(snapshot, now) }
    }
}
