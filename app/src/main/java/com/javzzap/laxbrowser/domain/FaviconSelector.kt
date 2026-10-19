package com.javzzap.laxbrowser.domain

/** An icon declared by a page through `<link rel="icon">` or `apple-touch-icon`. Untrusted. */
data class IconCandidate(
    val href: String,
    val sizes: List<IconSize>,
    val type: String?,
    val rel: String,
)

object FaviconSelector {
    /** Roughly a 32 dp icon on a high-density screen. */
    const val TARGET_PX = 96
    private const val MAX_CANDIDATES = 32
    private const val UNKNOWN_FAVICON_PX = 32
    private const val UNKNOWN_TOUCH_ICON_PX = 180

    /**
     * Picks the icon to download for [pageUrl]: the smallest raster icon of at least [TARGET_PX],
     * otherwise the largest available, otherwise `/favicon.ico` at the page's origin. SVG icons are
     * skipped because they cannot be decoded natively. Returns null for non-web pages.
     */
    fun best(candidates: List<IconCandidate>, pageUrl: String): String? {
        val origin = UrlInput.originOf(pageUrl) ?: return null
        val usable = candidates.take(MAX_CANDIDATES)
            .filter { UrlInput.isWebUrl(it.href) && !isSvg(it) }
            .map { it to sizeOf(it) }
        val chosen = usable.filter { it.second >= TARGET_PX }.minByOrNull { it.second }
            ?: usable.maxByOrNull { it.second }
        return chosen?.first?.href ?: "$origin/favicon.ico"
    }

    private fun sizeOf(candidate: IconCandidate): Int {
        val declared = candidate.sizes.filter { !it.isAny }.maxOfOrNull { minOf(it.width, it.height) }
        if (declared != null) return declared
        return if (candidate.rel.contains("apple-touch-icon")) UNKNOWN_TOUCH_ICON_PX else UNKNOWN_FAVICON_PX
    }

    private fun isSvg(candidate: IconCandidate): Boolean =
        candidate.type?.lowercase()?.contains("svg") == true ||
            candidate.href.substringBefore('?').substringBefore('#').lowercase().endsWith(".svg")
}
