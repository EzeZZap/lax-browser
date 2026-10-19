package com.javzzap.laxbrowser.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "containers")
data class ContainerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "spaces", indices = [Index("container_id")])
data class SpaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "container_id") val containerId: String,
    val position: Int,
    val color: Int,
    @ColumnInfo(name = "selected_tab_id") val selectedTabId: String?,
    val emoji: String? = null,
)

@Entity(tableName = "tab_folders", indices = [Index("space_id")])
data class TabFolderEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "space_id") val spaceId: String,
    val name: String,
    val position: Int,
    @ColumnInfo(defaultValue = "0") val collapsed: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "tabs", indices = [Index("space_id"), Index("container_id"), Index("folder_id")])
data class TabEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "space_id") val spaceId: String?,
    @ColumnInfo(name = "container_id") val containerId: String,
    val url: String,
    val title: String,
    @ColumnInfo(name = "favicon_url") val faviconUrl: String?,
    val position: Int,
    val kind: String,
    @ColumnInfo(name = "is_pinned") val isPinned: Boolean,
    @ColumnInfo(name = "is_essential") val isEssential: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "last_accessed_at") val lastAccessedAt: Long,
    @ColumnInfo(name = "pinned_url") val pinnedUrl: String? = null,
    @ColumnInfo(name = "folder_id") val folderId: String? = null,
)

/** A file download. Stored apart from browser data; [contentUri] points to the saved file. */
@Entity(tableName = "downloads", indices = [Index("created_at")])
data class DownloadEntity(
    @PrimaryKey val id: String,
    val url: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "mime_type") val mimeType: String?,
    @ColumnInfo(name = "total_bytes") val totalBytes: Long?,
    @ColumnInfo(name = "downloaded_bytes", defaultValue = "0") val downloadedBytes: Long,
    val status: String,
    @ColumnInfo(name = "content_uri") val contentUri: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "site_settings")
data class SiteSettingEntity(
    @PrimaryKey val host: String,
    @ColumnInfo(name = "zoom_percent", defaultValue = "100") val zoomPercent: Int,
    @ColumnInfo(name = "space_id") val spaceId: String?,
)

/** Per-extension app preferences. Extensions themselves are stored by Gecko. */
@Entity(tableName = "extension_prefs")
data class ExtensionPrefEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(defaultValue = "0") val pinned: Boolean,
)

/** Serialized `GeckoSession.SessionState`, kept apart from tab metadata. */
@Entity(tableName = "tab_session_state")
data class TabSessionStateEntity(
    @PrimaryKey @ColumnInfo(name = "tab_id") val tabId: String,
    val state: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "bookmarks", indices = [Index(value = ["url"], unique = true), Index("folder_id")])
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    @ColumnInfo(name = "favicon_url") val faviconUrl: String?,
    val position: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "folder_id") val folderId: String?,
)

@Entity(tableName = "bookmark_folders", indices = [Index("parent_id")])
data class BookmarkFolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "parent_id") val parentId: String?,
    val position: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "history", indices = [Index("visited_at"), Index("url")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    @ColumnInfo(name = "favicon_url") val faviconUrl: String?,
    @ColumnInfo(name = "visited_at") val visitedAt: Long,
)

@Entity(tableName = "shortcuts")
data class ShortcutEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    @ColumnInfo(name = "icon_url") val iconUrl: String?,
    val position: Int,
)

@Entity(tableName = "web_apps", indices = [Index("container_id")])
data class WebAppEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "short_name") val shortName: String?,
    @ColumnInfo(name = "start_url") val startUrl: String,
    val scope: String,
    val display: String,
    @ColumnInfo(name = "theme_color") val themeColor: Int?,
    @ColumnInfo(name = "background_color") val backgroundColor: Int?,
    @ColumnInfo(name = "icon_file") val iconFile: String?,
    @ColumnInfo(name = "manifest_url") val manifestUrl: String?,
    @ColumnInfo(name = "container_id") val containerId: String,
    @ColumnInfo(name = "installed_at") val installedAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "browser_settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "active_space_id") val activeSpaceId: String?,
    @ColumnInfo(name = "last_background_at") val lastBackgroundAt: Long?,
    @ColumnInfo(name = "search_engine_id", defaultValue = "duckduckgo") val searchEngineId: String,
    @ColumnInfo(name = "custom_search_url") val customSearchUrl: String?,
    @ColumnInfo(name = "toolbar_position", defaultValue = "TOP") val toolbarPosition: String,
    @ColumnInfo(name = "hide_toolbar_on_scroll", defaultValue = "1") val hideToolbarOnScroll: Boolean,
    @ColumnInfo(name = "pull_to_refresh", defaultValue = "1") val pullToRefresh: Boolean,
    @ColumnInfo(name = "website_appearance", defaultValue = "SYSTEM") val websiteAppearance: String,
    @ColumnInfo(name = "text_scale_percent", defaultValue = "100") val textScalePercent: Int,
    @ColumnInfo(name = "https_only", defaultValue = "0") val httpsOnly: Boolean,
    @ColumnInfo(name = "tracking_protection", defaultValue = "STANDARD") val trackingProtection: String,
    @ColumnInfo(name = "private_block_screenshots", defaultValue = "1") val privateBlockScreenshots: Boolean,
    @ColumnInfo(name = "private_hide_in_recents", defaultValue = "1") val privateHideInRecents: Boolean,
    @ColumnInfo(name = "search_suggestions", defaultValue = "1") val searchSuggestions: Boolean = true,
    @ColumnInfo(name = "global_privacy_control", defaultValue = "0") val globalPrivacyControl: Boolean = false,
    @ColumnInfo(name = "autoplay", defaultValue = "BLOCK_AUDIBLE") val autoplay: String = "BLOCK_AUDIBLE",
    @ColumnInfo(name = "auto_unload_minutes", defaultValue = "30") val autoUnloadMinutes: Int = 30,
    @ColumnInfo(name = "onboarding_done", defaultValue = "0") val onboardingDone: Boolean = false,
    @ColumnInfo(name = "check_for_updates", defaultValue = "1") val checkForUpdates: Boolean = true,
    @ColumnInfo(name = "toolbar_action", defaultValue = "RELOAD") val toolbarAction: String = "RELOAD",
    @ColumnInfo(name = "swap_toolbar_buttons", defaultValue = "0") val swapToolbarButtons: Boolean = false,
    @ColumnInfo(name = "sidebar_on_right", defaultValue = "0") val sidebarOnRight: Boolean = false,
    @ColumnInfo(name = "wallpaper_colors", defaultValue = "0") val wallpaperColors: Boolean = false,
    @ColumnInfo(name = "space_accent", defaultValue = "1") val spaceAccent: Boolean = true,
    @ColumnInfo(name = "external_links", defaultValue = "active") val externalLinks: String = "active",
    @ColumnInfo(name = "save_logins", defaultValue = "1") val saveLogins: Boolean = true,
    @ColumnInfo(name = "private_tabs_lock", defaultValue = "0") val privateTabsLock: Boolean = false,
    @ColumnInfo(name = "swipe_left", defaultValue = "PREVIOUS_TAB") val swipeLeft: String = "PREVIOUS_TAB",
    @ColumnInfo(name = "swipe_right", defaultValue = "NEXT_TAB") val swipeRight: String = "NEXT_TAB",
    @ColumnInfo(name = "swipe_up", defaultValue = "NEXT_SPACE") val swipeUp: String = "NEXT_SPACE",
    @ColumnInfo(name = "swipe_down", defaultValue = "PREVIOUS_SPACE") val swipeDown: String = "PREVIOUS_SPACE",
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
