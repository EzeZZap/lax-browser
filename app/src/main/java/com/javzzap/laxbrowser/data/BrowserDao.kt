package com.javzzap.laxbrowser.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
abstract class BrowserDao {
    @Query("SELECT * FROM containers") abstract suspend fun containers(): List<ContainerEntity>
    @Query("SELECT * FROM spaces") abstract suspend fun spaces(): List<SpaceEntity>
    @Query("SELECT * FROM tabs") abstract suspend fun tabs(): List<TabEntity>
    @Query("SELECT * FROM tab_folders") abstract suspend fun tabFolders(): List<TabFolderEntity>
    @Query("SELECT * FROM bookmarks") abstract suspend fun bookmarks(): List<BookmarkEntity>
    @Query("SELECT * FROM bookmark_folders") abstract suspend fun bookmarkFolders(): List<BookmarkFolderEntity>
    @Query("SELECT * FROM shortcuts") abstract suspend fun shortcuts(): List<ShortcutEntity>
    @Query("SELECT * FROM web_apps") abstract suspend fun webApps(): List<WebAppEntity>
    @Query("SELECT * FROM site_settings") abstract suspend fun siteSettings(): List<SiteSettingEntity>
    @Query("SELECT * FROM browser_settings WHERE id = 0") abstract suspend fun settings(): SettingsEntity?

    @Upsert abstract suspend fun upsertContainers(items: List<ContainerEntity>)
    @Upsert abstract suspend fun upsertSpaces(items: List<SpaceEntity>)
    @Upsert abstract suspend fun upsertTabs(items: List<TabEntity>)
    @Upsert abstract suspend fun upsertTabFolders(items: List<TabFolderEntity>)
    @Upsert abstract suspend fun upsertBookmarks(items: List<BookmarkEntity>)
    @Upsert abstract suspend fun upsertBookmarkFolders(items: List<BookmarkFolderEntity>)
    @Upsert abstract suspend fun upsertShortcuts(items: List<ShortcutEntity>)
    @Upsert abstract suspend fun upsertWebApps(items: List<WebAppEntity>)
    @Upsert abstract suspend fun upsertSiteSettings(items: List<SiteSettingEntity>)
    @Upsert abstract suspend fun upsertSettings(item: SettingsEntity)
    @Upsert abstract suspend fun upsertSessionStates(items: List<TabSessionStateEntity>)

    @Query("DELETE FROM containers WHERE id IN (:ids)") abstract suspend fun deleteContainers(ids: List<String>)
    @Query("DELETE FROM spaces WHERE id IN (:ids)") abstract suspend fun deleteSpaces(ids: List<String>)
    @Query("DELETE FROM tabs WHERE id IN (:ids)") abstract suspend fun deleteTabs(ids: List<String>)
    @Query("DELETE FROM tab_folders WHERE id IN (:ids)") abstract suspend fun deleteTabFolders(ids: List<String>)
    @Query("DELETE FROM bookmarks WHERE id IN (:ids)") abstract suspend fun deleteBookmarks(ids: List<String>)
    @Query("DELETE FROM bookmark_folders WHERE id IN (:ids)") abstract suspend fun deleteBookmarkFolders(ids: List<String>)
    @Query("DELETE FROM shortcuts WHERE id IN (:ids)") abstract suspend fun deleteShortcuts(ids: List<String>)
    @Query("DELETE FROM web_apps WHERE id IN (:ids)") abstract suspend fun deleteWebApps(ids: List<String>)
    @Query("DELETE FROM site_settings WHERE host IN (:hosts)") abstract suspend fun deleteSiteSettings(hosts: List<String>)
    @Query("DELETE FROM tab_session_state WHERE tab_id IN (:ids)") abstract suspend fun deleteSessionStates(ids: List<String>)

    @Query("DELETE FROM containers") abstract suspend fun clearContainers()
    @Query("DELETE FROM spaces") abstract suspend fun clearSpaces()
    @Query("DELETE FROM tabs") abstract suspend fun clearTabs()
    @Query("DELETE FROM tab_folders") abstract suspend fun clearTabFolders()
    @Query("DELETE FROM bookmarks") abstract suspend fun clearBookmarks()
    @Query("DELETE FROM bookmark_folders") abstract suspend fun clearBookmarkFolders()
    @Query("DELETE FROM shortcuts") abstract suspend fun clearShortcuts()
    @Query("DELETE FROM site_settings") abstract suspend fun clearSiteSettings()
    @Query("DELETE FROM tab_session_state") abstract suspend fun clearSessionStates()

    /** Restores a backup: everything except web apps, history, downloads, and extension prefs is replaced. */
    @Transaction
    open suspend fun replaceAll(changes: EntityChanges) {
        clearSessionStates()
        clearTabs()
        clearTabFolders()
        clearSpaces()
        clearBookmarks()
        clearBookmarkFolders()
        clearShortcuts()
        clearSiteSettings()
        clearContainers()
        applyChanges(changes)
    }

    @Query("SELECT state FROM tab_session_state WHERE tab_id = :tabId")
    abstract suspend fun sessionState(tabId: String): String?

    /** Writes a whole [DataDiff][com.javzzap.laxbrowser.domain.DataDiff] atomically. Deletes run first. */
    @Transaction
    open suspend fun applyChanges(changes: EntityChanges) {
        if (changes.deleteTabIds.isNotEmpty()) deleteTabs(changes.deleteTabIds)
        if (changes.deleteSessionStateTabIds.isNotEmpty()) deleteSessionStates(changes.deleteSessionStateTabIds)
        if (changes.deleteTabFolderIds.isNotEmpty()) deleteTabFolders(changes.deleteTabFolderIds)
        if (changes.deleteSpaceIds.isNotEmpty()) deleteSpaces(changes.deleteSpaceIds)
        if (changes.deleteBookmarkIds.isNotEmpty()) deleteBookmarks(changes.deleteBookmarkIds)
        if (changes.deleteBookmarkFolderIds.isNotEmpty()) deleteBookmarkFolders(changes.deleteBookmarkFolderIds)
        if (changes.deleteShortcutIds.isNotEmpty()) deleteShortcuts(changes.deleteShortcutIds)
        if (changes.deleteWebAppIds.isNotEmpty()) deleteWebApps(changes.deleteWebAppIds)
        if (changes.deleteSiteHosts.isNotEmpty()) deleteSiteSettings(changes.deleteSiteHosts)
        if (changes.deleteContainerIds.isNotEmpty()) deleteContainers(changes.deleteContainerIds)
        if (changes.containers.isNotEmpty()) upsertContainers(changes.containers)
        if (changes.spaces.isNotEmpty()) upsertSpaces(changes.spaces)
        if (changes.tabFolders.isNotEmpty()) upsertTabFolders(changes.tabFolders)
        if (changes.tabs.isNotEmpty()) upsertTabs(changes.tabs)
        if (changes.bookmarkFolders.isNotEmpty()) upsertBookmarkFolders(changes.bookmarkFolders)
        if (changes.bookmarks.isNotEmpty()) upsertBookmarks(changes.bookmarks)
        if (changes.shortcuts.isNotEmpty()) upsertShortcuts(changes.shortcuts)
        if (changes.webApps.isNotEmpty()) upsertWebApps(changes.webApps)
        if (changes.siteSettings.isNotEmpty()) upsertSiteSettings(changes.siteSettings)
        changes.settings?.let { upsertSettings(it) }
    }
}

data class EntityChanges(
    val containers: List<ContainerEntity> = emptyList(),
    val deleteContainerIds: List<String> = emptyList(),
    val spaces: List<SpaceEntity> = emptyList(),
    val deleteSpaceIds: List<String> = emptyList(),
    val tabs: List<TabEntity> = emptyList(),
    val deleteTabIds: List<String> = emptyList(),
    val tabFolders: List<TabFolderEntity> = emptyList(),
    val deleteTabFolderIds: List<String> = emptyList(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val deleteBookmarkIds: List<String> = emptyList(),
    val bookmarkFolders: List<BookmarkFolderEntity> = emptyList(),
    val deleteBookmarkFolderIds: List<String> = emptyList(),
    val shortcuts: List<ShortcutEntity> = emptyList(),
    val deleteShortcutIds: List<String> = emptyList(),
    val webApps: List<WebAppEntity> = emptyList(),
    val deleteWebAppIds: List<String> = emptyList(),
    val siteSettings: List<SiteSettingEntity> = emptyList(),
    val deleteSiteHosts: List<String> = emptyList(),
    val settings: SettingsEntity? = null,
    val deleteSessionStateTabIds: List<String> = emptyList(),
)
