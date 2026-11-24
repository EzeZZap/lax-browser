package com.javzzap.laxbrowser.domain

/**
 * DuckDuckGo-style bang shortcuts (`!g cats`, `!w Einstein`) that jump to a built-in search
 * engine regardless of the user's default. Unknown bangs fall through to normal URL/search
 * handling.
 */
object BangShortcuts {
    /** Bang token (without `!`) → [SearchEngines.builtIn] id. */
    private val BANGS: Map<String, String> = mapOf(
        "g" to "google",
        "google" to "google",
        "b" to "bing",
        "bing" to "bing",
        "w" to "wikipedia",
        "wiki" to "wikipedia",
        "wikipedia" to "wikipedia",
        "d" to "duckduckgo",
        "ddg" to "duckduckgo",
        "duckduckgo" to "duckduckgo",
        "br" to "brave",
        "brave" to "brave",
        "sp" to "startpage",
        "startpage" to "startpage",
        "eco" to "ecosia",
        "ecosia" to "ecosia",
        "q" to "qwant",
        "qwant" to "qwant",
    )

    private val INPUT = Regex("^!([A-Za-z0-9]+)(?:\\s+(.+))?$", RegexOption.DOT_MATCHES_ALL)

    /**
     * When [input] is `!<bang> <query>` with a known bang, returns that engine's search URL.
     * Blank query or unknown bang → null.
     */
    fun resolve(input: String): String? {
        val text = input.trim()
        if (!text.startsWith('!')) return null
        val match = INPUT.matchEntire(text) ?: return null
        val key = match.groupValues[1].lowercase()
        val query = match.groupValues[2].trim()
        if (query.isEmpty()) return null
        val engineId = BANGS[key] ?: return null
        val engine = SearchEngines.builtIn.firstOrNull { it.id == engineId } ?: return null
        return SearchEngines.searchUrl(engine.template, query)
    }

    /** Short labels for Settings help, e.g. `!g Google`. */
    fun helpLabels(): List<String> = listOf(
        "!g Google",
        "!b Bing",
        "!w Wikipedia",
        "!d DuckDuckGo",
        "!br Brave",
        "!sp Startpage",
        "!eco Ecosia",
        "!q Qwant",
    )
}
