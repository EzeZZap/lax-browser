package com.javzzap.laxbrowser.ui.sidebar

import com.javzzap.laxbrowser.domain.BrowserState
import com.javzzap.laxbrowser.domain.Tab
import com.javzzap.laxbrowser.domain.TabKind

/** Local address bar matches. Pure functions, so ranking is unit-tested. */
object SuggestionMatcher {
    private const val MAX_TABS = 8
    private const val MAX_BOOKMARKS = 4

    /**
     * Open web tabs whose title or address contains every word of [query]. With a [spaceId], only
     * that Space's tabs and the Essentials are searched; with null, every Space is, the active
     * Space's tabs first. Private browsing only searches private tabs.
     */
    fun openTabs(state: BrowserState, query: String, spaceId: String? = null, limit: Int = MAX_TABS): List<Suggestion.OpenTab> {
        val activeSpaceId = state.activeSpace?.id
        val candidates = when {
            state.privateMode -> state.privateTabs
            else -> state.data.tabs.filter { it.id !in state.quarantinedTabIds && (spaceId == null || it.isEssential || it.spaceId == spaceId) }
        }
        return candidates
            .filter { it.kind == TabKind.WEB && it.id != state.activeTab?.id && matches(query, it.title, it.url) }
            .sortedWith(compareBy<Tab> { if (it.isEssential || it.spaceId == activeSpaceId) 0 else 1 }.thenByDescending { it.lastAccessedAt })
            .take(limit)
            .map { tab ->
                val spaceName = tab.spaceId?.takeIf { it != activeSpaceId && !tab.isEssential }?.let { state.space(it)?.name }
                Suggestion.OpenTab(tab, spaceName)
            }
    }

    fun bookmarks(state: BrowserState, query: String): List<Suggestion.Bookmark> =
        state.data.bookmarks
            .filter { matches(query, it.title, it.url) }
            .sortedBy { if (hostStartsWith(it.url, query)) 0 else 1 }
            .take(MAX_BOOKMARKS)
            .map { Suggestion.Bookmark(it.url, it.title, it.faviconUrl) }

    private fun matches(query: String, title: String, url: String): Boolean {
        val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return false
        val address = url.substringAfter("://")
        return words.all { title.contains(it, ignoreCase = true) || address.contains(it, ignoreCase = true) }
    }

    private fun hostStartsWith(url: String, query: String): Boolean =
        url.substringAfter("://").removePrefix("www.").startsWith(query.trim(), ignoreCase = true)
}
