package com.javzzap.laxbrowser.domain

/** The rows that changed between two [BrowserData] snapshots, written in one transaction. */
data class DataDiff(
    val upsertContainers: List<Container> = emptyList(),
    val deleteContainerIds: List<String> = emptyList(),
    val upsertSpaces: List<Space> = emptyList(),
    val deleteSpaceIds: List<String> = emptyList(),
    val upsertTabs: List<Tab> = emptyList(),
    val deleteTabIds: List<String> = emptyList(),
    val upsertTabFolders: List<TabFolder> = emptyList(),
    val deleteTabFolderIds: List<String> = emptyList(),
    val upsertBookmarks: List<Bookmark> = emptyList(),
    val deleteBookmarkIds: List<String> = emptyList(),
    val upsertBookmarkFolders: List<BookmarkFolder> = emptyList(),
    val deleteBookmarkFolderIds: List<String> = emptyList(),
    val upsertShortcuts: List<Shortcut> = emptyList(),
    val deleteShortcutIds: List<String> = emptyList(),
    val upsertWebApps: List<WebApp> = emptyList(),
    val deleteWebAppIds: List<String> = emptyList(),
    val upsertSiteSettings: List<SiteSetting> = emptyList(),
    val deleteSiteHosts: List<String> = emptyList(),
    val settings: BrowserSettings? = null,
    val discardSessionStateTabIds: Set<String> = emptySet(),
) {
    val isEmpty: Boolean
        get() = upsertContainers.isEmpty() && deleteContainerIds.isEmpty() &&
            upsertSpaces.isEmpty() && deleteSpaceIds.isEmpty() &&
            upsertTabs.isEmpty() && deleteTabIds.isEmpty() &&
            upsertTabFolders.isEmpty() && deleteTabFolderIds.isEmpty() &&
            upsertBookmarks.isEmpty() && deleteBookmarkIds.isEmpty() &&
            upsertBookmarkFolders.isEmpty() && deleteBookmarkFolderIds.isEmpty() &&
            upsertShortcuts.isEmpty() && deleteShortcutIds.isEmpty() &&
            upsertWebApps.isEmpty() && deleteWebAppIds.isEmpty() &&
            upsertSiteSettings.isEmpty() && deleteSiteHosts.isEmpty() &&
            settings == null && discardSessionStateTabIds.isEmpty()

    companion object {
        fun between(old: BrowserData, new: BrowserData, discardSessionState: Set<String> = emptySet()): DataDiff {
            val containers = diffBy(old.containers, new.containers) { it.id }
            val spaces = diffBy(old.spaces, new.spaces) { it.id }
            val tabs = diffBy(old.tabs, new.tabs) { it.id }
            val tabFolders = diffBy(old.tabFolders, new.tabFolders) { it.id }
            val bookmarks = diffBy(old.bookmarks, new.bookmarks) { it.id }
            val folders = diffBy(old.bookmarkFolders, new.bookmarkFolders) { it.id }
            val shortcuts = diffBy(old.shortcuts, new.shortcuts) { it.id }
            val webApps = diffBy(old.webApps, new.webApps) { it.id }
            val sites = diffBy(old.siteSettings, new.siteSettings) { it.host }
            return DataDiff(
                upsertContainers = containers.first,
                deleteContainerIds = containers.second,
                upsertSpaces = spaces.first,
                deleteSpaceIds = spaces.second,
                upsertTabs = tabs.first,
                deleteTabIds = tabs.second,
                upsertTabFolders = tabFolders.first,
                deleteTabFolderIds = tabFolders.second,
                upsertBookmarks = bookmarks.first,
                deleteBookmarkIds = bookmarks.second,
                upsertBookmarkFolders = folders.first,
                deleteBookmarkFolderIds = folders.second,
                upsertShortcuts = shortcuts.first,
                deleteShortcutIds = shortcuts.second,
                upsertWebApps = webApps.first,
                deleteWebAppIds = webApps.second,
                upsertSiteSettings = sites.first,
                deleteSiteHosts = sites.second,
                settings = new.settings.takeIf { it != old.settings },
                discardSessionStateTabIds = discardSessionState + tabs.second,
            )
        }

        private fun <T> diffBy(old: List<T>, new: List<T>, id: (T) -> String): Pair<List<T>, List<String>> {
            val oldById = old.associateBy(id)
            val newIds = new.map(id).toSet()
            val upserts = new.filter { oldById[id(it)] != it }
            val deletes = old.map(id).filter { it !in newIds }
            return upserts to deletes
        }
    }
}
