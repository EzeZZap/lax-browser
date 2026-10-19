package com.javzzap.laxbrowser.domain

import com.javzzap.laxbrowser.platform.Versions
import com.javzzap.laxbrowser.testing.FakeClock
import com.javzzap.laxbrowser.testing.SequentialIds
import com.javzzap.laxbrowser.testing.initialState
import com.javzzap.laxbrowser.testing.then
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseFeaturesTest {
    private val clock = FakeClock()
    private val ops = BrowserOperations(clock, SequentialIds())

    private fun Reduction.id() = (outcome as Outcome.Created).id

    @Test
    fun `tabs hidden longer than the setting are unloaded, visible and protected ones are kept`() {
        var state = initialState(ops)
        val old = ops.newTab(state, "https://old.example").also { state = it.state }.id()
        val media = ops.newTab(state, "https://music.example").also { state = it.state }.id()
        val visible = ops.newTab(state, "https://visible.example").also { state = it.state }.id()
        assertTrue(state.runtime(old).loaded)
        val now = clock.now() + 31 * 60_000L
        val hidden = mapOf(old to clock.now(), media to clock.now())

        val candidates = AutoUnload.candidates(state, hidden, now, minutes = 30, protectedTabIds = setOf(media))
        assertEquals(listOf(old), candidates)
        assertTrue(visible in state.visibleTabIds)
        assertTrue(AutoUnload.candidates(state, hidden, now, minutes = 0, protectedTabIds = emptySet()).isEmpty())
        assertTrue("not yet", AutoUnload.candidates(state, hidden, clock.now() + 10 * 60_000L, 30, emptySet()).isEmpty())
    }

    @Test
    fun `private tabs are never unloaded automatically`() {
        var state = initialState(ops).then { ops.setPrivateMode(it, true) }
        val first = ops.newTab(state, "https://a.example").also { state = it.state }.id()
        ops.newTab(state, "https://b.example").also { state = it.state }
        val candidates = AutoUnload.candidates(state, mapOf(first to 0L), clock.now() + 600 * 60_000L, 5, emptySet())
        assertTrue(candidates.isEmpty())
    }

    @Test
    fun `site rules open a site in its Space from the address bar and from new tabs`() {
        var state = initialState(ops)
        val personal = state.activeSpace!!.id
        val work = ops.createSpace(state, "Work", 0, ContainerChoice.New("Work", 0)).also { state = it.state }.id()
        state = state.then { ops.switchSpace(it, personal) }.then { ops.setSiteSpace(it, "mail.example", work) }

        state = state.then { ops.newTab(it, "https://inbox.mail.example/today") }
        assertEquals(work, state.activeSpace!!.id)
        assertEquals("https://inbox.mail.example/today", state.activeTab!!.url)
        assertEquals(state.space(work)!!.containerId, state.activeTab!!.containerId)

        state = state.then { ops.switchSpace(it, personal) }
        val ntp = ops.openNewTabPage(state).also { state = it.state }.state.activeTab!!.id
        state = state.then { ops.loadUrl(it, ntp, "https://mail.example/") }
        assertEquals("the ruled site opens in its Space", work, state.activeSpace!!.id)
        assertEquals("the original tab is left alone", TabKind.NEW_TAB, state.tab(ntp)!!.kind)
    }

    @Test
    fun `links follow site rules except in private tabs and Essentials`() {
        var state = initialState(ops)
        val personal = state.activeSpace!!.id
        val work = ops.createSpace(state, "Work", 0, ContainerChoice.New("Work", 0)).also { state = it.state }.id()
        state = state.then { ops.switchSpace(it, personal) }.then { ops.setSiteSpace(it, "https://docs.example", work) }
        val tab = ops.newTab(state, "https://news.example").also { state = it.state }.id()
        assertEquals(work, ops.linkRuleSpace(state, tab, "https://docs.example/a"))
        assertNull("same site inside its Space", ops.linkRuleSpace(state.then { ops.switchSpace(it, work) }, tab, "https://news.example"))
        state = state.then { ops.addToEssentials(it, tab) }
        assertNull(ops.linkRuleSpace(state, tab, "https://docs.example/a"))
    }

    @Test
    fun `zoom is stored per site and cleared at 100 percent`() {
        var state = initialState(ops)
        val zoomed = ops.setSiteZoom(state, "https://www.example.com/page", 133)
        assertEquals(EngineCommand.RefreshZoom, zoomed.commands.single())
        state = zoomed.state
        assertEquals(133, SiteRules.zoomFor(state, "https://example.com/other"))
        assertEquals("subdomains share the site's zoom", 133, SiteRules.zoomFor(state, "https://m.example.com"))
        assertEquals(100, SiteRules.zoomFor(state, "https://another.example"))
        state = state.then { ops.setSiteZoom(it, "https://example.com", 100) }
        assertTrue(state.data.siteSettings.isEmpty())
        assertEquals(110, SiteRules.step(100, 1))
        assertEquals(90, SiteRules.step(100, -1))
        assertEquals(SiteRules.MAX_ZOOM, SiteRules.step(300, 1))
    }

    @Test
    fun `deleting a Space drops rules pointing to it but keeps zoom`() {
        var state = initialState(ops)
        val work = ops.createSpace(state, "Work", 0, ContainerChoice.New("Work", 0)).also { state = it.state }.id()
        state = state.then { ops.setSiteSpace(it, "a.example", work) }
            .then { ops.setSiteSpace(it, "b.example", work) }
            .then { ops.setSiteZoom(it, "https://b.example", 150) }
        state = state.then { ops.deleteSpace(it, work) }
        assertEquals(listOf(SiteSetting("b.example", 150, null)), state.data.siteSettings)
    }

    @Test
    fun `bookmark files round trip with folders and skip what is already saved`() {
        var state = initialState(ops)
        val folder = ops.createBookmarkFolder(state, "Work & Co", null).also { state = it.state }.id()
        state = state.then { ops.addBookmark(it, "https://a.example/?x=1&y=2", "A <tag>", folder) }
            .then { ops.addBookmark(it, "https://b.example", "B") }
        val html = BookmarkHtml.export(state, "Bookmarks")
        val nodes = BookmarkHtml.parse(html)
        assertEquals(2, BookmarkHtml.countLinks(nodes))
        val work = nodes.filterIsInstance<BookmarkHtml.Folder>().single()
        assertEquals("Work & Co", work.name)
        assertEquals(BookmarkHtml.Link("https://a.example/?x=1&y=2", "A <tag>"), work.children.single())

        val (reduction, summary) = ops.importBookmarks(state, nodes, "Imported")
        assertEquals(ImportSummary(imported = 0, skipped = 2), summary)
        assertTrue(reduction.state.data.bookmarkFolders.any { it.name == "Imported" })
    }

    @Test
    fun `bookmark files from other browsers are parsed tolerantly`() {
        val html = """
            <!DOCTYPE NETSCAPE-Bookmark-file-1>
            <DL><p>
              <DT><H3 PERSONAL_TOOLBAR_FOLDER="true">Bookmarks bar</H3>
              <DL><p>
                <DT><A HREF="https://news.example/" ADD_DATE="1">News &amp; more</A>
                <DT><A HREF="javascript:alert(1)">Bad</A>
                <DT><A HREF='ftp://files.example'>FTP</A>
              </DL><p>
              <DT><A HREF="example.org/page">Bare</A>
            </DL>
        """.trimIndent()
        val nodes = BookmarkHtml.parse(html)
        val bar = nodes.filterIsInstance<BookmarkHtml.Folder>().single()
        assertEquals("Bookmarks bar", bar.name)
        assertEquals(listOf(BookmarkHtml.Link("https://news.example/", "News & more")), bar.children)
        assertEquals("https://example.org/page", (nodes.last() as BookmarkHtml.Link).url)
        assertTrue(BookmarkHtml.parse("not a bookmark file").isEmpty())
    }

    @Test
    fun `backups restore Spaces, tabs, and settings and reject other files`() {
        var state = initialState(ops)
        val tab = ops.newTab(state, "https://a.example").also { state = it.state }.id()
        state = state.then { ops.pinTab(it, tab) }
            .then { ops.addBookmark(it, "https://b.example", "B") }
            .then { ops.setSiteZoom(it, "https://a.example", 120) }
            .then {
                ops.updateSettings(it) { s ->
                    s.copy(
                        autoUnloadMinutes = 60, httpsOnly = true, toolbarAction = ToolbarAction.BOOKMARK, swapToolbarButtons = true,
                        sidebarOnRight = true, wallpaperColors = true, spaceAccent = false,
                    )
                }
            }
        val restored = Backup.decode(Backup.encode(state.data, "1.0", clock.now()))
        assertTrue(restored.settings.wallpaperColors)
        assertFalse(restored.settings.spaceAccent)
        assertEquals(ToolbarAction.BOOKMARK, restored.settings.toolbarAction)
        assertTrue(restored.settings.swapToolbarButtons)
        assertTrue(restored.settings.sidebarOnRight)
        assertEquals(state.data.spaces, restored.spaces)
        assertEquals(state.data.tabs, restored.tabs)
        assertEquals(state.data.bookmarks, restored.bookmarks)
        assertEquals(state.data.siteSettings, restored.siteSettings)
        assertEquals(60, restored.settings.autoUnloadMinutes)
        assertTrue(restored.settings.httpsOnly)
        assertTrue(restored.settings.onboardingDone)

        assertTrue(runCatching { Backup.decode("{}") }.isFailure)
        assertTrue(runCatching { Backup.decode("garbage") }.isFailure)
    }

    @Test
    fun `backups drop invalid rows`() {
        val json = """
            {"app":"Lax","format":1,
             "spaces":[{"id":"s","name":"S","containerId":"c","position":0,"color":0}],
             "containers":[{"id":"c","name":"C","color":0,"createdAt":0}],
             "tabs":[{"id":"t1","spaceId":"s","containerId":"c","url":"javascript:alert(1)","kind":"WEB"},
                     {"id":"t2","spaceId":"s","containerId":"c","url":"https://ok.example","kind":"WEB"}],
             "bookmarks":[{"id":"b","url":"file:///etc/passwd"}]}
        """.trimIndent()
        val data = Backup.decode(json)
        assertEquals(listOf("t2"), data.tabs.map { it.id })
        assertTrue(data.bookmarks.isEmpty())
    }

    @Test
    fun `release versions compare numerically`() {
        assertTrue(Versions.isNewer("v1.10.0", "1.9.2"))
        assertTrue(Versions.isNewer("1.0.1", "1.0"))
        assertFalse(Versions.isNewer("1.0.0", "1.0.0"))
        assertFalse(Versions.isNewer("0.9", "1.0.0-beta"))
    }
}
