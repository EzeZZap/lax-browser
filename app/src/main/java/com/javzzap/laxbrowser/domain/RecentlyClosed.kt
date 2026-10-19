package com.javzzap.laxbrowser.domain

import org.json.JSONArray
import org.json.JSONObject

/** Keeps recently closed tabs across restarts. */
interface RecentlyClosedStore {
    suspend fun load(): List<ClosedTab>
    suspend fun save(tabs: List<ClosedTab>)

    object None : RecentlyClosedStore {
        override suspend fun load(): List<ClosedTab> = emptyList()
        override suspend fun save(tabs: List<ClosedTab>) = Unit
    }
}

/** JSON form of recently closed tabs (with their saved history). Pure, so it is unit-tested. */
object RecentlyClosedCodec {
    fun encode(tabs: List<ClosedTab>): String = JSONArray(tabs.map { closed ->
        val tab = closed.tab
        JSONObject()
            .put("id", tab.id)
            .putOpt("spaceId", tab.spaceId)
            .put("containerId", tab.containerId)
            .put("url", tab.url)
            .put("title", tab.title)
            .putOpt("faviconUrl", tab.faviconUrl)
            .put("position", tab.position)
            .put("pinned", tab.isPinned)
            .put("createdAt", tab.createdAt)
            .put("lastAccessedAt", tab.lastAccessedAt)
            .putOpt("pinnedUrl", tab.pinnedUrl)
            .putOpt("folderId", tab.folderId)
            .putOpt("sessionState", closed.sessionState)
    }).toString()

    /** Reads stored tabs; anything malformed, private, or not a web page is skipped. */
    fun decode(text: String): List<ClosedTab> {
        val array = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
        fun JSONObject.str(name: String): String? = if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }
        return (0 until array.length()).mapNotNull { index ->
            val o = array.optJSONObject(index) ?: return@mapNotNull null
            val url = o.str("url")?.takeIf(UrlInput::isWebUrl) ?: return@mapNotNull null
            val containerId = o.str("containerId")?.takeIf { it != PRIVATE_CONTEXT_ID } ?: return@mapNotNull null
            ClosedTab(
                tab = Tab(
                    id = o.str("id") ?: return@mapNotNull null,
                    spaceId = o.str("spaceId") ?: return@mapNotNull null,
                    containerId = containerId,
                    url = url,
                    title = o.optString("title"),
                    faviconUrl = o.str("faviconUrl")?.takeIf(UrlInput::isWebUrl),
                    position = o.optInt("position"),
                    kind = TabKind.WEB,
                    isPinned = o.optBoolean("pinned"),
                    isEssential = false,
                    createdAt = o.optLong("createdAt"),
                    lastAccessedAt = o.optLong("lastAccessedAt"),
                    pinnedUrl = o.str("pinnedUrl")?.takeIf(UrlInput::isWebUrl),
                    folderId = o.str("folderId"),
                ),
                sessionState = o.str("sessionState"),
            )
        }.distinctBy { it.tab.id }
    }
}
