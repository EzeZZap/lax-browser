package com.javzzap.laxbrowser.state

import android.util.Log
import com.javzzap.laxbrowser.data.BrowserRepository
import com.javzzap.laxbrowser.data.HistoryStore
import com.javzzap.laxbrowser.domain.UrlInput
import com.javzzap.laxbrowser.data.PersistenceQueue
import com.javzzap.laxbrowser.data.SessionStateStore
import com.javzzap.laxbrowser.domain.AutoUnload
import com.javzzap.laxbrowser.domain.BookmarkHtml
import com.javzzap.laxbrowser.domain.BrowserData
import com.javzzap.laxbrowser.domain.ImportSummary
import com.javzzap.laxbrowser.domain.LinkTarget
import com.javzzap.laxbrowser.domain.OnboardingChoices
import com.javzzap.laxbrowser.domain.RecentlyClosedStore
import com.javzzap.laxbrowser.domain.SiteRules
import kotlinx.coroutines.delay
import com.javzzap.laxbrowser.domain.BrowserOperations
import com.javzzap.laxbrowser.domain.BrowserSettings
import com.javzzap.laxbrowser.domain.BrowserState
import com.javzzap.laxbrowser.domain.Clock
import com.javzzap.laxbrowser.domain.ClosedTab
import com.javzzap.laxbrowser.domain.ContainerChoice
import com.javzzap.laxbrowser.domain.ContextMenuTarget
import com.javzzap.laxbrowser.domain.DataDiff
import com.javzzap.laxbrowser.domain.Outcome
import com.javzzap.laxbrowser.domain.Reduction
import com.javzzap.laxbrowser.domain.RejectReason
import com.javzzap.laxbrowser.domain.Restoration
import com.javzzap.laxbrowser.domain.TabRuntime
import com.javzzap.laxbrowser.domain.WebApp
import com.javzzap.laxbrowser.domain.WebAppManifest
import com.javzzap.laxbrowser.engine.BrowserEngine
import com.javzzap.laxbrowser.engine.EngineListener
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the single observable [BrowserState]. Every change goes through [dispatch]: a pure
 * operation produces the next state, the persisted part is diffed and written in one transaction
 * on a serial background queue, and the engine is reconciled. Main-thread only.
 */
class BrowserController(
    private val repository: BrowserRepository,
    private val engine: BrowserEngine,
    private val sessionStates: SessionStateStore,
    private val queue: PersistenceQueue,
    private val clock: Clock,
    private val ops: BrowserOperations,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    val history: HistoryStore = HistoryStore.None,
    private val closedTabStore: RecentlyClosedStore = RecentlyClosedStore.None,
) : EngineListener {

    private val mutableState = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = mutableState.asStateFlow()

    private val mutableRecentlyClosed = MutableStateFlow<List<ClosedTab>>(emptyList())

    /** Recently closed tabs, newest first; kept across restarts by [closedTabStore]. */
    val recentlyClosed: StateFlow<List<ClosedTab>> = mutableRecentlyClosed.asStateFlow()

    private fun setRecentlyClosed(tabs: List<ClosedTab>) {
        mutableRecentlyClosed.value = tabs
        scope.launch { closedTabStore.save(tabs) }
    }

    /** Forgets recently closed tabs (for example when history is cleared). */
    fun clearRecentlyClosed() = setRecentlyClosed(emptyList())

    private val mutableContextMenu = MutableStateFlow<ContextMenuTarget?>(null)
    val contextMenu: StateFlow<ContextMenuTarget?> = mutableContextMenu.asStateFlow()

    private var started = false
    private var pendingForegroundAt: Long? = null

    /** When each tab left the screen, for automatic unloading. In memory only. */
    private val hiddenSince = HashMap<String, Long>()

    /** Tabs automatic unloading must keep (for example the one playing media). Set by the app. */
    var protectedTabIds: () -> Set<String> = { emptySet() }

    init {
        engine.listener = this
    }

    fun start() {
        if (started) return
        started = true
        scope.launch {
            val data = withContext(ioDispatcher) {
                try {
                    repository.load()
                } catch (e: Exception) {
                    Log.e(TAG, "Could not read browser data; starting empty without overwriting it", e)
                    BrowserData()
                }
            }
            val restored = Restoration.restore(data)
            mutableState.value = restored
            // Closed tabs whose ID is open again (for example after restoring a backup) are dropped.
            val openIds = restored.data.tabs.map { it.id }.toSet()
            mutableRecentlyClosed.value = closedTabStore.load().filter { it.tab.id !in openIds }.take(MAX_RECENTLY_CLOSED)
            val cleanup = DataDiff.between(data, restored.data)
            if (!cleanup.isEmpty) queue.enqueue { repository.apply(cleanup) }
            dispatch { ops.ensureDefaults(it) }
            pendingForegroundAt?.let { at ->
                pendingForegroundAt = null
                dispatch { ops.onForeground(it, at) }
            }
        }
    }

    /** Checks every minute for tabs to unload automatically. */
    fun startAutoUnload() {
        scope.launch {
            while (true) {
                delay(AUTO_UNLOAD_CHECK_MS)
                unloadInactiveTabs()
            }
        }
    }

    /** Unloads tabs that have been off screen longer than the user's setting. */
    fun unloadInactiveTabs(now: Long = clock.now()) {
        val current = mutableState.value
        if (!current.isLoaded) return
        val candidates = AutoUnload.candidates(current, hiddenSince, now, current.data.settings.autoUnloadMinutes, protectedTabIds())
        if (candidates.isEmpty()) return
        dispatch { state -> candidates.fold(Reduction(state)) { acc, id -> ops.unloadTab(acc.state, id).takeUnless { it.isRejected } ?: acc } }
    }

    private fun trackVisibility(old: BrowserState, new: BrowserState) {
        val before = old.visibleTabIds
        val after = new.visibleTabIds
        if (before == after) return
        val now = clock.now()
        (before - after).forEach { hiddenSince[it] = now }
        after.forEach { hiddenSince.remove(it) }
    }

    /**
     * Replaces browser data with a restored backup. Open pages close; installed web apps are kept.
     * Called after the user confirmed that current data will be replaced.
     */
    suspend fun replaceData(restored: BrowserData) {
        val current = mutableState.value
        val data = restored.copy(webApps = current.data.webApps)
        // Close every session before the data they belong to disappears.
        engine.reconcile(BrowserState(isLoaded = true))
        withContext(ioDispatcher) { repository.replaceAll(data) }
        sessionStates.discardAll()
        hiddenSince.clear()
        setRecentlyClosed(emptyList())
        mutableState.value = Restoration.restore(data)
        dispatch { ops.ensureDefaults(it) }
    }

    fun dispatch(reconcile: Boolean = true, operation: (BrowserState) -> Reduction): Reduction {
        val old = mutableState.value
        if (!old.isLoaded) return Reduction.reject(old, RejectReason.NOT_ALLOWED)
        val reduction = operation(old)
        if (reduction.isRejected) return reduction
        val new = reduction.state
        mutableState.value = new
        trackVisibility(old, new)
        if (reduction.discardSessionState.isNotEmpty()) sessionStates.discard(reduction.discardSessionState)
        if (new.data !== old.data || reduction.discardSessionState.isNotEmpty()) {
            val diff = DataDiff.between(old.data, new.data, reduction.discardSessionState)
            if (!diff.isEmpty) queue.enqueue { repository.apply(diff) }
        }
        if (reconcile) engine.reconcile(new)
        reduction.commands.forEach(engine::execute)
        return reduction
    }

    // region Lifecycle

    fun onForeground() {
        val now = clock.now()
        if (!mutableState.value.isLoaded) {
            pendingForegroundAt = now
            return
        }
        dispatch { ops.onForeground(it, now) }
    }

    fun onBackground() {
        sessionStates.flushNow()
        if (!mutableState.value.isLoaded) return
        dispatch(reconcile = false) { ops.onBackground(it, clock.now()) }
    }

    // endregion

    // region Tabs

    fun newTab(url: String? = null) = dispatch { ops.newTab(it, url) }
    fun openNewTabPage() = dispatch { ops.openNewTabPage(it) }
    fun navigate(tabId: String?, input: String) = dispatch { ops.navigate(it, tabId, input) }
    fun openUrl(url: String) = dispatch { ops.openUrlInActiveSpace(it, url) }
    fun selectTab(tabId: String) = dispatch { ops.selectTab(it, tabId) }
    fun unloadTab(tabId: String) = dispatch { ops.unloadTab(it, tabId) }
    fun pinTab(tabId: String) = dispatch { ops.pinTab(it, tabId) }
    fun unpinTab(tabId: String) = dispatch { ops.unpinTab(it, tabId) }
    fun addToEssentials(tabId: String) = dispatch { ops.addToEssentials(it, tabId) }
    fun moveTab(tabId: String, delta: Int) = dispatch(reconcile = false) { ops.moveTab(it, tabId, delta) }
    fun moveTabToSpace(tabId: String, spaceId: String) = dispatch { ops.moveTabToSpace(it, tabId, spaceId) }
    fun goBack(tabId: String) = dispatch(reconcile = false) { ops.goBack(it, tabId) }
    fun goForward(tabId: String) = dispatch(reconcile = false) { ops.goForward(it, tabId) }
    fun reload(tabId: String) = dispatch { ops.reload(it, tabId) }
    fun stop(tabId: String) = dispatch(reconcile = false) { ops.stop(it, tabId) }

    fun openLinkInNewTab(url: String, background: Boolean) = dispatch { ops.openLinkInNewTab(it, url, background) }
    fun setPrivateMode(enabled: Boolean) = dispatch { ops.setPrivateMode(it, enabled) }

    /** Closes every private tab; their sessions close and Gecko discards the private data. */
    fun closeAllPrivateTabs() = dispatch { ops.closeAllPrivateTabs(it) }
    fun toggleDesktopMode(tabId: String) = dispatch { ops.toggleDesktopMode(it, tabId) }
    fun loadUrl(tabId: String?, url: String) = dispatch { ops.loadUrl(it, tabId, url) }
    fun openSplit(tabId: String) = dispatch { ops.openSplit(it, tabId) }
    fun openUrlInSplit(url: String) = dispatch { ops.openUrlInSplit(it, url) }
    fun closeSplit() = dispatch { ops.closeSplit(it) }
    fun swapSplit() = dispatch { ops.swapSplit(it) }
    fun resetPinnedTab(tabId: String) = dispatch { ops.resetPinnedTab(it, tabId) }
    fun setPinnedUrl(tabId: String, url: String?) = dispatch(reconcile = false) { ops.setPinnedUrl(it, tabId, url) }
    fun createTabFolder(name: String) = dispatch(reconcile = false) { ops.createTabFolder(it, name) }
    fun renameTabFolder(id: String, name: String) = dispatch(reconcile = false) { ops.renameTabFolder(it, id, name) }
    fun setTabFolderCollapsed(id: String, collapsed: Boolean) = dispatch(reconcile = false) { ops.setTabFolderCollapsed(it, id, collapsed) }
    fun moveTabFolder(id: String, delta: Int) = dispatch(reconcile = false) { ops.moveTabFolder(it, id, delta) }
    fun deleteTabFolder(id: String) = dispatch(reconcile = false) { ops.deleteTabFolder(it, id) }
    fun moveTabToFolder(tabId: String, folderId: String?) = dispatch(reconcile = false) { ops.moveTabToFolder(it, tabId, folderId) }
    fun setSpaceEmoji(spaceId: String, emoji: String?) = dispatch(reconcile = false) { ops.setSpaceEmoji(it, spaceId, emoji) }
    fun switchSpaceBy(delta: Int) = dispatch { ops.switchSpaceBy(it, delta) }
    fun setSiteZoom(url: String, percent: Int) = dispatch(reconcile = false) { ops.setSiteZoom(it, url, percent) }
    fun setSiteSpace(host: String, spaceId: String?) = dispatch(reconcile = false) { ops.setSiteSpace(it, host, spaceId) }
    fun removeSiteSetting(host: String) = dispatch(reconcile = false) { ops.removeSiteSetting(it, host) }

    fun importBookmarks(nodes: List<BookmarkHtml.Node>, folderName: String): ImportSummary? {
        var summary: ImportSummary? = null
        val reduction = dispatch(reconcile = false) { state -> ops.importBookmarks(state, nodes, folderName).also { summary = it.second }.first }
        return summary.takeUnless { reduction.isRejected }
    }
    fun updateSettings(transform: (BrowserSettings) -> BrowserSettings) = dispatch { ops.updateSettings(it, transform) }
    fun applyOnboarding(choices: OnboardingChoices) = dispatch { ops.applyOnboarding(it, choices) }
    fun openLinkIn(url: String, target: LinkTarget, applyRules: Boolean) = dispatch { ops.openLinkIn(it, url, target, applyRules) }
    fun dismissContextMenu() {
        mutableContextMenu.value = null
    }

    /**
     * Closes the engine session first; the record is removed only once that succeeded. Closed
     * ordinary and pinned tabs can be reopened with [reopenClosedTab].
     */
    fun closeTab(tabId: String): Reduction {
        val tab = mutableState.value.tab(tabId) ?: return Reduction.reject(mutableState.value, RejectReason.NOT_FOUND)
        val closed = if (tab.isEssential || tab.isPrivate) null else ClosedTab(tab, sessionStates.peek(tab.id))
        if (!engine.closeSession(tab.id)) return Reduction.reject(mutableState.value, RejectReason.NOT_ALLOWED)
        val reduction = dispatch { ops.closeTab(it, tabId) }
        if (!reduction.isRejected && closed != null) {
            setRecentlyClosed((listOf(closed) + mutableRecentlyClosed.value.filter { it.tab.id != tab.id }).take(MAX_RECENTLY_CLOSED))
        }
        return reduction
    }

    /** Reopens [closed] (the most recently closed tab by default) with its saved history. */
    fun reopenClosedTab(closed: ClosedTab? = mutableRecentlyClosed.value.firstOrNull()): Reduction {
        closed ?: return Reduction.reject(mutableState.value, RejectReason.NOT_FOUND)
        closed.sessionState?.let { sessionStates.update(closed.tab.id, it) }
        val reduction = dispatch { ops.restoreClosedTab(it, closed.tab) }
        if (reduction.isRejected) {
            sessionStates.discard(setOf(closed.tab.id))
        } else {
            setRecentlyClosed(mutableRecentlyClosed.value.filter { it.tab.id != closed.tab.id })
        }
        return reduction
    }

    fun removeEssential(tabId: String): Reduction {
        if (!engine.closeSession(tabId)) return Reduction.reject(mutableState.value, RejectReason.NOT_ALLOWED)
        return dispatch { ops.removeEssential(it, tabId) }
    }

    // endregion

    // region Spaces and containers

    fun createSpace(name: String, color: Int, choice: ContainerChoice) = dispatch { ops.createSpace(it, name, color, choice) }
    fun renameSpace(spaceId: String, name: String) = dispatch(reconcile = false) { ops.renameSpace(it, spaceId, name) }
    fun setSpaceColor(spaceId: String, color: Int) = dispatch(reconcile = false) { ops.setSpaceColor(it, spaceId, color) }
    fun moveSpace(spaceId: String, delta: Int) = dispatch(reconcile = false) { ops.moveSpace(it, spaceId, delta) }
    fun switchSpace(spaceId: String) = dispatch { ops.switchSpace(it, spaceId) }
    fun deleteSpace(spaceId: String) = dispatch { ops.deleteSpace(it, spaceId) }
    fun setSpaceContainer(spaceId: String, containerId: String) = dispatch { ops.setSpaceContainer(it, spaceId, containerId) }
    fun createContainer(name: String, color: Int) = dispatch(reconcile = false) { ops.createContainer(it, name, color) }
    fun renameContainer(id: String, name: String, color: Int?) = dispatch(reconcile = false) { ops.renameContainer(it, id, name, color) }
    fun containerUsage(id: String) = ops.containerUsage(mutableState.value, id)
    fun deleteContainer(id: String) = dispatch { ops.deleteContainer(it, id) }

    // endregion

    // region Bookmarks, shortcuts, web apps

    fun addBookmark(url: String, title: String?, folderId: String? = null, faviconUrl: String? = null) =
        dispatch(reconcile = false) { ops.addBookmark(it, url, title, folderId, faviconUrl) }
    fun updateBookmark(id: String, title: String, url: String, folderId: String?) =
        dispatch(reconcile = false) { ops.updateBookmark(it, id, title, url, folderId) }
    fun moveBookmarkToFolder(id: String, folderId: String?) = dispatch(reconcile = false) { ops.moveBookmarkToFolder(it, id, folderId) }
    fun createBookmarkFolder(name: String, parentId: String?) = dispatch(reconcile = false) { ops.createBookmarkFolder(it, name, parentId) }
    fun renameBookmarkFolder(id: String, name: String) = dispatch(reconcile = false) { ops.renameBookmarkFolder(it, id, name) }
    fun moveBookmarkFolder(id: String, parentId: String?) = dispatch(reconcile = false) { ops.moveBookmarkFolder(it, id, parentId) }
    fun deleteBookmarkFolder(id: String) = dispatch(reconcile = false) { ops.deleteBookmarkFolder(it, id) }
    fun folderContents(id: String) = ops.folderContents(mutableState.value, id)
    fun removeBookmark(id: String) = dispatch(reconcile = false) { ops.removeBookmark(it, id) }
    fun openBookmark(id: String) = dispatch { ops.openBookmark(it, id) }
    fun bookmarkFor(url: String) = ops.bookmarkFor(mutableState.value, url)

    fun addShortcut(url: String, title: String?, iconUrl: String? = null) = dispatch(reconcile = false) { ops.addShortcut(it, url, title, iconUrl) }
    fun updateShortcut(id: String, title: String, url: String) = dispatch(reconcile = false) { ops.updateShortcut(it, id, title, url) }
    fun removeShortcut(id: String) = dispatch(reconcile = false) { ops.removeShortcut(it, id) }
    fun moveShortcut(id: String, delta: Int) = dispatch(reconcile = false) { ops.moveShortcut(it, id, delta) }
    fun openShortcut(id: String) = dispatch { ops.openShortcut(it, id) }

    fun upsertWebApp(app: WebApp) = dispatch(reconcile = false) { ops.upsertWebApp(it, app) }
    fun removeWebApp(id: String) = dispatch(reconcile = false) { ops.removeWebApp(it, id) }

    /** Resolves after every pending write has reached the database. */
    suspend fun awaitPersistence() = queue.awaitIdle()

    // endregion

    // region EngineListener

    override fun onLocationChange(tabId: String, url: String?) {
        dispatch(reconcile = false) { ops.onLocationChange(it, tabId, url) }
    }

    override fun onTitleChange(tabId: String, title: String?) {
        dispatch(reconcile = false) { ops.onTitleChange(it, tabId, title) }
        val tab = mutableState.value.tab(tabId)?.takeIf { !it.isPrivate } ?: return
        if (!title.isNullOrBlank() && UrlInput.isWebUrl(tab.url)) writeHistory { history.updateTitle(tab.url, title.trim()) }
    }

    override fun onVisited(tabId: String, url: String) {
        val tab = mutableState.value.tab(tabId)?.takeIf { !it.isPrivate } ?: return
        val at = clock.now()
        writeHistory { history.recordVisit(url, if (tab.url == url) tab.title else "", at) }
    }

    /** History writes run off the main thread; a failed write never affects browsing. */
    private fun writeHistory(block: suspend () -> Unit) {
        scope.launch(ioDispatcher) {
            try {
                block()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w(TAG, "History write failed")
            }
        }
    }

    /** Deletes history visited at or after [since] (0 for all history). */
    /** Clears history since [since]; recently closed tabs used in that time are forgotten too. */
    fun clearHistory(since: Long) {
        writeHistory { history.clearSince(since) }
        val kept = mutableRecentlyClosed.value.filter { it.tab.lastAccessedAt < since }
        if (kept.size != mutableRecentlyClosed.value.size) setRecentlyClosed(kept)
    }

    fun deleteHistoryEntry(id: Long) = writeHistory { history.delete(id) }

    override fun onRuntimeChange(tabId: String, update: (TabRuntime) -> TabRuntime) {
        dispatch(reconcile = false) { ops.onRuntimeChange(it, tabId, update) }
    }

    override fun onManifest(tabId: String, manifest: WebAppManifest?) {
        dispatch(reconcile = false) { ops.onRuntimeChange(it, tabId) { r -> r.copy(manifest = manifest) } }
    }

    override fun onSessionStateChange(tabId: String, state: String) {
        val current = mutableState.value
        val tab = current.tab(tabId) ?: return
        // Private tabs never have their page history saved.
        if (!tab.isPrivate && current.runtime(tabId).loaded) sessionStates.update(tabId, state)
    }

    override fun onSessionLost(tabId: String, crashed: Boolean) {
        dispatch { ops.onSessionLost(it, tabId, crashed) }
        // A killed (not crashed) background process: reload the visible tab from its saved state.
        if (!crashed && mutableState.value.activeTab?.id == tabId) selectTab(tabId)
    }

    override fun onCloseRequest(tabId: String) {
        val tab = mutableState.value.tab(tabId) ?: return
        if (!tab.isPinned && !tab.isEssential) closeTab(tabId)
    }

    override fun contentTabContainer(openerTabId: String): String? =
        ops.contentTabContainer(mutableState.value, openerTabId)

    override fun onContentOpenedTab(newTabId: String, openerTabId: String, url: String) {
        dispatch { ops.onContentOpenedTab(it, newTabId, openerTabId, url) }
    }

    override fun onOpenInNewTab(url: String) {
        dispatch { ops.newTab(it, url) }
    }

    override fun onFavicon(tabId: String, pageUrl: String, iconUrl: String) {
        dispatch(reconcile = false) { ops.onFavicon(it, tabId, pageUrl, iconUrl) }
        val isPrivate = mutableState.value.tab(tabId)?.isPrivate != false
        if (!isPrivate && UrlInput.isWebUrl(iconUrl)) writeHistory { history.updateFavicon(pageUrl, iconUrl) }
    }

    override fun onContextMenu(target: ContextMenuTarget) {
        if (mutableState.value.activeTab?.id == target.tabId) mutableContextMenu.value = target
    }

    override fun extensionTabContainer(): String? = ops.extensionTabContainer(mutableState.value)

    override fun onExtensionOpenedTab(newTabId: String, url: String, select: Boolean) {
        dispatch { ops.onExtensionOpenedTab(it, newTabId, url, select) }
    }

    override fun onExtensionSelectTab(tabId: String) {
        val tab = mutableState.value.tab(tabId) ?: return
        if (tab.isPrivate == mutableState.value.privateMode) selectTab(tabId)
    }

    override fun zoomFor(url: String): Int = SiteRules.zoomFor(mutableState.value, url)

    override fun linkRuleSpace(tabId: String, url: String): String? = ops.linkRuleSpace(mutableState.value, tabId, url)

    override fun openInSpace(spaceId: String, url: String) {
        dispatch { ops.openInSpace(it, spaceId, url) }
    }

    override fun glanceContainer(openerTabId: String): String? = ops.glanceContainer(mutableState.value, openerTabId)

    override fun onGlancePromoted(newTabId: String, openerTabId: String, url: String): Boolean =
        !dispatch { ops.onGlancePromoted(it, newTabId, openerTabId, url) }.isRejected

    override fun onExtensionCloseTab(tabId: String): Boolean {
        val tab = mutableState.value.tab(tabId) ?: return false
        if (tab.isPinned || tab.isEssential) return false
        closeTab(tabId)
        return mutableState.value.tab(tabId) == null
    }

    // endregion

    companion object {
        private const val TAG = "BrowserController"
        private const val MAX_RECENTLY_CLOSED = 20
        private const val AUTO_UNLOAD_CHECK_MS = 60_000L

        fun Reduction.createdId(): String? = (outcome as? Outcome.Created)?.id
    }
}
