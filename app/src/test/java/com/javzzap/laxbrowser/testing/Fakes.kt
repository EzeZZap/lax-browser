package com.javzzap.laxbrowser.testing

import com.javzzap.laxbrowser.data.BrowserRepository
import com.javzzap.laxbrowser.data.HistoryStore
import com.javzzap.laxbrowser.domain.HistoryEntry
import com.javzzap.laxbrowser.domain.HistoryPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import com.javzzap.laxbrowser.domain.BrowserData
import com.javzzap.laxbrowser.domain.BrowserOperations
import com.javzzap.laxbrowser.domain.BrowserState
import com.javzzap.laxbrowser.domain.Clock
import com.javzzap.laxbrowser.domain.DataDiff
import com.javzzap.laxbrowser.domain.EngineCommand
import com.javzzap.laxbrowser.domain.IdGenerator
import com.javzzap.laxbrowser.domain.Reduction
import com.javzzap.laxbrowser.domain.TabKind
import com.javzzap.laxbrowser.engine.BrowserEngine
import com.javzzap.laxbrowser.engine.EngineListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeClock(var nowMs: Long = 1_000_000L) : Clock {
    override fun now(): Long = nowMs
    fun advance(ms: Long) {
        nowMs += ms
    }
}

class SequentialIds(private val prefix: String = "id") : IdGenerator {
    private var next = 0
    override fun newId(): String = "$prefix-${next++}"
}

/** Applies diffs to an in-memory [BrowserData], mirroring the Room repository's semantics. */
class InMemoryRepository(var data: BrowserData = BrowserData()) : BrowserRepository {
    val sessionStates = HashMap<String, String>()
    var applyCount = 0

    override suspend fun load(): BrowserData = data

    override suspend fun apply(diff: DataDiff) {
        if (diff.isEmpty) return
        applyCount++
        data = BrowserData(
            containers = merge(data.containers, diff.upsertContainers, diff.deleteContainerIds) { it.id },
            spaces = merge(data.spaces, diff.upsertSpaces, diff.deleteSpaceIds) { it.id },
            tabs = merge(data.tabs, diff.upsertTabs, diff.deleteTabIds) { it.id },
            tabFolders = merge(data.tabFolders, diff.upsertTabFolders, diff.deleteTabFolderIds) { it.id },
            bookmarks = merge(data.bookmarks, diff.upsertBookmarks, diff.deleteBookmarkIds) { it.id },
            bookmarkFolders = merge(data.bookmarkFolders, diff.upsertBookmarkFolders, diff.deleteBookmarkFolderIds) { it.id },
            shortcuts = merge(data.shortcuts, diff.upsertShortcuts, diff.deleteShortcutIds) { it.id },
            webApps = merge(data.webApps, diff.upsertWebApps, diff.deleteWebAppIds) { it.id },
            siteSettings = merge(data.siteSettings, diff.upsertSiteSettings, diff.deleteSiteHosts) { it.host },
            settings = diff.settings ?: data.settings,
        )
        diff.discardSessionStateTabIds.forEach { sessionStates.remove(it) }
    }

    override suspend fun replaceAll(data: BrowserData) {
        this.data = data
        sessionStates.clear()
    }

    override suspend fun loadSessionState(tabId: String): String? = sessionStates[tabId]

    override suspend fun saveSessionStates(states: Map<String, String>, now: Long) {
        sessionStates.putAll(states)
    }

    private fun <T> merge(old: List<T>, upserts: List<T>, deletes: List<String>, id: (T) -> String): List<T> {
        val byId = LinkedHashMap<String, T>()
        old.forEach { byId[id(it)] = it }
        deletes.forEach { byId.remove(it) }
        upserts.forEach { byId[id(it)] = it }
        return byId.values.toList()
    }
}

/** Keeps visits in memory with the same duplicate rule as the Room store. */
class FakeHistoryStore : HistoryStore {
    val entries = mutableListOf<HistoryEntry>()
    var clearedSince: Long? = null
    private var nextId = 1L

    override suspend fun recordVisit(url: String, title: String, at: Long) {
        val last = entries.lastOrNull { it.url == url }
        if (last != null && at - last.visitedAt in 0..HistoryPolicy.DUPLICATE_WINDOW_MS) {
            entries[entries.indexOf(last)] = last.copy(visitedAt = at)
        } else {
            entries += HistoryEntry(nextId++, url, title, null, at)
        }
    }

    override suspend fun updateTitle(url: String, title: String) {
        val last = entries.lastOrNull { it.url == url } ?: return
        entries[entries.indexOf(last)] = last.copy(title = title)
    }

    override suspend fun updateFavicon(url: String, iconUrl: String) {
        val last = entries.lastOrNull { it.url == url } ?: return
        entries[entries.indexOf(last)] = last.copy(faviconUrl = iconUrl)
    }

    override fun observe(query: String, limit: Int): Flow<List<HistoryEntry>> = flowOf(entries.toList())
    override suspend fun visited(urls: List<String>): Set<String> = entries.map { it.url }.filter { it in urls }.toSet()
    override suspend fun delete(id: Long) {
        entries.removeAll { it.id == id }
    }

    override suspend fun clearSince(since: Long) {
        clearedSince = since
        entries.removeAll { it.visitedAt >= since }
    }
}

/** Records engine calls and mirrors reconcile's session bookkeeping without Gecko. */
class FakeEngine : BrowserEngine {
    override var listener: EngineListener? = null
    private val generation = MutableStateFlow(0)
    override val sessionGeneration: StateFlow<Int> = generation

    /** tabId -> containerId of the open session. */
    val sessions = LinkedHashMap<String, String>()
    var activeTabId: String? = null
    val commands = mutableListOf<EngineCommand>()
    val closed = mutableListOf<String>()
    val opened = mutableListOf<Pair<String, String>>()

    override fun reconcile(state: BrowserState) {
        val desired = state.allTabs
            .filter { it.id !in state.quarantinedTabIds && it.kind == TabKind.WEB && state.runtime(it.id).loaded && !state.isBlocked(it) }
            .associateBy { it.id }
        for ((id, container) in sessions.toList()) {
            val tab = desired[id]
            if (tab == null || tab.containerId != container) {
                sessions.remove(id)
                closed += id
            }
        }
        for (tab in desired.values) {
            if (tab.id !in sessions) {
                sessions[tab.id] = tab.containerId
                opened += tab.id to tab.containerId
            }
        }
        activeTabId = state.activeTab?.id?.takeIf { it in sessions }
    }

    override fun execute(command: EngineCommand) {
        commands += command
    }

    override fun closeSession(tabId: String): Boolean {
        if (sessions.remove(tabId) != null) closed += tabId
        return true
    }

    override fun hasSession(tabId: String): Boolean = tabId in sessions
}

/** Runs a chain of operations, failing loudly on rejection. */
fun BrowserState.then(op: (BrowserState) -> Reduction): BrowserState {
    val reduction = op(this)
    check(!reduction.isRejected) { "Operation rejected: ${reduction.outcome}" }
    return reduction.state
}

fun newOps(clock: Clock = FakeClock(), ids: IdGenerator = SequentialIds()) = BrowserOperations(clock, ids)

/** A loaded state with the default Space and container. */
fun initialState(ops: BrowserOperations): BrowserState = ops.ensureDefaults(BrowserState(isLoaded = true)).state
