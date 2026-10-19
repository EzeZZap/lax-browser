package com.javzzap.laxbrowser.domain

import org.json.JSONArray
import org.json.JSONObject

/**
 * Lax's own backup file: Spaces, containers, tabs, folders, bookmarks, shortcuts, site settings,
 * preferences, and optionally saved logins (encrypted with a password, see [LoginBackup]).
 * History, downloads, installed web apps, and anything Gecko stores (cookies, site data,
 * extensions) are not included. Restored files are untrusted: rows that fail validation are
 * dropped, and [Restoration] repairs references between what remains.
 */
object Backup {
    const val FORMAT = 1
    private const val MAX_ROWS = 100_000

    class InvalidBackupException(message: String) : Exception(message)

    /** [logins] is the section made by [LoginBackup.encrypt], or null to leave logins out. */
    fun encode(data: BrowserData, appVersion: String, now: Long, logins: JSONObject? = null): String = JSONObject().apply {
        put("format", FORMAT)
        put("app", "Lax")
        put("appVersion", appVersion)
        put("createdAt", now)
        putOpt("logins", logins)
        put("containers", JSONArray(data.containers.map { JSONObject().put("id", it.id).put("name", it.name).put("color", it.color).put("createdAt", it.createdAt) }))
        put(
            "spaces",
            JSONArray(
                data.spaces.map {
                    JSONObject().put("id", it.id).put("name", it.name).put("containerId", it.containerId).put("position", it.position)
                        .put("color", it.color).putOpt("selectedTabId", it.selectedTabId).putOpt("emoji", it.emoji)
                },
            ),
        )
        put(
            "tabs",
            JSONArray(
                data.tabs.filter { !it.isPrivate }.map {
                    JSONObject().put("id", it.id).putOpt("spaceId", it.spaceId).put("containerId", it.containerId).put("url", it.url)
                        .put("title", it.title).putOpt("faviconUrl", it.faviconUrl).put("position", it.position).put("kind", it.kind.name)
                        .put("pinned", it.isPinned).put("essential", it.isEssential).put("createdAt", it.createdAt)
                        .put("lastAccessedAt", it.lastAccessedAt).putOpt("pinnedUrl", it.pinnedUrl).putOpt("folderId", it.folderId)
                },
            ),
        )
        put(
            "tabFolders",
            JSONArray(
                data.tabFolders.map {
                    JSONObject().put("id", it.id).put("spaceId", it.spaceId).put("name", it.name).put("position", it.position)
                        .put("collapsed", it.collapsed).put("createdAt", it.createdAt)
                },
            ),
        )
        put(
            "bookmarks",
            JSONArray(
                data.bookmarks.map {
                    JSONObject().put("id", it.id).put("url", it.url).put("title", it.title).putOpt("faviconUrl", it.faviconUrl)
                        .put("position", it.position).put("createdAt", it.createdAt).put("updatedAt", it.updatedAt).putOpt("folderId", it.folderId)
                },
            ),
        )
        put(
            "bookmarkFolders",
            JSONArray(
                data.bookmarkFolders.map {
                    JSONObject().put("id", it.id).put("name", it.name).putOpt("parentId", it.parentId).put("position", it.position)
                        .put("createdAt", it.createdAt).put("updatedAt", it.updatedAt)
                },
            ),
        )
        put("shortcuts", JSONArray(data.shortcuts.map { JSONObject().put("id", it.id).put("url", it.url).put("title", it.title).put("position", it.position) }))
        put("siteSettings", JSONArray(data.siteSettings.map { JSONObject().put("host", it.host).put("zoom", it.zoomPercent).putOpt("spaceId", it.spaceId) }))
        val s = data.settings
        put(
            "settings",
            JSONObject().putOpt("activeSpaceId", s.activeSpaceId).put("searchEngineId", s.searchEngineId).putOpt("customSearchUrl", s.customSearchUrl)
                .put("toolbarPosition", s.toolbarPosition.name).put("hideToolbarOnScroll", s.hideToolbarOnScroll).put("pullToRefresh", s.pullToRefresh)
                .put("websiteAppearance", s.websiteAppearance.name).put("textScalePercent", s.textScalePercent).put("httpsOnly", s.httpsOnly)
                .put("trackingProtection", s.trackingProtection.name).put("privateBlockScreenshots", s.privateBlockScreenshots)
                .put("privateHideInRecents", s.privateHideInRecents).put("searchSuggestions", s.searchSuggestions)
                .put("globalPrivacyControl", s.globalPrivacyControl).put("autoplay", s.autoplay.name).put("autoUnloadMinutes", s.autoUnloadMinutes)
                .put("checkForUpdates", s.checkForUpdates).put("toolbarAction", s.toolbarAction.name)
                .put("swapToolbarButtons", s.swapToolbarButtons).put("sidebarOnRight", s.sidebarOnRight)
                .put("wallpaperColors", s.wallpaperColors).put("spaceAccent", s.spaceAccent).put("externalLinks", s.externalLinks.key)
                .put("saveLogins", s.saveLogins).put("privateTabsLock", s.privateTabsLock)
                .put("swipeLeft", s.swipeLeft.name).put("swipeRight", s.swipeRight.name)
                .put("swipeUp", s.swipeUp.name).put("swipeDown", s.swipeDown.name),
        )
    }.toString(2)

    /** The encrypted logins section of a backup, if it has one. */
    fun encryptedLogins(text: String): JSONObject? = runCatching { JSONObject(text).optJSONObject("logins") }.getOrNull()

    /** Reads a backup. Throws [InvalidBackupException] when the file is not a Lax backup. */
    fun decode(text: String): BrowserData {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: throw InvalidBackupException("Not JSON")
        if (root.optString("app") != "Lax" || root.optInt("format", -1) !in 1..FORMAT) throw InvalidBackupException("Not a Lax backup")
        fun rows(name: String): List<JSONObject> {
            val array = root.optJSONArray(name) ?: return emptyList()
            return (0 until minOf(array.length(), MAX_ROWS)).mapNotNull { array.optJSONObject(it) }
        }
        fun JSONObject.str(name: String): String? = if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }
        fun JSONObject.req(name: String): String? = str(name)?.takeIf { it.isNotBlank() }

        val containers = rows("containers").mapNotNull { o ->
            Container(o.req("id") ?: return@mapNotNull null, o.req("name") ?: return@mapNotNull null, o.optInt("color"), o.optLong("createdAt"))
        }.distinctBy { it.id }
        val spaces = rows("spaces").mapNotNull { o ->
            Space(
                id = o.req("id") ?: return@mapNotNull null,
                name = o.req("name") ?: return@mapNotNull null,
                containerId = o.req("containerId") ?: return@mapNotNull null,
                position = o.optInt("position"),
                color = o.optInt("color"),
                selectedTabId = o.str("selectedTabId"),
                emoji = o.str("emoji")?.take(16),
            )
        }.distinctBy { it.id }
        val tabs = rows("tabs").mapNotNull { o ->
            val kind = TabKind.entries.firstOrNull { it.name == o.optString("kind") } ?: return@mapNotNull null
            val url = o.optString("url")
            if (kind == TabKind.WEB && !UrlInput.isWebUrl(url)) return@mapNotNull null
            Tab(
                id = o.req("id") ?: return@mapNotNull null,
                spaceId = o.str("spaceId"),
                containerId = o.req("containerId") ?: return@mapNotNull null,
                url = if (kind == TabKind.WEB) url else "",
                title = o.optString("title").take(500),
                faviconUrl = o.str("faviconUrl")?.takeIf(UrlInput::isWebUrl),
                position = o.optInt("position"),
                kind = kind,
                isPinned = o.optBoolean("pinned"),
                isEssential = o.optBoolean("essential"),
                createdAt = o.optLong("createdAt"),
                lastAccessedAt = o.optLong("lastAccessedAt"),
                pinnedUrl = o.str("pinnedUrl")?.takeIf(UrlInput::isWebUrl),
                folderId = o.str("folderId"),
            )
        }.distinctBy { it.id }
        val tabFolders = rows("tabFolders").mapNotNull { o ->
            TabFolder(
                o.req("id") ?: return@mapNotNull null, o.req("spaceId") ?: return@mapNotNull null, o.req("name") ?: return@mapNotNull null,
                o.optInt("position"), o.optBoolean("collapsed"), o.optLong("createdAt"),
            )
        }.distinctBy { it.id }
        val bookmarks = rows("bookmarks").mapNotNull { o ->
            val url = o.str("url")?.let(UrlInput::normalizeWebUrl) ?: return@mapNotNull null
            Bookmark(
                o.req("id") ?: return@mapNotNull null, url, o.optString("title").take(500).ifBlank { UrlInput.displayHost(url) },
                o.str("faviconUrl")?.takeIf(UrlInput::isWebUrl), o.optInt("position"), o.optLong("createdAt"), o.optLong("updatedAt"), o.str("folderId"),
            )
        }.distinctBy { it.id }.distinctBy { it.url }
        val bookmarkFolders = rows("bookmarkFolders").mapNotNull { o ->
            BookmarkFolder(
                o.req("id") ?: return@mapNotNull null, o.req("name") ?: return@mapNotNull null, o.str("parentId"),
                o.optInt("position"), o.optLong("createdAt"), o.optLong("updatedAt"),
            )
        }.distinctBy { it.id }
        val shortcuts = rows("shortcuts").mapNotNull { o ->
            val url = o.str("url")?.let(UrlInput::normalizeWebUrl) ?: return@mapNotNull null
            Shortcut(o.req("id") ?: return@mapNotNull null, url, o.optString("title").take(200).ifBlank { UrlInput.displayHost(url) }, null, o.optInt("position"))
        }.distinctBy { it.id }
        val siteSettings = rows("siteSettings").mapNotNull { o ->
            val host = o.req("host")?.lowercase()?.takeIf { SiteRules.hostKey("https://$it") == it } ?: return@mapNotNull null
            SiteSetting(host, o.optInt("zoom", SiteRules.DEFAULT_ZOOM).coerceIn(SiteRules.MIN_ZOOM, SiteRules.MAX_ZOOM), o.str("spaceId"))
        }.distinctBy { it.host }

        val s = root.optJSONObject("settings") ?: JSONObject()
        val defaults = BrowserSettings()
        fun <E : Enum<E>> enumOf(values: Array<E>, name: String, fallback: E) = values.firstOrNull { it.name == s.optString(name) } ?: fallback
        val settings = BrowserSettings(
            activeSpaceId = s.str("activeSpaceId"),
            searchEngineId = s.optString("searchEngineId", defaults.searchEngineId),
            customSearchUrl = s.str("customSearchUrl"),
            toolbarPosition = enumOf(ToolbarPosition.entries.toTypedArray(), "toolbarPosition", defaults.toolbarPosition),
            hideToolbarOnScroll = s.optBoolean("hideToolbarOnScroll", defaults.hideToolbarOnScroll),
            toolbarAction = enumOf(ToolbarAction.entries.toTypedArray(), "toolbarAction", defaults.toolbarAction),
            swapToolbarButtons = s.optBoolean("swapToolbarButtons", defaults.swapToolbarButtons),
            sidebarOnRight = s.optBoolean("sidebarOnRight", defaults.sidebarOnRight),
            wallpaperColors = s.optBoolean("wallpaperColors", defaults.wallpaperColors),
            spaceAccent = s.optBoolean("spaceAccent", defaults.spaceAccent),
            externalLinks = LinkTarget.parse(s.str("externalLinks")),
            saveLogins = s.optBoolean("saveLogins", defaults.saveLogins),
            privateTabsLock = s.optBoolean("privateTabsLock", defaults.privateTabsLock),
            swipeLeft = enumOf(SwipeAction.entries.toTypedArray(), "swipeLeft", defaults.swipeLeft),
            swipeRight = enumOf(SwipeAction.entries.toTypedArray(), "swipeRight", defaults.swipeRight),
            swipeUp = enumOf(SwipeAction.entries.toTypedArray(), "swipeUp", defaults.swipeUp),
            swipeDown = enumOf(SwipeAction.entries.toTypedArray(), "swipeDown", defaults.swipeDown),
            pullToRefresh = s.optBoolean("pullToRefresh", defaults.pullToRefresh),
            websiteAppearance = enumOf(WebsiteAppearance.entries.toTypedArray(), "websiteAppearance", defaults.websiteAppearance),
            textScalePercent = s.optInt("textScalePercent", defaults.textScalePercent).coerceIn(50, 200),
            httpsOnly = s.optBoolean("httpsOnly", defaults.httpsOnly),
            trackingProtection = enumOf(TrackingProtection.entries.toTypedArray(), "trackingProtection", defaults.trackingProtection),
            privateBlockScreenshots = s.optBoolean("privateBlockScreenshots", defaults.privateBlockScreenshots),
            privateHideInRecents = s.optBoolean("privateHideInRecents", defaults.privateHideInRecents),
            searchSuggestions = s.optBoolean("searchSuggestions", defaults.searchSuggestions),
            globalPrivacyControl = s.optBoolean("globalPrivacyControl", defaults.globalPrivacyControl),
            autoplay = enumOf(AutoplayPolicy.entries.toTypedArray(), "autoplay", defaults.autoplay),
            autoUnloadMinutes = s.optInt("autoUnloadMinutes", defaults.autoUnloadMinutes).coerceIn(0, AutoUnload.MAX_MINUTES),
            onboardingDone = true,
            checkForUpdates = s.optBoolean("checkForUpdates", defaults.checkForUpdates),
        ).let { if (it.searchEngineId == SearchEngines.CUSTOM_ID && !SearchEngines.isValidCustomTemplate(it.customSearchUrl)) it.copy(searchEngineId = defaults.searchEngineId) else it }

        if (spaces.isEmpty()) throw InvalidBackupException("No Spaces")
        return BrowserData(
            containers = containers,
            spaces = spaces,
            tabs = tabs,
            tabFolders = tabFolders,
            bookmarks = bookmarks,
            bookmarkFolders = bookmarkFolders,
            shortcuts = shortcuts,
            siteSettings = siteSettings,
            settings = settings,
        )
    }
}
