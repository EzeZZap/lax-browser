package com.javzzap.laxbrowser.ui.sidebar

import com.javzzap.laxbrowser.domain.Tab

/** A row under the address bar while typing. */
sealed interface Suggestion {
    /** Search for [query] with the user's search engine. [fromEngine] marks the engine's own suggestions. */
    data class Search(val query: String, val fromEngine: Boolean) : Suggestion

    /** Go straight to what was typed, when it looks like an address. */
    data class Address(val url: String) : Suggestion

    /** An open tab, possibly in another Space ([spaceName]). */
    data class OpenTab(val tab: Tab, val spaceName: String?) : Suggestion

    data class Bookmark(val url: String, val title: String, val faviconUrl: String?) : Suggestion

    data class History(val url: String, val title: String, val faviconUrl: String?) : Suggestion
}
