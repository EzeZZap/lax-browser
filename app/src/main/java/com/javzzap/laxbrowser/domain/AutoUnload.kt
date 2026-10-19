package com.javzzap.laxbrowser.domain

/**
 * Automatic tab unloading frees the memory of tabs that have not been on screen for a while.
 * Unloading keeps the tab record (like the manual "Unload"), so the tab reloads when selected.
 */
object AutoUnload {
    const val DEFAULT_MINUTES = 30
    const val MAX_MINUTES = 24 * 60

    /** Choices offered in Settings; 0 turns automatic unloading off. */
    val CHOICES = listOf(0, 5, 15, 30, 60, 180)

    /**
     * Tabs to unload: loaded web tabs that are not on screen, not in [protectedTabIds] (for example
     * the tab playing media), and hidden for at least [minutes]. [hiddenSince] holds when each tab
     * left the screen; tabs without an entry use their last access time. Private tabs are never
     * unloaded, because their pages cannot be restored.
     */
    fun candidates(
        state: BrowserState,
        hiddenSince: Map<String, Long>,
        now: Long,
        minutes: Int,
        protectedTabIds: Set<String>,
    ): List<String> {
        if (minutes <= 0) return emptyList()
        val limit = minutes * 60_000L
        val visible = state.visibleTabIds
        return state.data.tabs.filter { tab ->
            tab.kind == TabKind.WEB &&
                tab.id !in state.quarantinedTabIds &&
                tab.id !in visible &&
                tab.id !in protectedTabIds &&
                state.runtime(tab.id).loaded &&
                now - (hiddenSince[tab.id] ?: tab.lastAccessedAt) >= limit
        }.map { it.id }
    }
}

/** Per-site zoom and "always open in this Space" rules. */
object SiteRules {
    const val MIN_ZOOM = 30
    const val MAX_ZOOM = 300
    const val DEFAULT_ZOOM = 100

    /** Zoom steps used by the zoom buttons, like desktop browsers. */
    val ZOOM_STEPS = listOf(30, 50, 67, 80, 90, 100, 110, 120, 133, 150, 170, 200, 240, 300)

    /** The key a site's settings are stored under: its host without "www.". */
    fun hostKey(url: String): String? =
        UrlInput.hostOf(url)?.lowercase()?.removePrefix("www.")?.takeIf { it.isNotBlank() && UrlInput.isWebUrl(url) }

    /** The most specific setting for [url]: its own host, or the closest parent domain. */
    fun settingFor(state: BrowserState, url: String): SiteSetting? {
        val host = hostKey(url) ?: return null
        return state.data.siteSettings
            .filter { host == it.host || host.endsWith("." + it.host) }
            .maxByOrNull { it.host.length }
    }

    fun zoomFor(state: BrowserState, url: String): Int = settingFor(state, url)?.zoomPercent ?: DEFAULT_ZOOM

    /** The Space [url] must open in, if a rule names an existing Space with a working container. */
    fun spaceFor(state: BrowserState, url: String): Space? {
        val host = hostKey(url) ?: return null
        val rule = state.data.siteSettings
            .filter { it.spaceId != null && (host == it.host || host.endsWith("." + it.host)) }
            .maxByOrNull { it.host.length } ?: return null
        return state.space(rule.spaceId)?.takeUnless { state.isSpaceBlocked(it) }
    }

    /** The next zoom step up (delta > 0) or down (delta < 0) from [current]. */
    fun step(current: Int, delta: Int): Int =
        if (delta > 0) ZOOM_STEPS.firstOrNull { it > current } ?: MAX_ZOOM else ZOOM_STEPS.lastOrNull { it < current } ?: MIN_ZOOM
}
