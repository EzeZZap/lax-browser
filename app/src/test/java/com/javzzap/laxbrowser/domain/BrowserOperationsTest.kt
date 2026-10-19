package com.javzzap.laxbrowser.domain

import com.javzzap.laxbrowser.testing.FakeClock
import com.javzzap.laxbrowser.testing.SequentialIds
import com.javzzap.laxbrowser.testing.initialState
import com.javzzap.laxbrowser.testing.then
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserOperationsTest {
    private val clock = FakeClock()
    private val ops = BrowserOperations(clock, SequentialIds())

    private fun BrowserState.createdTab(url: String?): Pair<BrowserState, Tab> {
        val r = ops.newTab(this, url)
        val id = (r.outcome as Outcome.Created).id
        return r.state to r.state.tab(id)!!
    }

    private fun BrowserState.withSecondSpace(name: String = "Work"): Pair<BrowserState, Space> {
        val r = ops.createSpace(this, name, 0, ContainerChoice.New(name, 0))
        val id = (r.outcome as Outcome.Created).id
        return r.state to r.state.space(id)!!
    }

    @Test
    fun `defaults create one space with its own container`() {
        val state = initialState(ops)
        assertEquals(1, state.data.spaces.size)
        assertEquals(1, state.data.containers.size)
        assertEquals(state.data.containers.single().id, state.activeSpace!!.containerId)
    }

    @Test
    fun `new tab inherits the active space and its container`() {
        val (state, work) = initialState(ops).withSecondSpace()
        val (after, tab) = state.createdTab("https://example.com")
        assertEquals(work.id, tab.spaceId)
        assertEquals(work.containerId, tab.containerId)
        assertEquals(tab.id, after.activeTab!!.id)
        assertTrue(after.runtime(tab.id).loaded)
        assertEquals(TabLifecycle.ACTIVE, after.lifecycle(tab))
    }

    @Test
    fun `switching spaces preserves each space's tabs and selection`() {
        val start = initialState(ops)
        val personal = start.activeSpace!!
        val (s1, personalTab) = start.createdTab("https://a.example")
        val (s2, work) = s1.withSecondSpace()
        val (s3, workTab) = s2.createdTab("https://b.example")

        val back = s3.then { ops.switchSpace(it, personal.id) }
        assertEquals(personalTab.id, back.activeTab!!.id)
        assertEquals(listOf(personalTab.id), back.regularTabs(personal.id).map { it.id })
        assertEquals(listOf(workTab.id), back.regularTabs(work.id).map { it.id })
        assertEquals(workTab.id, back.space(work.id)!!.selectedTabId)
        assertTrue("previous space's tab keeps its session", back.runtime(workTab.id).loaded)
        assertEquals(TabLifecycle.OPEN, back.lifecycle(back.tab(workTab.id)!!))
    }

    @Test
    fun `essentials are global and separate from pinned tabs`() {
        val start = initialState(ops)
        val (s1, tab) = start.createdTab("https://mail.example")
        val (s2, pinnedTab) = s1.createdTab("https://docs.example")
        val s3 = s2.then { ops.addToEssentials(it, tab.id) }.then { ops.pinTab(it, pinnedTab.id) }
        val (s4, work) = s3.withSecondSpace()

        val essential = s4.tab(tab.id)!!
        assertTrue(essential.isEssential)
        assertFalse(essential.isPinned)
        assertNull(essential.spaceId)
        assertEquals(listOf(tab.id), s4.essentials.map { it.id })
        assertTrue(s4.pinnedTabs(work.id).isEmpty())
        assertEquals(listOf(pinnedTab.id), s4.pinnedTabs(start.activeSpace!!.id).map { it.id })
        assertTrue(s4.pinnedTabs(start.activeSpace!!.id).none { it.isEssential })

        val selected = s4.then { ops.selectTab(it, tab.id) }
        assertEquals("an Essential can be selected from any space", tab.id, selected.activeTab!!.id)
    }

    @Test
    fun `pinning keeps the tab in its space and does not make it essential`() {
        val (state, tab) = initialState(ops).createdTab("https://example.com")
        val pinned = state.then { ops.pinTab(it, tab.id) }.tab(tab.id)!!
        assertTrue(pinned.isPinned)
        assertFalse(pinned.isEssential)
        assertEquals(tab.spaceId, pinned.spaceId)
    }

    @Test
    fun `unloading a pinned tab preserves record and pin, selecting reloads it`() {
        val (s1, tab) = initialState(ops).createdTab("https://example.com/page")
        val s2 = s1.then { ops.onTitleChange(it, tab.id, "Example") }.then { ops.pinTab(it, tab.id) }
        val unloaded = s2.then { ops.unloadTab(it, tab.id) }

        val record = unloaded.tab(tab.id)!!
        assertTrue(record.isPinned)
        assertEquals("https://example.com/page", record.url)
        assertEquals("Example", record.title)
        assertEquals(tab.spaceId, record.spaceId)
        assertEquals(TabLifecycle.UNLOADED, unloaded.lifecycle(record))

        val reloaded = unloaded.then { ops.selectTab(it, tab.id) }
        assertTrue(reloaded.runtime(tab.id).loaded)
        assertEquals(TabLifecycle.ACTIVE, reloaded.lifecycle(reloaded.tab(tab.id)!!))
    }

    @Test
    fun `closing an ordinary tab removes it without touching pinned tabs or bookmarks`() {
        val (s1, pinned) = initialState(ops).createdTab("https://pinned.example")
        val (s2, ordinary) = s1.then { ops.pinTab(it, pinned.id) }.createdTab("https://ordinary.example")
        val s3 = s2.then { ops.addBookmark(it, ordinary.url, "Ordinary") }

        val r = ops.closeTab(s3, ordinary.id)
        assertNull(r.state.tab(ordinary.id))
        assertNotNull(r.state.tab(pinned.id))
        assertTrue(r.state.tab(pinned.id)!!.isPinned)
        assertEquals(1, r.state.data.bookmarks.size)
        assertTrue(ordinary.id in r.discardSessionState)
        assertEquals("selection falls back to a neighbour", pinned.id, r.state.activeTab!!.id)
    }

    @Test
    fun `closing an essential unloads it instead of deleting it`() {
        val (s1, tab) = initialState(ops).createdTab("https://mail.example")
        val s2 = s1.then { ops.addToEssentials(it, tab.id) }
        val closed = s2.then { ops.closeTab(it, tab.id) }
        assertNotNull(closed.tab(tab.id))
        assertTrue(closed.tab(tab.id)!!.isEssential)
        assertFalse(closed.runtime(tab.id).loaded)

        val removed = closed.then { ops.removeEssential(it, tab.id) }
        assertNull(removed.tab(tab.id))
    }

    @Test
    fun `moving a tab to a space with another container unloads it and discards history`() {
        val start = initialState(ops)
        val personal = start.activeSpace!!
        val (s1, tab) = start.createdTab("https://example.com")
        val (s2, work) = s1.withSecondSpace()
        val s3 = s2.then { ops.switchSpace(it, personal.id) }

        val r = ops.moveTabToSpace(s3, tab.id, work.id)
        val moved = r.state.tab(tab.id)!!
        assertEquals(work.id, moved.spaceId)
        assertEquals(work.containerId, moved.containerId)
        assertNotEquals(personal.containerId, moved.containerId)
        assertFalse(r.state.runtime(tab.id).loaded)
        assertEquals(setOf(tab.id), r.discardSessionState)
        assertNull(r.state.space(personal.id)!!.selectedTabId)
        assertEquals("URL is kept so it can reload in the new container", "https://example.com", moved.url)
    }

    @Test
    fun `moving a tab between spaces sharing a container keeps its session`() {
        val start = initialState(ops)
        val personal = start.activeSpace!!
        val (s1, tab) = start.createdTab("https://example.com")
        val r1 = ops.createSpace(s1, "Shared", 0, ContainerChoice.Existing(personal.containerId))
        val shared = (r1.outcome as Outcome.Created).id
        val s2 = r1.state.then { ops.switchSpace(it, personal.id) }

        val r = ops.moveTabToSpace(s2, tab.id, shared)
        assertTrue(r.state.runtime(tab.id).loaded)
        assertTrue(r.discardSessionState.isEmpty())
    }

    @Test
    fun `active tab always belongs to the active space or is an essential`() {
        val start = initialState(ops)
        val (s1, tab) = start.createdTab("https://example.com")
        val (s2, work) = s1.withSecondSpace()
        val rejected = ops.selectTab(s2, tab.id)
        assertEquals(Outcome.Rejected(RejectReason.NOT_ALLOWED), rejected.outcome)
        assertEquals(work.id, s2.activeSpace!!.id)
        assertNull(s2.activeTab)
    }

    @Test
    fun `deleting a space removes its tabs but keeps the container and essentials`() {
        val start = initialState(ops)
        val (s1, essential) = start.createdTab("https://mail.example")
        val s2 = s1.then { ops.addToEssentials(it, essential.id) }
        val (s3, work) = s2.withSecondSpace()
        val (s4, workTab) = s3.createdTab("https://work.example")
        val s5 = s4.then { ops.pinTab(it, workTab.id) }

        val r = ops.deleteSpace(s5, work.id)
        assertNull(r.state.space(work.id))
        assertNull(r.state.tab(workTab.id))
        assertNotNull(r.state.container(work.containerId))
        assertNotNull(r.state.tab(essential.id))
        assertEquals(start.activeSpace!!.id, r.state.activeSpace!!.id)
        assertTrue(workTab.id in r.discardSessionState)
    }

    @Test
    fun `the last space cannot be deleted`() {
        val state = initialState(ops)
        assertEquals(Outcome.Rejected(RejectReason.LAST_SPACE), ops.deleteSpace(state, state.activeSpace!!.id).outcome)
    }

    @Test
    fun `a container in use cannot be deleted and an unused one clears its data`() {
        val state = initialState(ops)
        val used = state.activeSpace!!.containerId
        assertEquals(Outcome.Rejected(RejectReason.CONTAINER_IN_USE), ops.deleteContainer(state, used).outcome)

        val created = ops.createContainer(state, "Spare", 0)
        val spare = (created.outcome as Outcome.Created).id
        val r = ops.deleteContainer(created.state, spare)
        assertNull(r.state.container(spare))
        assertEquals(listOf(EngineCommand.ClearContainerData(spare)), r.commands)
    }

    @Test
    fun `changing a space's container moves all its tabs explicitly`() {
        val start = initialState(ops)
        val space = start.activeSpace!!
        val (s1, a) = start.createdTab("https://a.example")
        val (s2, b) = s1.createdTab("https://b.example")
        val created = ops.createContainer(s2, "Other", 0)
        val other = (created.outcome as Outcome.Created).id

        val r = ops.setSpaceContainer(created.state, space.id, other)
        assertEquals(other, r.state.space(space.id)!!.containerId)
        assertTrue(r.state.data.tabs.filter { it.spaceId == space.id }.all { it.containerId == other })
        assertEquals(setOf(a.id, b.id), r.discardSessionState)
        assertFalse(r.state.runtime(a.id).loaded)
        assertTrue("the selected tab reloads in the new container", r.state.runtime(b.id).loaded)
    }

    @Test
    fun `content-opened tabs use the opener's space and container`() {
        val (s1, opener) = initialState(ops).createdTab("https://example.com")
        assertEquals(opener.containerId, ops.contentTabContainer(s1, opener.id))
        val r = ops.onContentOpenedTab(s1, "popup", opener.id, "https://example.com/popup")
        val popup = r.state.tab("popup")!!
        assertEquals(opener.spaceId, popup.spaceId)
        assertEquals(opener.containerId, popup.containerId)
        assertEquals("popup", r.state.activeTab!!.id)
    }

    @Test
    fun `essentials cannot open content windows in their own container`() {
        val (s1, tab) = initialState(ops).createdTab("https://mail.example")
        val s2 = s1.then { ops.addToEssentials(it, tab.id) }
        assertNull(ops.contentTabContainer(s2, tab.id))
    }

    @Test
    fun `navigating an unloaded tab discards stale history, a live one issues a load`() {
        val (s1, tab) = initialState(ops).createdTab("https://a.example")
        val live = ops.navigate(s1, tab.id, "b.example")
        assertEquals(listOf(EngineCommand.Load(tab.id, "https://b.example")), live.commands)

        val unloaded = s1.then { ops.unloadTab(it, tab.id) }
        val r = ops.navigate(unloaded, tab.id, "c.example")
        assertTrue(r.commands.isEmpty())
        assertEquals(setOf(tab.id), r.discardSessionState)
        assertEquals("https://c.example", r.state.tab(tab.id)!!.url)
        assertTrue(r.state.runtime(tab.id).loaded)
    }

    @Test
    fun `navigating from the new tab page turns it into a web tab in place`() {
        val (s1, ntp) = initialState(ops).createdTab(null)
        assertEquals(TabKind.NEW_TAB, ntp.kind)
        val after = s1.then { ops.navigate(it, ntp.id, "example.com") }
        assertEquals(TabKind.WEB, after.tab(ntp.id)!!.kind)
        assertEquals(1, after.data.tabs.size)
    }

    @Test
    fun `about blank does not overwrite a stored url`() {
        val (s1, tab) = initialState(ops).createdTab("https://example.com")
        val after = ops.onLocationChange(s1, tab.id, "about:blank").state
        assertEquals("https://example.com", after.tab(tab.id)!!.url)
    }

    @Test
    fun `bookmarks reject invalid urls and do not duplicate`() {
        val state = initialState(ops)
        assertEquals(Outcome.Rejected(RejectReason.INVALID_URL), ops.addBookmark(state, "not a url", null).outcome)
        assertEquals(Outcome.Rejected(RejectReason.INVALID_URL), ops.addBookmark(state, "javascript:alert(1)", null).outcome)

        val first = ops.addBookmark(state, "https://example.com/", null)
        val id = (first.outcome as Outcome.Created).id
        assertEquals("example.com", first.state.data.bookmarks.single().title)
        val again = ops.addBookmark(first.state, "https://example.com", "Other")
        assertEquals(Outcome.AlreadyExists(id), again.outcome)
        assertEquals(1, again.state.data.bookmarks.size)
    }

    @Test
    fun `opening a bookmark uses the active space's container`() {
        val (s1, work) = initialState(ops).withSecondSpace()
        val s2 = s1.then { ops.addBookmark(it, "https://example.com", "Example") }
        val r = ops.openBookmark(s2, s2.data.bookmarks.single().id)
        val tab = r.state.activeTab!!
        assertEquals(work.id, tab.spaceId)
        assertEquals(work.containerId, tab.containerId)
        assertEquals(1, r.state.data.bookmarks.size)
    }

    @Test
    fun `removing a tab never removes its bookmark`() {
        val (s1, tab) = initialState(ops).createdTab("https://example.com")
        val s2 = s1.then { ops.addBookmark(it, tab.url, tab.title) }.then { ops.closeTab(it, tab.id) }
        assertEquals(1, s2.data.bookmarks.size)
    }

    @Test
    fun `shortcuts can be added, edited, reordered and removed and are not tabs`() {
        var state = initialState(ops)
        state = state.then { ops.addShortcut(it, "a.example", "A") }.then { ops.addShortcut(it, "b.example", "B") }
        assertTrue(state.data.tabs.isEmpty())
        val (a, b) = state.data.shortcuts.sortedBy { it.position }
        state = state.then { ops.moveShortcut(it, b.id, -1) }
        assertEquals(listOf(b.id, a.id), state.data.shortcuts.sortedBy { it.position }.map { it.id })
        state = state.then { ops.updateShortcut(it, a.id, "A2", "https://a2.example") }
        assertEquals("https://a2.example", state.data.shortcuts.first { it.id == a.id }.url)
        state = state.then { ops.removeShortcut(it, b.id) }
        assertEquals(listOf(a.id), state.data.shortcuts.map { it.id })

        val opened = state.then { ops.openShortcut(it, a.id) }
        assertEquals("https://a2.example", opened.activeTab!!.url)
        assertFalse(opened.activeTab!!.isPinned)
        assertFalse(opened.activeTab!!.isEssential)
    }

    @Test
    fun `tabs reorder within their own list`() {
        val start = initialState(ops)
        val (s1, a) = start.createdTab("https://a.example")
        val (s2, b) = s1.createdTab("https://b.example")
        val moved = s2.then { ops.moveTab(it, b.id, -1) }
        assertEquals(listOf(b.id, a.id), moved.regularTabs(start.activeSpace!!.id).map { it.id })
    }

    @Test
    fun `blocked spaces cannot open tabs`() {
        val state = initialState(ops)
        val orphan = state.copy(data = state.data.copy(containers = emptyList()))
        assertEquals(Outcome.Rejected(RejectReason.CONTAINER_MISSING), ops.newTab(orphan, "https://example.com").outcome)
    }
}
