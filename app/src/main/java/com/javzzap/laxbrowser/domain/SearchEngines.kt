package com.javzzap.laxbrowser.domain

import org.json.JSONArray
import java.net.URLEncoder

/**
 * A search engine. [template] is an http(s) URL in which `%s` is replaced by the encoded query.
 * [suggestTemplate] returns OpenSearch suggestions (`["query", ["suggestion", ...]]`).
 */
data class SearchEngine(val id: String, val name: String, val template: String, val suggestTemplate: String? = null)

object SearchEngines {
    const val DEFAULT_ID = "duckduckgo"
    const val CUSTOM_ID = "custom"
    const val PLACEHOLDER = "%s"
    private const val MAX_SUGGESTIONS = 6

    val builtIn: List<SearchEngine> = listOf(
        SearchEngine("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=%s", "https://duckduckgo.com/ac/?q=%s&type=list"),
        SearchEngine("google", "Google", "https://www.google.com/search?q=%s", "https://suggestqueries.google.com/complete/search?client=firefox&q=%s"),
        SearchEngine("bing", "Bing", "https://www.bing.com/search?q=%s", "https://api.bing.com/osjson.aspx?query=%s"),
        SearchEngine("brave", "Brave Search", "https://search.brave.com/search?q=%s", "https://search.brave.com/api/suggest?q=%s"),
        SearchEngine("startpage", "Startpage", "https://www.startpage.com/sp/search?query=%s"),
        SearchEngine("ecosia", "Ecosia", "https://www.ecosia.org/search?q=%s", "https://ac.ecosia.org/autocomplete?q=%s&type=list"),
        SearchEngine("qwant", "Qwant", "https://www.qwant.com/?q=%s"),
        SearchEngine(
            "wikipedia", "Wikipedia", "https://www.wikipedia.org/search-redirect.php?search=%s",
            "https://en.wikipedia.org/w/api.php?action=opensearch&format=json&search=%s",
        ),
    )

    val default: SearchEngine = builtIn.first { it.id == DEFAULT_ID }

    /** A custom template must be an http(s) URL that contains the `%s` placeholder. */
    fun isValidCustomTemplate(template: String?): Boolean {
        val trimmed = template?.trim() ?: return false
        if (!trimmed.contains(PLACEHOLDER)) return false
        return UrlInput.isWebUrl(trimmed.replace(PLACEHOLDER, "test"))
    }

    /** The template to use for [settings]; falls back to the default when the choice is invalid. */
    fun templateFor(settings: BrowserSettings): String = when (settings.searchEngineId) {
        CUSTOM_ID -> settings.customSearchUrl?.trim()?.takeIf(::isValidCustomTemplate) ?: default.template
        else -> (builtIn.firstOrNull { it.id == settings.searchEngineId } ?: default).template
    }

    /** The suggestion endpoint for [settings], or null (custom engines have none). */
    fun suggestTemplateFor(settings: BrowserSettings): String? =
        if (settings.searchEngineId == CUSTOM_ID) null else (builtIn.firstOrNull { it.id == settings.searchEngineId } ?: default).suggestTemplate

    fun searchUrl(template: String, query: String): String =
        template.replace(PLACEHOLDER, URLEncoder.encode(query, "UTF-8"))

    /** Parses an OpenSearch suggestion response; anything malformed yields no suggestions. */
    fun parseSuggestions(body: String, query: String): List<String> = runCatching {
        val array = JSONArray(body).getJSONArray(1)
        (0 until array.length()).mapNotNull { array.optString(it).trim().takeIf { s -> s.isNotEmpty() && s.length <= 200 } }
            .filter { !it.equals(query.trim(), ignoreCase = true) }
            .distinct()
            .take(MAX_SUGGESTIONS)
    }.getOrDefault(emptyList())
}
