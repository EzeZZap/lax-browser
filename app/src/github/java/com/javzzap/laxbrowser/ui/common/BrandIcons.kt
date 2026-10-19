package com.javzzap.laxbrowser.ui.common

import com.javzzap.laxbrowser.R

/**
 * Site and add-on logos bundled with GitHub builds. They belong to their owners and are not
 * freely licensed, so F-Droid builds ship without them (see src/fdroid) and show the site's
 * favicon or a letter instead.
 */
object BrandIcons {
    val sites: Map<String, Int> = mapOf(
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

    /** Recommended add-ons' icons, by add-on ID. */
    val extensions: Map<String, Int> = mapOf(
        "uBlock0@raymondhill.net" to R.drawable.ext_ublock_origin,
        "{446900e4-71c2-419f-a6a7-df9c091e268b}" to R.drawable.ext_bitwarden,
        "addon@darkreader.org" to R.drawable.ext_dark_reader,
        "gdpr@cavi.au.dk" to R.drawable.ext_consent_o_matic,
        "sponsorBlocker@ajay.app" to R.drawable.ext_sponsorblock,
        "jid1-MnnxcxisBPnSXQ@jetpack" to R.drawable.ext_privacy_badger,
    )
}
