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

class PrivateBrowsingTest {
    private val ops = BrowserOperations(FakeClock(), SequentialIds())

    private fun Reduction.id() = (outcome as Outcome.Created).id

    @Test
    fun `private tabs live outside persisted data and outside spaces`() {
        var state = initialState(ops).then { ops.newTab(it, "https://normal.example") }
        val dataBefore = state.data
        state = state.then { ops.setPrivateMode(it, true) }
        val tab = ops.newTab(state, "https://secret.example").also { state = it.state }.id()

        val private = state.tab(tab)!!
        assertTrue(private.isPrivate)
        assertNull(private.spaceId)
        assertEquals(PRIVATE_CONTEXT_ID, private.containerId)
        assertEquals(dataBefore.tabs, state.data.tabs)
        assertTrue(DataDiff.between(dataBefore, state.data).isEmpty)
        assertEquals(tab, state.activeTab!!.id)
        assertTrue(state.runtime(tab).loaded)
        assertFalse(state.isBlocked(private))
    }

    @Test
    fun `navigation, titles and favicons in private tabs never touch saved data`() {
        var state = initialState(ops).then { ops.addBookmark(it, "https://secret.example", "Secret") }
        val dataBefore = state.data
        state = state.then { ops.setPrivateMode(it, true) }
        val tab = ops.newTab(state, null).also { state = it.state }.id()
        state = state
            .then { ops.navigate(it, tab, "secret.example") }
            .then { ops.onTitleChange(it, tab, "Secret page") }
            .then { ops.onFavicon(it, tab, "https://secret.example", "https://secret.example/icon.png") }

        assertEquals("Secret page", state.tab(tab)!!.title)
        assertEquals("https://secret.example/icon.png", state.tab(tab)!!.faviconUrl)
        assertNull("bookmarks are not updated from private browsing", state.data.bookmarks.single().faviconUrl)
        assertTrue(DataDiff.between(dataBefore, state.data).isEmpty)
    }

    @Test
    fun `private mode hides spaces and keeps each side's selection`() {
        var state = initialState(ops)
        val normal = ops.newTab(state, "https://normal.example").also { state = it.state }.id()
        state = state.then { ops.setPrivateMode(it, true) }
        assertNull(state.activeTab)
        val private = ops.newTab(state, "https://private.example").also { state = it.state }.id()

        assertEquals(Outcome.Rejected(RejectReason.NOT_ALLOWED), ops.selectTab(state, normal).outcome)
        state = state.then { ops.setPrivateMode(it, false) }
        assertEquals(normal, state.activeTab!!.id)
        assertEquals(Outcome.Rejected(RejectReason.NOT_ALLOWED), ops.selectTab(state, private).outcome)
        assertTrue("private tabs stay open while hidden", state.runtime(private).loaded)
    }

    @Test
    fun `private tabs cannot be pinned, made essential, or moved to a space`() {
        var state = initialState(ops).then { ops.setPrivateMode(it, true) }
        val tab = ops.newTab(state, "https://private.example").also { state = it.state }.id()
        val space = state.activeSpace!!.id
        assertEquals(Outcome.Rejected(RejectReason.NOT_ALLOWED), ops.pinTab(state, tab).outcome)
        assertEquals(Outcome.Rejected(RejectReason.NOT_ALLOWED), ops.addToEssentials(state, tab).outcome)
        assertEquals(Outcome.Rejected(RejectReason.NOT_ALLOWED), ops.moveTabToSpace(state, tab, space).outcome)
        assertEquals(Outcome.Rejected(RejectReason.NOT_ALLOWED), ops.restoreClosedTab(state, state.tab(tab)!!).outcome)
    }

    @Test
    fun `closing private tabs selects a neighbour and close all returns to spaces`() {
        var state = initialState(ops).then { ops.setPrivateMode(it, true) }
        val a = ops.newTab(state, "https://a.example").also { state = it.state }.id()
        val b = ops.newTab(state, "https://b.example").also { state = it.state }.id()
        state = state.then { ops.closeTab(it, b) }
        assertEquals(a, state.activeTab!!.id)
        assertNull(state.tab(b))

        ops.newTab(state, "https://c.example").also { state = it.state }
        state = state.then { ops.closeAllPrivateTabs(it) }
        assertFalse(state.privateMode)
        assertTrue(state.privateTabs.isEmpty())
        assertTrue(state.runtime.keys.none { it == a })
    }

    @Test
    fun `windows opened by private pages stay private`() {
        var state = initialState(ops).then { ops.setPrivateMode(it, true) }
        val opener = ops.newTab(state, "https://a.example").also { state = it.state }.id()
        assertEquals(PRIVATE_CONTEXT_ID, ops.contentTabContainer(state, opener))
        state = state.then { ops.onContentOpenedTab(it, "popup", opener, "https://a.example/popup") }
        assertTrue(state.tab("popup")!!.isPrivate)
        assertEquals("popup", state.activeTab!!.id)
        assertTrue(state.data.tabs.none { it.id == "popup" })
    }

    @Test
    fun `the time based new tab page leaves private browsing alone`() {
        var state = initialState(ops).then { ops.onBackground(it, 0L) }.then { ops.setPrivateMode(it, true) }
        val before = state
        state = ops.onForeground(state, now = LaunchPolicy.NEW_TAB_PAGE_THRESHOLD_MS * 5).state
        assertEquals(before, state)
    }
}
