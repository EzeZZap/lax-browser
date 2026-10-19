package com.javzzap.laxbrowser.ui

import com.javzzap.laxbrowser.R
import com.javzzap.laxbrowser.domain.SearchEngines
import com.javzzap.laxbrowser.ui.common.BundledIcons
import com.javzzap.laxbrowser.ui.extensions.recommendedExtensions
import com.javzzap.laxbrowser.ui.onboarding.essentialPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandIconsTest {
    @Test
    fun `bundled icons match a host or its closest parent domain`() {
        assertEquals(R.drawable.site_gmail, BundledIcons.forUrl("https://mail.google.com/mail/u/0/"))
        assertEquals(R.drawable.site_google, BundledIcons.forUrl("https://www.google.com/search?q=x"))
        assertEquals(R.drawable.site_wikipedia, BundledIcons.forUrl("https://en.m.wikipedia.org/wiki/Kotlin"))
        assertEquals(R.drawable.site_duckduckgo, BundledIcons.forUrl("https://duckduckgo.com/?q=%s"))
        assertNull(BundledIcons.forUrl("https://example.com/"))
        assertNull(BundledIcons.forUrl("about:blank"))
        SearchEngines.builtIn.forEach { engine ->
            assertTrue("${engine.name} has a bundled icon", BundledIcons.forUrl(engine.template) != null)
        }
        essentialPresets.forEach { preset ->
            assertTrue("${preset.title} has a bundled icon", BundledIcons.forUrl(preset.url) != null)
        }
    }

    @Test
    fun `every recommended add-on has a bundled icon`() {
        recommendedExtensions.forEach { assertNotNull("${it.name} has an icon", it.icon) }
    }
}
