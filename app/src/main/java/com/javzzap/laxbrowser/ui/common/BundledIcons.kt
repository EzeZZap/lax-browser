package com.javzzap.laxbrowser.ui.common

import androidx.annotation.DrawableRes
import com.javzzap.laxbrowser.R
import com.javzzap.laxbrowser.domain.UrlInput

/**
 * Icons shipped with the app (res/drawable-nodpi) for the onboarding's preset sites and the
 * built-in search engines, so they show without a network request and before a site is visited.
 */
object BundledIcons {
    private val byHost: Map<String, Int> = mapOf(
        "mail.google.com" to R.drawable.site_gmail,
        "calendar.google.com" to R.drawable.site_google_calendar,
        "drive.google.com" to R.drawable.site_google_drive,
        "google.com" to R.drawable.site_google,
        "outlook.live.com" to R.drawable.site_outlook,
        "outlook.office.com" to R.drawable.site_outlook,
        "web.whatsapp.com" to R.drawable.site_whatsapp,
        "whatsapp.com" to R.drawable.site_whatsapp,
        "discord.com" to R.drawable.site_discord,
        "youtube.com" to R.drawable.site_youtube,
        "spotify.com" to R.drawable.site_spotify,
        "reddit.com" to R.drawable.site_reddit,
        "wikipedia.org" to R.drawable.site_wikipedia,
        "github.com" to R.drawable.site_github,
        "notion.so" to R.drawable.site_notion,
        "duckduckgo.com" to R.drawable.site_duckduckgo,
        "bing.com" to R.drawable.site_bing,
        "search.brave.com" to R.drawable.site_brave_search,
        "startpage.com" to R.drawable.site_startpage,
        "ecosia.org" to R.drawable.site_ecosia,
        "qwant.com" to R.drawable.site_qwant,
    )

    /**
     * The bundled icon for [url]'s host or its closest listed parent domain (so
     * `en.m.wikipedia.org` uses Wikipedia's), or null.
     */
    @DrawableRes
    fun forUrl(url: String): Int? {
        if (!UrlInput.isWebUrl(url)) return null
        var host = UrlInput.displayHost(url).lowercase().removePrefix("www.")
        while (host.contains('.')) {
            byHost[host]?.let { return it }
            host = host.substringAfter('.')
        }
        return null
    }
}
