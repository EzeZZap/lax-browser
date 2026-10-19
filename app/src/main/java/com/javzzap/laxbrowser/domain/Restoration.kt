package com.javzzap.laxbrowser.domain

/**
 * Builds a safe starting state from persisted data that may be stale, partial, or inconsistent.
 *
 * - Tabs whose Space is missing are quarantined: kept in the database, hidden, and never attached
 *   to another Space.
 * - Essentials that also reference a Space are quarantined as inconsistent.
 * - Invalid selections and an invalid active Space are cleared.
 * - Tabs and Spaces whose container is missing are kept; [BrowserState.isBlocked] prevents them
 *   from loading until the user assigns a container.
 *
 * Nothing is loaded at startup: every tab starts UNLOADED until it is selected.
 */
object Restoration {
    fun restore(data: BrowserData): BrowserState {
        val spaceIds = data.spaces.map { it.id }.toSet()
        val quarantined = data.tabs.filter { tab ->
            if (tab.isEssential) tab.spaceId != null else tab.spaceId == null || tab.spaceId !in spaceIds
        }.map { it.id }.toSet()

        // A pinned tab only stays in a folder that exists in its own Space.
        val folderSpaces = data.tabFolders.associate { it.id to it.spaceId }
        val tabs = data.tabs.map { tab ->
            val folderId = tab.folderId ?: return@map tab
            if (tab.isPinned && folderSpaces[folderId] == tab.spaceId) tab else tab.copy(folderId = null)
        }
        val validTabs = tabs.filter { it.id !in quarantined }.associateBy { it.id }
        val spaces = data.spaces.map { space ->
            val selected = space.selectedTabId?.let(validTabs::get)
            val valid = selected != null && (selected.isEssential || selected.spaceId == space.id)
            if (valid || space.selectedTabId == null) space else space.copy(selectedTabId = null)
        }

        val activeSpaceId = data.settings.activeSpaceId?.takeIf { it in spaceIds }
            ?: spaces.minByOrNull { it.position }?.id

        return BrowserState(
            isLoaded = true,
            data = data.copy(tabs = tabs, spaces = spaces, settings = data.settings.copy(activeSpaceId = activeSpaceId)),
            runtime = emptyMap(),
            quarantinedTabIds = quarantined,
        )
    }
}
