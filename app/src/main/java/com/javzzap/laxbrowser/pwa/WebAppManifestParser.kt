package com.javzzap.laxbrowser.pwa

import com.javzzap.laxbrowser.domain.IconSize
import com.javzzap.laxbrowser.domain.ManifestIcon
import com.javzzap.laxbrowser.domain.WebAppManifest
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads the manifest JSON that GeckoView passes to `ContentDelegate.onWebAppManifest`. Gecko has
 * already resolved URLs and converted colors to `#AARRGGBB`, but the content is still treated as
 * untrusted: anything malformed yields null or is dropped.
 */
object WebAppManifestParser {
    private const val MAX_ICONS = 64

    fun parse(json: JSONObject): WebAppManifest? {
        val startUrl = json.optStringOrNull("start_url") ?: return null
        val scope = json.optStringOrNull("scope") ?: defaultScope(startUrl) ?: return null
        return WebAppManifest(
            name = json.optStringOrNull("name")?.take(MAX_NAME_LENGTH),
            shortName = json.optStringOrNull("short_name")?.take(MAX_NAME_LENGTH),
            startUrl = startUrl,
            scope = scope,
            display = json.optStringOrNull("display")?.lowercase() ?: "browser",
            themeColor = parseColor(json.optStringOrNull("theme_color")),
            backgroundColor = parseColor(json.optStringOrNull("background_color")),
            icons = parseIcons(json.optJSONArray("icons")),
            manifestUrl = json.optStringOrNull("moz_manifest_url") ?: json.optStringOrNull("manifestUrl"),
            preferRelatedApplications = json.optBoolean("prefer_related_applications", false),
        )
    }

    /** Manifest members such as `sizes` and `purpose` may be a space-separated string or an array. */
    private fun tokens(value: Any?): List<String> = when (value) {
        is JSONArray -> (0 until value.length()).mapNotNull { value.opt(it) as? String }
        is String -> value.split(' ', '\t', '\n')
        else -> emptyList()
    }

    fun parseIconSizes(value: Any?): List<IconSize> {
        return tokens(value).mapNotNull { token ->
            val t = token.trim().lowercase()
            when {
                t == "any" -> IconSize(0, 0, isAny = true)
                else -> {
                    val parts = t.split('x')
                    val w = parts.getOrNull(0)?.toIntOrNull()
                    val h = parts.getOrNull(1)?.toIntOrNull()
                    if (parts.size == 2 && w != null && h != null && w > 0 && h > 0) IconSize(w, h) else null
                }
            }
        }
    }

    /** Parses `#AARRGGBB` or `#RRGGBB`. */
    fun parseColor(value: String?): Int? {
        val hex = value?.trim()?.removePrefix("#") ?: return null
        val parsed = hex.toLongOrNull(16) ?: return null
        return when (hex.length) {
            8 -> parsed.toInt()
            6 -> (0xFF000000L or parsed).toInt()
            else -> null
        }
    }

    private fun parseIcons(array: JSONArray?): List<ManifestIcon> {
        if (array == null) return emptyList()
        return (0 until minOf(array.length(), MAX_ICONS)).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val src = obj.optStringOrNull("src") ?: return@mapNotNull null
            val purposes = tokens(obj.opt("purpose"))
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .toSet()
                .ifEmpty { setOf("any") }
            ManifestIcon(
                src = src,
                sizes = parseIconSizes(obj.opt("sizes")),
                purposes = purposes,
                type = obj.optStringOrNull("type"),
            )
        }
    }

    private fun defaultScope(startUrl: String): String? {
        val schemeEnd = startUrl.indexOf("://")
        if (schemeEnd < 0) return null
        val pathStart = startUrl.indexOf('/', schemeEnd + 3)
        if (pathStart < 0) return "$startUrl/"
        val path = startUrl.substring(pathStart).substringBefore('?').substringBefore('#')
        return startUrl.substring(0, pathStart) + path.substring(0, path.lastIndexOf('/') + 1)
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) (opt(key) as? String)?.trim()?.takeIf { it.isNotEmpty() } else null

    const val MAX_NAME_LENGTH = 100
}
