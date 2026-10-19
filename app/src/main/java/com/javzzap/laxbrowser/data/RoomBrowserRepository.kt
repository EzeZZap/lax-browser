package com.javzzap.laxbrowser.data

import android.util.Log
import com.javzzap.laxbrowser.domain.AutoUnload
import com.javzzap.laxbrowser.domain.AutoplayPolicy
import com.javzzap.laxbrowser.domain.SiteRules
import com.javzzap.laxbrowser.domain.SwipeAction
import com.javzzap.laxbrowser.domain.SiteSetting
import com.javzzap.laxbrowser.domain.Bookmark
import com.javzzap.laxbrowser.domain.BookmarkFolder
import com.javzzap.laxbrowser.domain.BrowserData
import com.javzzap.laxbrowser.domain.BrowserSettings
import com.javzzap.laxbrowser.domain.Container
import com.javzzap.laxbrowser.domain.DataDiff
import com.javzzap.laxbrowser.domain.Shortcut
import com.javzzap.laxbrowser.domain.Space
import com.javzzap.laxbrowser.domain.Tab
import com.javzzap.laxbrowser.domain.TabFolder
import com.javzzap.laxbrowser.domain.TabKind
import com.javzzap.laxbrowser.domain.LinkTarget
import com.javzzap.laxbrowser.domain.ToolbarAction
import com.javzzap.laxbrowser.domain.ToolbarPosition
import com.javzzap.laxbrowser.domain.TrackingProtection
import com.javzzap.laxbrowser.domain.WebApp
import com.javzzap.laxbrowser.domain.WebsiteAppearance

class RoomBrowserRepository(private val dao: BrowserDao) : BrowserRepository {

    override suspend fun load(): BrowserData {
        val settings = dao.settings()
        return BrowserData(
            containers = dao.containers().mapSafely { it.toDomain() },
            spaces = dao.spaces().mapSafely { it.toDomain() },
            tabs = dao.tabs().mapSafely { it.toDomain() },
            tabFolders = dao.tabFolders().mapSafely { it.toDomain() },
            bookmarks = dao.bookmarks().mapSafely { it.toDomain() },
            bookmarkFolders = dao.bookmarkFolders().mapSafely { it.toDomain() },
            shortcuts = dao.shortcuts().mapSafely { it.toDomain() },
            webApps = dao.webApps().mapSafely { it.toDomain() },
            siteSettings = dao.siteSettings().mapSafely { it.toDomain() },
            settings = settings?.toDomain() ?: BrowserSettings(),
        )
    }

    override suspend fun apply(diff: DataDiff) {
        if (diff.isEmpty) return
        dao.applyChanges(
            EntityChanges(
                containers = diff.upsertContainers.map { it.toEntity() },
                deleteContainerIds = diff.deleteContainerIds,
                spaces = diff.upsertSpaces.map { it.toEntity() },
                deleteSpaceIds = diff.deleteSpaceIds,
                tabs = diff.upsertTabs.map { it.toEntity() },
                deleteTabIds = diff.deleteTabIds,
                tabFolders = diff.upsertTabFolders.map { it.toEntity() },
                deleteTabFolderIds = diff.deleteTabFolderIds,
                bookmarks = diff.upsertBookmarks.map { it.toEntity() },
                deleteBookmarkIds = diff.deleteBookmarkIds,
                bookmarkFolders = diff.upsertBookmarkFolders.map { it.toEntity() },
                deleteBookmarkFolderIds = diff.deleteBookmarkFolderIds,
                shortcuts = diff.upsertShortcuts.map { it.toEntity() },
                deleteShortcutIds = diff.deleteShortcutIds,
                webApps = diff.upsertWebApps.map { it.toEntity() },
                deleteWebAppIds = diff.deleteWebAppIds,
                siteSettings = diff.upsertSiteSettings.map { it.toEntity() },
                deleteSiteHosts = diff.deleteSiteHosts,
                settings = diff.settings?.toEntity(),
                deleteSessionStateTabIds = diff.discardSessionStateTabIds.toList(),
            ),
        )
    }

    override suspend fun replaceAll(data: BrowserData) {
        dao.replaceAll(
            EntityChanges(
                containers = data.containers.map { it.toEntity() },
                spaces = data.spaces.map { it.toEntity() },
                tabs = data.tabs.filter { !it.isPrivate }.map { it.toEntity() },
                tabFolders = data.tabFolders.map { it.toEntity() },
                bookmarks = data.bookmarks.map { it.toEntity() },
                bookmarkFolders = data.bookmarkFolders.map { it.toEntity() },
                shortcuts = data.shortcuts.map { it.toEntity() },
                siteSettings = data.siteSettings.map { it.toEntity() },
                settings = data.settings.toEntity(),
            ),
        )
    }

    override suspend fun loadSessionState(tabId: String): String? = dao.sessionState(tabId)

    override suspend fun saveSessionStates(states: Map<String, String>, now: Long) {
        if (states.isEmpty()) return
        dao.upsertSessionStates(states.map { (id, state) -> TabSessionStateEntity(id, state, now) })
    }

    /** One malformed row must not prevent startup: it is skipped and left in the database. */
    private inline fun <E, D> List<E>.mapSafely(transform: (E) -> D?): List<D> {
        var skipped = 0
        val result = mapNotNull { row ->
            val mapped = try {
                transform(row)
            } catch (_: RuntimeException) {
                null
            }
            if (mapped == null) skipped++
            mapped
        }
        if (skipped > 0) Log.w(TAG, "Skipped $skipped unreadable row(s)")
        return result
    }

    companion object {
        private const val TAG = "BrowserRepository"
    }
}

internal fun ContainerEntity.toDomain() = Container(id, name, color, createdAt)
internal fun Container.toEntity() = ContainerEntity(id, name, color, createdAt)

internal fun SpaceEntity.toDomain() = Space(id, name, containerId, position, color, selectedTabId, emoji?.takeIf { it.isNotBlank() })
internal fun Space.toEntity() = SpaceEntity(id, name, containerId, position, color, selectedTabId, emoji)

internal fun TabFolderEntity.toDomain() = TabFolder(id, spaceId, name, position, collapsed, createdAt)
internal fun TabFolder.toEntity() = TabFolderEntity(id, spaceId, name, position, collapsed, createdAt)

internal fun TabEntity.toDomain(): Tab? {
    val tabKind = TabKind.entries.firstOrNull { it.name == kind } ?: return null
    if (id.isBlank() || containerId.isBlank()) return null
    return Tab(
        id, spaceId, containerId, url, title, faviconUrl, position, tabKind, isPinned, isEssential, createdAt, lastAccessedAt,
        pinnedUrl = pinnedUrl, folderId = folderId,
    )
}

internal fun Tab.toEntity() = TabEntity(
    id, spaceId, containerId, url, title, faviconUrl, position, kind.name, isPinned, isEssential, createdAt, lastAccessedAt, pinnedUrl, folderId,
)

internal fun BookmarkEntity.toDomain() = Bookmark(id, url, title, faviconUrl, position, createdAt, updatedAt, folderId)
internal fun Bookmark.toEntity() = BookmarkEntity(id, url, title, faviconUrl, position, createdAt, updatedAt, folderId)

internal fun BookmarkFolderEntity.toDomain() = BookmarkFolder(id, name, parentId, position, createdAt, updatedAt)
internal fun BookmarkFolder.toEntity() = BookmarkFolderEntity(id, name, parentId, position, createdAt, updatedAt)

/** Unknown enum values (for example from a newer app version) fall back to the defaults. */
internal fun SettingsEntity.toDomain(): BrowserSettings {
    val defaults = BrowserSettings()
    return BrowserSettings(
        activeSpaceId = activeSpaceId,
        lastBackgroundAt = lastBackgroundAt,
        searchEngineId = searchEngineId,
        customSearchUrl = customSearchUrl,
        toolbarPosition = ToolbarPosition.entries.firstOrNull { it.name == toolbarPosition } ?: defaults.toolbarPosition,
        hideToolbarOnScroll = hideToolbarOnScroll,
        toolbarAction = ToolbarAction.entries.firstOrNull { it.name == toolbarAction } ?: defaults.toolbarAction,
        swapToolbarButtons = swapToolbarButtons,
        sidebarOnRight = sidebarOnRight,
        wallpaperColors = wallpaperColors,
        spaceAccent = spaceAccent,
        externalLinks = LinkTarget.parse(externalLinks),
        saveLogins = saveLogins,
        privateTabsLock = privateTabsLock,
        swipeLeft = swipeAction(swipeLeft, defaults.swipeLeft),
        swipeRight = swipeAction(swipeRight, defaults.swipeRight),
        swipeUp = swipeAction(swipeUp, defaults.swipeUp),
        swipeDown = swipeAction(swipeDown, defaults.swipeDown),
        pullToRefresh = pullToRefresh,
        websiteAppearance = WebsiteAppearance.entries.firstOrNull { it.name == websiteAppearance } ?: defaults.websiteAppearance,
        textScalePercent = textScalePercent.coerceIn(50, 200),
        httpsOnly = httpsOnly,
        trackingProtection = TrackingProtection.entries.firstOrNull { it.name == trackingProtection } ?: defaults.trackingProtection,
        privateBlockScreenshots = privateBlockScreenshots,
        privateHideInRecents = privateHideInRecents,
        searchSuggestions = searchSuggestions,
        globalPrivacyControl = globalPrivacyControl,
        autoplay = AutoplayPolicy.entries.firstOrNull { it.name == autoplay } ?: defaults.autoplay,
        autoUnloadMinutes = autoUnloadMinutes.coerceIn(0, AutoUnload.MAX_MINUTES),
        onboardingDone = onboardingDone,
        checkForUpdates = checkForUpdates,
    )
}

internal fun BrowserSettings.toEntity() = SettingsEntity(
    activeSpaceId = activeSpaceId,
    lastBackgroundAt = lastBackgroundAt,
    searchEngineId = searchEngineId,
    customSearchUrl = customSearchUrl,
    toolbarPosition = toolbarPosition.name,
    hideToolbarOnScroll = hideToolbarOnScroll,
    pullToRefresh = pullToRefresh,
    websiteAppearance = websiteAppearance.name,
    textScalePercent = textScalePercent,
    httpsOnly = httpsOnly,
    trackingProtection = trackingProtection.name,
    privateBlockScreenshots = privateBlockScreenshots,
    privateHideInRecents = privateHideInRecents,
    searchSuggestions = searchSuggestions,
    globalPrivacyControl = globalPrivacyControl,
    autoplay = autoplay.name,
    autoUnloadMinutes = autoUnloadMinutes,
    onboardingDone = onboardingDone,
    checkForUpdates = checkForUpdates,
    toolbarAction = toolbarAction.name,
    swapToolbarButtons = swapToolbarButtons,
    sidebarOnRight = sidebarOnRight,
    wallpaperColors = wallpaperColors,
    spaceAccent = spaceAccent,
    externalLinks = externalLinks.key,
    saveLogins = saveLogins,
    privateTabsLock = privateTabsLock,
    swipeLeft = swipeLeft.name,
    swipeRight = swipeRight.name,
    swipeUp = swipeUp.name,
    swipeDown = swipeDown.name,
)

private fun swipeAction(name: String, fallback: SwipeAction) = SwipeAction.entries.firstOrNull { it.name == name } ?: fallback

internal fun SiteSettingEntity.toDomain() = SiteSetting(host, zoomPercent.coerceIn(SiteRules.MIN_ZOOM, SiteRules.MAX_ZOOM), spaceId)
internal fun SiteSetting.toEntity() = SiteSettingEntity(host, zoomPercent, spaceId)

internal fun ShortcutEntity.toDomain() = Shortcut(id, url, title, iconUrl, position)
internal fun Shortcut.toEntity() = ShortcutEntity(id, url, title, iconUrl, position)

internal fun WebAppEntity.toDomain() = WebApp(
    id, name, shortName, startUrl, scope, display, themeColor, backgroundColor, iconFile, manifestUrl, containerId, installedAt, updatedAt,
)

internal fun WebApp.toEntity() = WebAppEntity(
    id, name, shortName, startUrl, scope, display, themeColor, backgroundColor, iconFile, manifestUrl, containerId, installedAt, updatedAt,
)
