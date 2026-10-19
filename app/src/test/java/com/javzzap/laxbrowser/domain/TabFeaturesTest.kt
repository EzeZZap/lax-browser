package com.javzzap.laxbrowser.domain

import com.javzzap.laxbrowser.testing.FakeClock
import com.javzzap.laxbrowser.testing.SequentialIds
import com.javzzap.laxbrowser.testing.initialState
import com.javzzap.laxbrowser.testing.then
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabFeaturesTest {
    private val ops = BrowserOperations(FakeClock(), SequentialIds())

    private fun Reduction.id() = (outcome as Outcome.Created).id

    @Test
    fun `a closed tab is reopened with its id, space, container and position`() {
        var state = initialState(ops)
        val a = ops.newTab(state, "https://a.example").also { state = it.state }.id()
        val b = ops.newTab(state, "https://b.example").also { state = it.state }.id()
        val closed = state.tab(a)!!
        state = state.then { ops.closeTab(it, a) }
        assertNull(state.tab(a))

        val reopened = state.then { ops.restoreClosedTab(it, closed) }
        val tab = reopened.tab(a)!!
        assertEquals(closed.copy(lastAccessedAt = tab.lastAccessedAt), tab)
        assertEquals(listOf(a, b), reopened.regularTabs(closed.spaceId).map { it.id })
        assertEquals(a, reopened.activeTab!!.id)
        assertTrue(reopened.runtime(a).loaded)
    }

    @Test
    fun `a closed tab is not reopened into a space that changed container`() {
        var state = initialState(ops)
        val space = state.activeSpace!!
        val a = ops.newTab(state, "https://a.example").also { state = it.state }.id()
        val closed = state.tab(a)!!
        state = state.then { ops.closeTab(it, a) }
        val other = ops.createContainer(state, "Other", 0).also { state = it.state }.id()
        state = state.then { ops.setSpaceContainer(it, space.id, other) }
        assertEquals(Outcome.Rejected(RejectReason.NOT_ALLOWED), ops.restoreClosedTab(state, closed).outcome)
    }

    @Test
    fun `reopening a tab of another space switches to it`() {
        var state = initialState(ops)
        val personal = state.activeSpace!!
        val a = ops.newTab(state, "https://a.example").also { state = it.state }.id()
        val closed = state.tab(a)!!
        state = state.then { ops.closeTab(it, a) }
            .then { ops.createSpace(it, "Work", 0, ContainerChoice.New("Work", 0)) }
        val reopened = state.then { ops.restoreClosedTab(it, closed) }
        assertEquals(personal.id, reopened.activeSpace!!.id)
        assertEquals(a, reopened.activeTab!!.id)
    }

    @Test
    fun `links open in the active space, in the background without being selected`() {
        var state = initialState(ops)
        val current = ops.newTab(state, "https://a.example").also { state = it.state }.id()
        val r = ops.openLinkInNewTab(state, "https://b.example/page", background = true)
        val opened = r.id()
        assertEquals(current, r.state.activeTab!!.id)
        assertTrue("background tabs start loading", r.state.runtime(opened).loaded)
        assertEquals(state.activeSpace!!.containerId, r.state.tab(opened)!!.containerId)
        assertEquals(Outcome.Rejected(RejectReason.INVALID_URL), ops.openLinkInNewTab(state, "javascript:alert(1)", false).outcome)
    }

    @Test
    fun `desktop mode toggles per tab and reloads live sessions`() {
        var state = initialState(ops)
        val tab = ops.newTab(state, "https://a.example").also { state = it.state }.id()
        val on = ops.toggleDesktopMode(state, tab)
        assertTrue(on.state.runtime(tab).desktopMode)
        assertEquals(listOf(EngineCommand.SetDesktopMode(tab, true)), on.commands)
        val off = ops.toggleDesktopMode(on.state, tab)
        assertFalse(off.state.runtime(tab).desktopMode)

        val unloaded = on.state.then { ops.unloadTab(it, tab) }
        assertTrue("no command without a session", ops.toggleDesktopMode(unloaded, tab).commands.isEmpty())
    }

    @Test
    fun `favicons are recorded on the tab and on matching bookmarks and shortcuts`() {
        var state = initialState(ops)
        val tab = ops.newTab(state, "https://site.example/").also { state = it.state }.id()
        state = state
            .then { ops.addBookmark(it, "https://site.example", "Site") }
            .then { ops.addShortcut(it, "https://site.example/", "Site") }
            .then { ops.addBookmark(it, "https://other.example", "Other") }
        state = state.then { ops.onFavicon(it, tab, "https://site.example/", "https://site.example/icon.png") }

        assertEquals("https://site.example/icon.png", state.tab(tab)!!.faviconUrl)
        assertEquals("https://site.example/icon.png", state.data.bookmarks.first { it.title == "Site" }.faviconUrl)
        assertNull(state.data.bookmarks.first { it.title == "Other" }.faviconUrl)
        assertEquals("https://site.example/icon.png", state.data.shortcuts.single().iconUrl)
    }

    @Test
    fun `favicons from another site or non-web icon urls are ignored, and navigation clears them`() {
        var state = initialState(ops)
        val tab = ops.newTab(state, "https://site.example/").also { state = it.state }.id()
        state = state.then { ops.onFavicon(it, tab, "https://evil.example/", "https://evil.example/i.png") }
        assertNull(state.tab(tab)!!.faviconUrl)
        state = state.then { ops.onFavicon(it, tab, "https://site.example/", "data:image/png;base64,AAAA") }
        assertNull(state.tab(tab)!!.faviconUrl)

        state = state.then { ops.onFavicon(it, tab, "https://site.example/", "https://site.example/i.png") }
        state = state.then { ops.onLocationChange(it, tab, "https://site.example/other") }
        assertEquals("same site keeps its icon", "https://site.example/i.png", state.tab(tab)!!.faviconUrl)
        state = state.then { ops.onLocationChange(it, tab, "https://new.example/") }
        assertNull(state.tab(tab)!!.faviconUrl)
    }
}
