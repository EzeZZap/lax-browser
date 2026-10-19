package com.javzzap.laxbrowser.ui

import com.javzzap.laxbrowser.domain.BrowserOperations
import com.javzzap.laxbrowser.domain.DownloadNames
import com.javzzap.laxbrowser.domain.Outcome
import com.javzzap.laxbrowser.domain.SearchEngines
import com.javzzap.laxbrowser.engine.gecko.WebPrompt
import com.javzzap.laxbrowser.testing.FakeClock
import com.javzzap.laxbrowser.domain.Reduction
import com.javzzap.laxbrowser.testing.SequentialIds
import com.javzzap.laxbrowser.testing.initialState
import com.javzzap.laxbrowser.testing.then
import com.javzzap.laxbrowser.ui.prompts.DateTimeValues
import com.javzzap.laxbrowser.ui.sidebar.SuggestionMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class SuggestionsAndHelpersTest {
    private val ops = BrowserOperations(FakeClock(), SequentialIds())

    private fun Reduction.id() = (outcome as Outcome.Created).id

    @Test
    fun `open tabs match across Spaces and name the other Space`() {
        var state = initialState(ops)
        state = state.then { ops.newTab(it, "https://docs.example/guide") }.then { ops.onTitleChange(it, it.activeTab!!.id, "Guide") }
        val first = state.activeSpace!!
        state = state.then { ops.createSpace(it, "Work", 0, com.javzzap.laxbrowser.domain.ContainerChoice.New("Work", 0)) }
        state = state.then { ops.newTab(it, "https://mail.example") }
        val matches = SuggestionMatcher.openTabs(state, "docs")
        assertEquals(1, matches.size)
        assertEquals(first.name, matches.single().spaceName)
        assertTrue("the active tab is not suggested", SuggestionMatcher.openTabs(state, "mail").isEmpty())
    }

    @Test
    fun `tab search can be limited to one Space and keeps Essentials`() {
        var state = initialState(ops)
        val home = state.activeSpace!!
        state = state.then { ops.newTab(it, "https://news.example/home") }
        val essential = ops.newTab(state, "https://news.example/mail").also { state = it.state }.id()
        state = state.then { ops.addToEssentials(it, essential) }
        state = state.then { ops.createSpace(it, "Work", 0, com.javzzap.laxbrowser.domain.ContainerChoice.New("Work", 0)) }
        val work = state.activeSpace!!
        state = state.then { ops.newTab(it, "https://news.example/work") }.then { ops.newTab(it, "https://other.example") }

        val inHome = SuggestionMatcher.openTabs(state, "news", spaceId = home.id).map { it.tab.url }
        assertEquals(setOf("https://news.example/home", "https://news.example/mail"), inHome.toSet())
        val inWork = SuggestionMatcher.openTabs(state, "news", spaceId = work.id).map { it.tab.url }
        assertEquals(setOf("https://news.example/work", "https://news.example/mail"), inWork.toSet())
        val everywhere = SuggestionMatcher.openTabs(state, "news").map { it.tab.url }
        assertEquals(3, everywhere.size)
        assertTrue("the active Space comes first", everywhere.indexOf("https://news.example/work") < everywhere.indexOf("https://news.example/home"))
    }

    @Test
    fun `Space accents keep text readable for any Space color`() {
        val white = 0xFFFFFFFF.toInt()
        val darkSurface = 0xFF13111A.toInt()
        listOf(0xFFFFEB3B, 0xFF00E5FF, 0xFF7C4DFF, 0xFF9E9E9E, 0xFF000000, 0xFFFFFFFF).map { it.toInt() }.forEach { color ->
            val light = com.javzzap.laxbrowser.ui.theme.SpaceAccent.colors(color, dark = false)
            assertTrue("white on light accent of ${Integer.toHexString(color)}", com.javzzap.laxbrowser.ui.theme.SpaceAccent.contrast(light.primary, white) >= 4.5)
            val dark = com.javzzap.laxbrowser.ui.theme.SpaceAccent.colors(color, dark = true)
            assertTrue("dark accent of ${Integer.toHexString(color)} on the dark surface", com.javzzap.laxbrowser.ui.theme.SpaceAccent.contrast(dark.primary, darkSurface) >= 4.5)
        }
    }

    @Test
    fun `emoji search matches every word and puts word starts first`() {
        val search = com.javzzap.laxbrowser.ui.common.EmojiSearch
        val entries = listOf(
            com.javzzap.laxbrowser.ui.common.EmojiEntry("😸", "grinning cat face with smiling eyes"),
            com.javzzap.laxbrowser.ui.common.EmojiEntry("🐱", "cat face gato"),
            com.javzzap.laxbrowser.ui.common.EmojiEntry("🎓", "graduation cap school escuela"),
        )
        assertEquals(listOf("😸", "🐱"), search.search(entries, "cat"))
        assertEquals(listOf("😸"), search.search(entries, "smiling CAT"))
        assertEquals(listOf("🎓"), search.search(entries, "escuela"))
        assertTrue(search.search(entries, "  ").isEmpty())
        assertTrue(search.isTypedEmoji("🚀"))
        assertTrue(search.isTypedEmoji("👩‍💻"))
        assertFalse(search.isTypedEmoji("rocket"))
        assertFalse(search.isTypedEmoji("!"))
    }

    @Test
    fun `shortcuts added from a page keep its web icon`() {
        val state = initialState(ops)
        val withIcon = ops.addShortcut(state, "https://news.example/", "News", "https://news.example/icon.png").state
        assertEquals("https://news.example/icon.png", withIcon.data.shortcuts.single().iconUrl)
        val withData = ops.addShortcut(state, "https://news.example/", "News", "data:image/png;base64,AAAA").state
        assertNull(withData.data.shortcuts.single().iconUrl)
    }

    @Test
    fun `tab search matches every word in any order`() {
        var state = initialState(ops)
        state = state.then { ops.newTab(it, "https://docs.example/kotlin") }.then { ops.onTitleChange(it, it.activeTab!!.id, "Coroutines guide") }
        state = state.then { ops.newTab(it, "https://other.example") }
        assertEquals(1, SuggestionMatcher.openTabs(state, "guide kotlin").size)
        assertTrue(SuggestionMatcher.openTabs(state, "guide rust").isEmpty())
    }

    @Test
    fun `private browsing only suggests private tabs`() {
        var state = initialState(ops).then { ops.newTab(it, "https://normal.example") }
        state = state.then { ops.setPrivateMode(it, true) }.then { ops.newTab(it, "https://secret.example") }
        state = state.then { ops.newTab(it, "https://other.example") }
        assertTrue(SuggestionMatcher.openTabs(state, "normal").isEmpty())
        assertEquals(1, SuggestionMatcher.openTabs(state, "secret").size)
    }

    @Test
    fun `bookmarks starting with the query come first`() {
        var state = initialState(ops)
        state = state.then { ops.addBookmark(it, "https://blog.example/rust", "Rust notes") }
        state = state.then { ops.addBookmark(it, "https://rust-lang.org", "Rust") }
        val matches = SuggestionMatcher.bookmarks(state, "rust")
        assertEquals("https://rust-lang.org", matches.first().url)
        assertEquals(2, matches.size)
        assertTrue((ops.addBookmark(state, "https://x.example", null).outcome) is Outcome.Created)
    }

    @Test
    fun `search suggestions parse OpenSearch responses and ignore junk`() {
        assertEquals(listOf("kotlin flow", "kotlin coroutines"), SearchEngines.parseSuggestions("""["kotlin",["kotlin","kotlin flow","kotlin coroutines"]]""", "kotlin"))
        assertTrue(SearchEngines.parseSuggestions("<html>", "x").isEmpty())
        assertTrue(SearchEngines.parseSuggestions("""{"a":1}""", "x").isEmpty())
    }

    @Test
    fun `download names come from headers or the URL and cannot leave the folder`() {
        assertEquals("report.pdf", DownloadNames.fileName("attachment; filename=\"report.pdf\"", "https://x.example/dl?id=1", "application/pdf"))
        assertEquals("résumé.txt", DownloadNames.fileName("attachment; filename*=UTF-8''r%C3%A9sum%C3%A9.txt", "https://x.example/a", null))
        assertEquals("_.._etc_passwd", DownloadNames.fileName("attachment; filename=\"/../etc/passwd\"", "https://x.example/a", null))
        assertEquals("photo.jpg", DownloadNames.fileName(null, "https://x.example/img/photo", "image/jpeg"))
        assertEquals("download", DownloadNames.fileName(null, "https://x.example/", null))
    }

    @Test
    fun `shared text opens the first address or searches the text`() {
        assertEquals("https://news.example/story", SharedText.extract("Look at this https://news.example/story, wow"))
        assertEquals("best pizza nearby", SharedText.extract("  best pizza nearby "))
        assertNull(SharedText.extract("   "))
    }

    @Test
    fun `date and time inputs use HTML formats`() {
        val date = LocalDate.of(2026, 1, 5)
        val time = LocalTime.of(9, 7)
        assertEquals("2026-01-05", DateTimeValues.format(WebPrompt.DateTimeType.DATE, date, null))
        assertEquals("2026-01", DateTimeValues.format(WebPrompt.DateTimeType.MONTH, date, null))
        assertEquals("2026-W02", DateTimeValues.format(WebPrompt.DateTimeType.WEEK, date, null))
        assertEquals("09:07", DateTimeValues.format(WebPrompt.DateTimeType.TIME, null, time))
        assertEquals("2026-01-05T09:07", DateTimeValues.format(WebPrompt.DateTimeType.DATETIME_LOCAL, date, time))
        assertEquals(LocalDate.of(2026, 3, 1), DateTimeValues.initialDate(WebPrompt.DateTimeType.MONTH, "2026-03"))
        assertEquals(LocalDate.of(2026, 1, 5), DateTimeValues.initialDate(WebPrompt.DateTimeType.WEEK, "2026-W02"))
        assertEquals(LocalTime.of(14, 30), DateTimeValues.initialTime("2026-01-05T14:30"))
        assertNull(DateTimeValues.initialDate(WebPrompt.DateTimeType.DATE, "garbage"))
    }
}
