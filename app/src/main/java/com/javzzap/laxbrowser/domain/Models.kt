package com.javzzap.laxbrowser.domain

/** A storage partition. [id] is passed to Gecko as the session context ID and is never reused. */
data class Container(
    val id: String,
    val name: String,
    val color: Int,
    val createdAt: Long,
)

/** [color] is a preset or custom ARGB color; [emoji], when set, replaces the color dot as the icon. */
data class Space(
    val id: String,
    val name: String,
    val containerId: String,
    val position: Int,
    val color: Int,
    val selectedTabId: String?,
    val emoji: String? = null,
)

/** A folder of pinned tabs inside one Space (like Zen's folders). */
data class TabFolder(
    val id: String,
    val spaceId: String,
    val name: String,
    val position: Int,
    val collapsed: Boolean,
    val createdAt: Long,
)

enum class TabKind { WEB, NEW_TAB }

/**
 * A browser tab record.
 *
 * Space tabs have a non-null [spaceId] and the same [containerId] as their Space. Essentials are
 * global: [spaceId] is null and [containerId] is the container they were created with. Private
 * tabs ([isPrivate]) have no Space, use [PRIVATE_CONTEXT_ID] instead of a container, and only exist
 * in memory.
 *
 * Pinned tabs and Essentials remember [pinnedUrl], the page they can be reset to. [folderId] places a
 * pinned tab in one of its Space's folders.
 */
data class Tab(
    val id: String,
    val spaceId: String?,
    val containerId: String,
    val url: String,
    val title: String,
    val faviconUrl: String?,
    val position: Int,
    val kind: TabKind,
    val isPinned: Boolean,
    val isEssential: Boolean,
    val createdAt: Long,
    val lastAccessedAt: Long,
    val isPrivate: Boolean = false,
    val pinnedUrl: String? = null,
    val folderId: String? = null,
)

/** Marks a private tab's engine context. Container IDs are UUIDs, so they never collide with it. */
const val PRIVATE_CONTEXT_ID = "private"

/** A bookmark. [folderId] is null for the root ("Bookmarks") level. */
data class Bookmark(
    val id: String,
    val url: String,
    val title: String,
    val faviconUrl: String?,
    val position: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val folderId: String? = null,
)

/** A bookmark folder. [parentId] is null for folders at the root level. */
data class BookmarkFolder(
    val id: String,
    val name: String,
    val parentId: String?,
    val position: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Per-site settings keyed by host (rules also match subdomains): page zoom and the Space the site
 * always opens in. A null [spaceId] means no Space rule.
 */
data class SiteSetting(
    val host: String,
    val zoomPercent: Int = 100,
    val spaceId: String? = null,
)

/** A website shortcut shown on the new-tab page. Not a tab. */
data class Shortcut(
    val id: String,
    val url: String,
    val title: String,
    val iconUrl: String?,
    val position: Int,
)

/** An installed progressive web app. [id] is also the launcher shortcut ID. */
data class WebApp(
    val id: String,
    val name: String,
    val shortName: String?,
    val startUrl: String,
    val scope: String,
    val display: String,
    val themeColor: Int?,
    val backgroundColor: Int?,
    val iconFile: String?,
    val manifestUrl: String?,
    val containerId: String,
    val installedAt: Long,
    val updatedAt: Long,
)

enum class ToolbarPosition { TOP, BOTTOM }

/** The customizable button between the address and the menu button in the toolbar. */
enum class ToolbarAction {
    RELOAD, BACK, FORWARD, HOME, BOOKMARK, SHARE, COPY_LINK, FIND, READER_VIEW, TRANSLATE,
    DESKTOP_SITE, SPLIT_VIEW, PRIVATE_BROWSING, BOOKMARKS, HISTORY, DOWNLOADS, NONE,
}

enum class WebsiteAppearance { SYSTEM, LIGHT, DARK }

enum class TrackingProtection { STANDARD, STRICT, OFF }

/** Where links opened from other apps (or shared to Lax) go. Stored as [key]. */
sealed interface LinkTarget {
    val key: String

    /** The active Space, or the Space a site rule names. */
    data object ActiveSpace : LinkTarget {
        override val key = "active"
    }

    /** Ask every time. */
    data object Ask : LinkTarget {
        override val key = "ask"
    }

    data object PrivateTab : LinkTarget {
        override val key = "private"
    }

    /** A specific Space, and with it that Space's container. */
    data class InSpace(val spaceId: String) : LinkTarget {
        override val key get() = "space:$spaceId"
    }

    companion object {
        /** Unknown or empty values fall back to the active Space. */
        fun parse(key: String?): LinkTarget = when {
            key == Ask.key -> Ask
            key == PrivateTab.key -> PrivateTab
            key != null && key.startsWith("space:") && key.length > 6 -> InSpace(key.removePrefix("space:"))
            else -> ActiveSpace
        }
    }
}

/** Default for media that starts playing by itself. Sites can be allowed individually. */
enum class AutoplayPolicy { BLOCK_AUDIBLE, BLOCK_ALL, ALLOW_ALL }

/** Browser state and user preferences, persisted as a single row. */
data class BrowserSettings(
    val activeSpaceId: String? = null,
    val lastBackgroundAt: Long? = null,
    val searchEngineId: String = SearchEngines.DEFAULT_ID,
    val customSearchUrl: String? = null,
    val toolbarPosition: ToolbarPosition = ToolbarPosition.TOP,
    val hideToolbarOnScroll: Boolean = true,
    val toolbarAction: ToolbarAction = ToolbarAction.RELOAD,
    /** Puts the menu button where the sidebar button normally is, and the other way around. */
    val swapToolbarButtons: Boolean = false,
    val sidebarOnRight: Boolean = false,
    /** Android 12+ wallpaper-based colors instead of Lax's own palette. */
    val wallpaperColors: Boolean = false,
    /** Tints buttons, selections, and the loading bar with the active Space's color. */
    val spaceAccent: Boolean = true,
    /** Where links from other apps open. */
    val externalLinks: LinkTarget = LinkTarget.ActiveSpace,
    val pullToRefresh: Boolean = true,
    val websiteAppearance: WebsiteAppearance = WebsiteAppearance.SYSTEM,
    val textScalePercent: Int = 100,
    val httpsOnly: Boolean = false,
    val trackingProtection: TrackingProtection = TrackingProtection.STANDARD,
    val privateBlockScreenshots: Boolean = true,
    val privateHideInRecents: Boolean = true,
    val searchSuggestions: Boolean = true,
    val globalPrivacyControl: Boolean = false,
    val autoplay: AutoplayPolicy = AutoplayPolicy.BLOCK_AUDIBLE,
    /** Unload tabs hidden for this many minutes; 0 never unloads automatically. */
    val autoUnloadMinutes: Int = AutoUnload.DEFAULT_MINUTES,
    val onboardingDone: Boolean = false,
    val checkForUpdates: Boolean = true,
    /** Offer to save logins and fill them in (Lax's own encrypted storage). */
    val saveLogins: Boolean = true,
    /** Ask for the screen lock before showing private tabs again after leaving the app. */
    val privateTabsLock: Boolean = false,
) {
    /** The parts that configure the engine, used to detect when they need to be re-applied. */
    val engineSettings: EngineSettings
        get() = EngineSettings(websiteAppearance, textScalePercent, httpsOnly, trackingProtection, globalPrivacyControl, autoplay, saveLogins)
}

data class EngineSettings(
    val websiteAppearance: WebsiteAppearance,
    val textScalePercent: Int,
    val httpsOnly: Boolean,
    val trackingProtection: TrackingProtection,
    val globalPrivacyControl: Boolean = false,
    val autoplay: AutoplayPolicy = AutoplayPolicy.BLOCK_AUDIBLE,
    val saveLogins: Boolean = true,
)

/** Everything that is persisted. Independent from live Gecko sessions. */
data class BrowserData(
    val containers: List<Container> = emptyList(),
    val spaces: List<Space> = emptyList(),
    val tabs: List<Tab> = emptyList(),
    val tabFolders: List<TabFolder> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val bookmarkFolders: List<BookmarkFolder> = emptyList(),
    val shortcuts: List<Shortcut> = emptyList(),
    val webApps: List<WebApp> = emptyList(),
    val siteSettings: List<SiteSetting> = emptyList(),
    val settings: BrowserSettings = BrowserSettings(),
)

enum class TabLifecycle {
    /** Selected and displayed. */
    ACTIVE,

    /** Has a session but is not displayed. */
    OPEN,

    /** The record exists but no session or engine resources are held. */
    UNLOADED,
}

/** Live, non-persisted per-tab state reported by the engine. */
data class TabRuntime(
    val loaded: Boolean = false,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isSecure: Boolean = false,
    val manifest: WebAppManifest? = null,
    val crashed: Boolean = false,
    val desktopMode: Boolean = false,
)

/** A tab that was closed and can be reopened with its saved history. */
data class ClosedTab(val tab: Tab, val sessionState: String?)

/** What the user long-pressed in a page. URLs are untrusted page input. */
data class ContextMenuTarget(
    val tabId: String,
    val linkUrl: String?,
    val imageUrl: String?,
    val title: String?,
)

/** A manifest as delivered by Gecko after its own validation and URL resolution. */
data class WebAppManifest(
    val name: String?,
    val shortName: String?,
    val startUrl: String,
    val scope: String,
    val display: String,
    val themeColor: Int?,
    val backgroundColor: Int?,
    val icons: List<ManifestIcon>,
    val manifestUrl: String?,
    val preferRelatedApplications: Boolean,
) {
    val displayName: String? get() = name?.takeIf { it.isNotBlank() } ?: shortName?.takeIf { it.isNotBlank() }
}

data class ManifestIcon(
    val src: String,
    val sizes: List<IconSize>,
    val purposes: Set<String>,
    val type: String?,
)

/** A manifest icon size. `any` (scalable) is represented by [isAny]. */
data class IconSize(val width: Int, val height: Int, val isAny: Boolean = false)
