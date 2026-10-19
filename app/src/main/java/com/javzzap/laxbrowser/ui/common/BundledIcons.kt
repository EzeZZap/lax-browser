package com.javzzap.laxbrowser.ui.common

import androidx.annotation.DrawableRes
import com.javzzap.laxbrowser.domain.UrlInput

/**
 * Icons shipped with the app for the onboarding's preset sites and the built-in search engines,
 * so they show without a network request and before a site is visited. Which icons exist depends
 * on the build ([BrandIcons]).
 */
object BundledIcons {
    /**
     * The bundled icon for [url]'s host or its closest listed parent domain (so
     * `en.m.wikipedia.org` uses Wikipedia's), or null.
     */
    @DrawableRes
    fun forUrl(url: String): Int? {
        if (!UrlInput.isWebUrl(url)) return null
        var host = UrlInput.displayHost(url).lowercase().removePrefix("www.")
        while (host.contains('.')) {
            BrandIcons.sites[host]?.let { return it }
            host = host.substringAfter('.')
        }
        return null
    }
}
