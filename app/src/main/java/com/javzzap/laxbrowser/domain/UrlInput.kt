package com.javzzap.laxbrowser.domain

/** Turns URL bar text into a loadable URL or a search URL. Pure JVM so it is unit-testable. */
object UrlInput {
    private val schemePattern = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):(.*)$", RegexOption.DOT_MATCHES_ALL)
    private val ipv4Pattern = Regex("^(25[0-5]|2[0-4]\\d|1?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}$")
    private val hierarchicalSchemes = setOf("http", "https")
    private val opaqueSchemes = setOf("about", "data", "view-source", "mailto", "tel")

    /**
     * Returns the URL to load for [input], or null when the input is blank. Input that is not a
     * URL becomes a search with [searchTemplate].
     */
    fun normalize(input: String, searchTemplate: String = SearchEngines.default.template): String? {
        val text = input.trim()
        if (text.isEmpty()) return null
        return toUrlOrNull(text) ?: SearchEngines.searchUrl(searchTemplate, text)
    }

    /**
     * For an address typed without a scheme ("example.com"), the https:// URL Lax tries first.
     * Only these may fall back to http:// when the secure connection fails, like Firefox's
     * HTTPS-First; links and addresses typed with https:// are never downgraded.
     */
    fun upgradedAddress(input: String): String? {
        val text = input.trim()
        if (text.contains("://")) return null
        return toUrlOrNull(text)?.takeIf { it.startsWith("https://") }
    }

    /** [upgradedAddress]'s URL over plain HTTP. */
    fun httpFallbackFor(httpsUrl: String): String? = httpsUrl.takeIf { it.startsWith("https://") }?.let { "http://" + it.removePrefix("https://") }

    /** Returns [input] as a URL when it is one (explicit scheme or host-like), otherwise null. */
    fun toUrlOrNull(input: String): String? {
        val text = input.trim()
        if (text.isEmpty()) return null

        val match = schemePattern.matchEntire(text)
        if (match != null) {
            val scheme = match.groupValues[1].lowercase()
            val rest = match.groupValues[2]
            when {
                scheme in hierarchicalSchemes && rest.startsWith("//") && rest.length > 2 ->
                    return scheme + ":" + rest.replace(" ", "%20")
                scheme in opaqueSchemes && rest.isNotEmpty() && !text.any { it.isWhitespace() } ->
                    return scheme + ":" + rest
            }
        }

        if (!text.any { it.isWhitespace() } && looksLikeHost(text)) return "https://$text"
        return null
    }

    /** Normalizes user input that must be an http(s) URL (bookmarks, shortcuts). */
    fun normalizeWebUrl(input: String): String? = toUrlOrNull(input)?.takeIf { isWebUrl(it) }

    fun isWebUrl(url: String): Boolean {
        val match = schemePattern.matchEntire(url.trim()) ?: return false
        val scheme = match.groupValues[1].lowercase()
        val rest = match.groupValues[2]
        return scheme in hierarchicalSchemes && rest.startsWith("//") && hostOf(url).isNotEmpty()
    }

    fun searchUrl(query: String, template: String = SearchEngines.default.template): String =
        SearchEngines.searchUrl(template, query)

    /** Host of an http(s) URL, lowercased, or an empty string. */
    fun hostOf(url: String): String {
        val afterScheme = url.substringAfter("://", missingDelimiterValue = "")
        if (afterScheme.isEmpty()) return ""
        val authority = afterScheme.takeWhile { it != '/' && it != '?' && it != '#' }.substringAfterLast('@')
        val host = if (authority.startsWith("[")) {
            authority.substringBefore(']') + "]"
        } else {
            authority.substringBefore(':')
        }
        return host.lowercase()
    }

    /** Origin (`scheme://host[:port]`) of an http(s) URL, or null. */
    fun originOf(url: String): String? {
        if (!isWebUrl(url)) return null
        val scheme = url.substringBefore("://").lowercase()
        val authority = url.substringAfter("://")
            .takeWhile { it != '/' && it != '?' && it != '#' }
            .substringAfterLast('@')
            .lowercase()
        return "$scheme://$authority"
    }

    /** Short label for displaying a URL in lists. */
    fun displayHost(url: String): String = hostOf(url).removePrefix("www.").ifEmpty { url }

    private fun looksLikeHost(text: String): Boolean {
        val authority = text.takeWhile { it != '/' && it != '?' && it != '#' }
        if (authority.isEmpty()) return false

        if (authority.startsWith("[")) {
            val end = authority.indexOf(']')
            if (end < 0) return false
            val tail = authority.substring(end + 1)
            return tail.isEmpty() || (tail.startsWith(":") && tail.drop(1).isPort())
        }

        val host: String
        val colon = authority.lastIndexOf(':')
        if (colon >= 0) {
            if (!authority.substring(colon + 1).isPort()) return false
            host = authority.substring(0, colon)
        } else {
            host = authority
        }

        val lower = host.lowercase()
        if (lower == "localhost") return true
        if (ipv4Pattern.matches(lower)) return true

        val labels = lower.split('.')
        if (labels.size < 2 || labels.any { it.isEmpty() || it.length > 63 }) return false
        if (labels.any { label -> label.startsWith('-') || label.endsWith('-') }) return false
        if (!labels.all { label -> label.all { it.isLetterOrDigit() || it == '-' } }) return false
        val tld = labels.last()
        return tld.startsWith("xn--") || (tld.length >= 2 && tld.all { it.isLetter() })
    }

    private fun String.isPort(): Boolean = isNotEmpty() && length <= 5 && all { it.isDigit() }
}
