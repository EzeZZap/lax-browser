package com.javzzap.laxbrowser.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FaviconSelectorTest {
    private val page = "https://site.example/path/page"

    private fun icon(href: String, size: Int? = null, type: String? = null, rel: String = "icon") =
        IconCandidate(href, size?.let { listOf(IconSize(it, it)) } ?: emptyList(), type, rel)

    @Test
    fun `prefers the smallest icon that is large enough`() {
        val best = FaviconSelector.best(
            listOf(icon("https://site.example/16.png", 16), icon("https://site.example/192.png", 192), icon("https://site.example/96.png", 96)),
            page,
        )
        assertEquals("https://site.example/96.png", best)
    }

    @Test
    fun `falls back to the largest icon when all are small`() {
        val best = FaviconSelector.best(listOf(icon("https://site.example/16.png", 16), icon("https://site.example/32.png", 32)), page)
        assertEquals("https://site.example/32.png", best)
    }

    @Test
    fun `apple touch icons without sizes count as large`() {
        val best = FaviconSelector.best(
            listOf(icon("https://site.example/favicon.png"), icon("https://site.example/touch.png", rel = "apple-touch-icon")),
            page,
        )
        assertEquals("https://site.example/touch.png", best)
    }

    @Test
    fun `svg and non-web icons are skipped`() {
        val best = FaviconSelector.best(
            listOf(
                icon("https://site.example/icon.svg", 512),
                icon("https://site.example/vector", 512, type = "image/svg+xml"),
                icon("data:image/png;base64,AAAA", 512),
                icon("https://site.example/32.png", 32),
            ),
            page,
        )
        assertEquals("https://site.example/32.png", best)
    }

    @Test
    fun `uses favicon ico at the origin when the page declares nothing usable`() {
        assertEquals("https://site.example/favicon.ico", FaviconSelector.best(emptyList(), page))
        assertEquals("https://site.example:8443/favicon.ico", FaviconSelector.best(emptyList(), "https://site.example:8443/a"))
        assertNull(FaviconSelector.best(emptyList(), "about:blank"))
    }
}
