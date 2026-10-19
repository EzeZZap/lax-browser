package com.javzzap.laxbrowser.domain

import com.javzzap.laxbrowser.testing.FakeClock
import com.javzzap.laxbrowser.testing.SequentialIds
import com.javzzap.laxbrowser.testing.initialState
import com.javzzap.laxbrowser.testing.then
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchAndSettingsTest {
    private val ops = BrowserOperations(FakeClock(), SequentialIds())

    @Test
    fun `duckduckgo is the default search engine`() {
        assertEquals("https://duckduckgo.com/?q=%s", SearchEngines.templateFor(BrowserSettings()))
        val state = initialState(ops).then { ops.navigate(it, null, "hello world") }
        assertEquals("https://duckduckgo.com/?q=hello+world", state.activeTab!!.url)
    }

    @Test
    fun `navigation searches with the selected engine`() {
        val state = initialState(ops)
            .then { ops.updateSettings(it) { s -> s.copy(searchEngineId = "google") } }
            .then { ops.navigate(it, null, "kotlin coroutines") }
        assertEquals("https://www.google.com/search?q=kotlin+coroutines", state.activeTab!!.url)
    }

    @Test
    fun `urls are never sent to the search engine`() {
        val state = initialState(ops)
            .then { ops.updateSettings(it) { s -> s.copy(searchEngineId = "bing") } }
            .then { ops.navigate(it, null, "example.com") }
        assertEquals("https://example.com", state.activeTab!!.url)
    }

    @Test
    fun `custom search templates must be http urls with a placeholder`() {
        assertTrue(SearchEngines.isValidCustomTemplate("https://search.example/?q=%s"))
        assertFalse(SearchEngines.isValidCustomTemplate("https://search.example/?q="))
        assertFalse(SearchEngines.isValidCustomTemplate("javascript:alert('%s')"))
        assertFalse(SearchEngines.isValidCustomTemplate(null))

        val state = initialState(ops)
        val rejected = ops.updateSettings(state) { it.copy(searchEngineId = SearchEngines.CUSTOM_ID, customSearchUrl = "nope") }
        assertEquals(Outcome.Rejected(RejectReason.INVALID_URL), rejected.outcome)

        val accepted = state
            .then { ops.updateSettings(it) { s -> s.copy(searchEngineId = SearchEngines.CUSTOM_ID, customSearchUrl = " https://s.example/find?q=%s ") } }
            .then { ops.navigate(it, null, "a b") }
        assertEquals("https://s.example/find?q=a+b", accepted.activeTab!!.url)
    }

    @Test
    fun `an invalid stored choice falls back to the default engine`() {
        val settings = BrowserSettings(searchEngineId = SearchEngines.CUSTOM_ID, customSearchUrl = "broken")
        assertEquals(SearchEngines.default.template, SearchEngines.templateFor(settings))
        assertEquals(SearchEngines.default.template, SearchEngines.templateFor(BrowserSettings(searchEngineId = "gone")))
    }

    @Test
    fun `text scale is limited and unknown engines are rejected`() {
        val state = initialState(ops)
        assertEquals(200, ops.updateSettings(state) { it.copy(textScalePercent = 500) }.state.data.settings.textScalePercent)
        assertEquals(50, ops.updateSettings(state) { it.copy(textScalePercent = 10) }.state.data.settings.textScalePercent)
        assertEquals(Outcome.Rejected(RejectReason.NOT_FOUND), ops.updateSettings(state) { it.copy(searchEngineId = "nope") }.outcome)
    }

    @Test
    fun `toolbar and site settings are stored`() {
        val state = initialState(ops).then {
            ops.updateSettings(it) { s ->
                s.copy(
                    toolbarPosition = ToolbarPosition.BOTTOM,
                    hideToolbarOnScroll = false,
                    pullToRefresh = false,
                    websiteAppearance = WebsiteAppearance.DARK,
                    httpsOnly = true,
                    trackingProtection = TrackingProtection.STRICT,
                )
            }
        }
        val settings = state.data.settings
        assertEquals(ToolbarPosition.BOTTOM, settings.toolbarPosition)
        assertFalse(settings.hideToolbarOnScroll)
        assertEquals(EngineSettings(WebsiteAppearance.DARK, 100, true, TrackingProtection.STRICT), settings.engineSettings)
    }
}
