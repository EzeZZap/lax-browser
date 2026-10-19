package com.javzzap.laxbrowser.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataDiffTest {
    private val a = Shortcut("a", "https://a.example", "A", null, 0)
    private val b = Shortcut("b", "https://b.example", "B", null, 1)

    @Test
    fun `identical data has an empty diff`() {
        val data = BrowserData(shortcuts = listOf(a, b))
        assertTrue(DataDiff.between(data, data).isEmpty)
    }

    @Test
    fun `changed rows are upserted and missing rows deleted`() {
        val old = BrowserData(shortcuts = listOf(a, b))
        val new = BrowserData(shortcuts = listOf(a.copy(title = "A2")))
        val diff = DataDiff.between(old, new)
        assertEquals(listOf(a.copy(title = "A2")), diff.upsertShortcuts)
        assertEquals(listOf("b"), diff.deleteShortcutIds)
    }

    @Test
    fun `deleted tabs also drop their session state`() {
        val tab = Tab("t", "s", "c", "https://x", "x", null, 0, TabKind.WEB, false, false, 0, 0)
        val diff = DataDiff.between(BrowserData(tabs = listOf(tab)), BrowserData())
        assertEquals(listOf("t"), diff.deleteTabIds)
        assertEquals(setOf("t"), diff.discardSessionStateTabIds)
    }

    @Test
    fun `settings are only written when they change`() {
        val old = BrowserData(settings = BrowserSettings("s", 1))
        assertEquals(null, DataDiff.between(old, old).settings)
        assertEquals(BrowserSettings("s", 2), DataDiff.between(old, old.copy(settings = BrowserSettings("s", 2))).settings)
    }
}
