package com.javzzap.laxbrowser.domain

/**
 * The single observable browser state: persisted [data] plus live [runtime] per tab.
 *
 * [quarantinedTabIds] lists tab rows that were restored without a valid Space. They stay in the
 * database but are never shown or attached to another Space.
 *
 * Private browsing lives outside [data] on purpose: [privateTabs] are never diffed, persisted, or
 * restored. While [privateMode] is on, the private tab list is shown instead of Spaces.
 *
 * [splitTabs] maps a tab list ([contextKey]: a Space ID or [PRIVATE_CONTEXT_ID]) to the second tab
 * shown in split view. It is live UI state and is not persisted.
 */
data class BrowserState(
    val isLoaded: Boolean = false,
    val data: BrowserData = BrowserData(),
    val runtime: Map<String, TabRuntime> = emptyMap(),
    val quarantinedTabIds: Set<String> = emptySet(),
    val privateMode: Boolean = false,
    val privateTabs: List<Tab> = emptyList(),
    val privateSelectedTabId: String? = null,
    val splitTabs: Map<String, String> = emptyMap(),
) {
    /** Identifies the tab list currently shown: the active Space, or private browsing. */
    val contextKey: String?
        get() = if (privateMode) PRIVATE_CONTEXT_ID else data.settings.activeSpaceId

    /**
     * The second tab of split view, or null when split view is off or its tab is no longer valid
     * (closed, unloaded, moved to another Space, or now the active tab).
     */
    val splitTab: Tab?
        get() {
            val id = contextKey?.let(splitTabs::get) ?: return null
            val active = activeTab ?: return null
            val tab = tab(id) ?: return null
            if (tab.id == active.id || tab.isPrivate != privateMode || tab.kind != TabKind.WEB) return null
            if (isBlocked(tab) || !runtime(tab.id).loaded) return null
            if (!privateMode && !tab.isEssential && tab.spaceId != activeSpace?.id) return null
            return tab
        }

    /** Tabs on screen: the active tab and, in split view, the second tab. Both sessions are active. */
    val visibleTabIds: Set<String>
        get() = setOfNotNull(activeTab?.id, splitTab?.id)

    fun tabFolders(spaceId: String?): List<TabFolder> =
        data.tabFolders.filter { it.spaceId == spaceId && spaceId != null }.sortedBy { it.position }

    fun tabFolder(id: String?): TabFolder? = data.tabFolders.firstOrNull { it.id == id }

    /** Pinned tabs of [spaceId] that are not in a folder. */
    fun loosePinnedTabs(spaceId: String?): List<Tab> {
        val folderIds = tabFolders(spaceId).map { it.id }.toSet()
        return pinnedTabs(spaceId).filter { it.folderId == null || it.folderId !in folderIds }
    }

    fun tabsInFolder(folderId: String): List<Tab> =
        data.tabs.filter { it.folderId == folderId && it.isPinned && it.id !in quarantinedTabIds }.sortedBy { it.position }

    /** Every tab that may have an engine session: persisted tabs and private tabs. */
    val allTabs: List<Tab>
        get() = data.tabs + privateTabs

    val sortedPrivateTabs: List<Tab>
        get() = privateTabs.sortedBy { it.position }

    val activeSpace: Space?
        get() = data.spaces.firstOrNull { it.id == data.settings.activeSpaceId }

    val sortedSpaces: List<Space>
        get() = data.spaces.sortedBy { it.position }

    val essentials: List<Tab>
        get() = data.tabs.filter { it.isEssential && it.id !in quarantinedTabIds }.sortedBy { it.position }

    fun pinnedTabs(spaceId: String?): List<Tab> =
        data.tabs.filter { it.spaceId == spaceId && spaceId != null && it.isPinned && !it.isEssential }
            .sortedBy { it.position }

    fun regularTabs(spaceId: String?): List<Tab> =
        data.tabs.filter { it.spaceId == spaceId && spaceId != null && !it.isPinned && !it.isEssential }
            .sortedBy { it.position }

    val activeTab: Tab?
        get() = if (privateMode) {
            privateSelectedTabId?.let { id -> privateTabs.firstOrNull { it.id == id } }
        } else {
            activeSpace?.selectedTabId?.let { tab(it) }?.takeIf { !it.isPrivate }
        }

    fun tab(id: String): Tab? =
        data.tabs.firstOrNull { it.id == id && it.id !in quarantinedTabIds } ?: privateTabs.firstOrNull { it.id == id }

    fun space(id: String?): Space? = data.spaces.firstOrNull { it.id == id }

    fun container(id: String?): Container? = data.containers.firstOrNull { it.id == id }

    fun runtime(tabId: String): TabRuntime = runtime[tabId] ?: TabRuntime()

    fun lifecycle(tab: Tab): TabLifecycle = when {
        !runtime(tab.id).loaded -> TabLifecycle.UNLOADED
        tab.id in visibleTabIds -> TabLifecycle.ACTIVE
        else -> TabLifecycle.OPEN
    }

    fun bookmarkFolder(id: String?): BookmarkFolder? = data.bookmarkFolders.firstOrNull { it.id == id }

    /**
     * The folder a folder is displayed in. Folders whose parent is missing or that are part of a
     * parent cycle (corrupt data) are shown at the root instead of disappearing.
     */
    fun effectiveParent(folder: BookmarkFolder): String? {
        val visited = HashSet<String>()
        var current: BookmarkFolder? = folder
        while (current != null) {
            if (!visited.add(current.id)) return null
            val parentId = current.parentId ?: break
            current = bookmarkFolder(parentId) ?: return null
        }
        return folder.parentId
    }

    fun childFolders(parentId: String?): List<BookmarkFolder> =
        data.bookmarkFolders.filter { effectiveParent(it) == parentId }.sortedBy { it.position }

    fun bookmarksIn(folderId: String?): List<Bookmark> = data.bookmarks
        .filter { (it.folderId?.takeIf { id -> bookmarkFolder(id) != null }) == folderId }
        .sortedBy { it.position }

    /** [folderId] and every folder below it. */
    fun folderSubtree(folderId: String): Set<String> {
        val result = linkedSetOf(folderId)
        var frontier = listOf(folderId)
        while (frontier.isNotEmpty()) {
            frontier = data.bookmarkFolders.filter { it.parentId in frontier && it.id !in result }.map { it.id }
            result += frontier
        }
        return result
    }

    /** A tab is blocked when its container no longer exists, so it must never get a session. */
    fun isBlocked(tab: Tab): Boolean = !tab.isPrivate && container(tab.containerId) == null

    fun isSpaceBlocked(space: Space): Boolean = container(space.containerId) == null
}
