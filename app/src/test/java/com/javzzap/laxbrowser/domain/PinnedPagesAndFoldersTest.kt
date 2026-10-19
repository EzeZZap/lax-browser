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

class PinnedPagesAndFoldersTest {
    private val ops = BrowserOperations(FakeClock(), SequentialIds())

    private fun Reduction.id() = (outcome as Outcome.Created).id

    @Test
    fun `pinning remembers the page and reset goes back to it`() {
        var state = initialState(ops)
        val tab = ops.newTab(state, "https://mail.example/inbox").also { state = it.state }.id()
        state = state.then { ops.pinTab(it, tab) }
        assertEquals("https://mail.example/inbox", state.tab(tab)!!.pinnedUrl)

        state = state.then { ops.onLocationChange(it, tab, "https://mail.example/message/42") }
        val reset = ops.resetPinnedTab(state, tab)
        assertEquals(EngineCommand.Load(tab, "https://mail.example/inbox"), reset.commands.single())
        assertEquals("https://mail.example/inbox", reset.state.tab(tab)!!.url)
    }

    @Test
    fun `the saved page can be replaced with the current one or edited`() {
        var state = initialState(ops)
        val tab = ops.newTab(state, "https://a.example").also { state = it.state }.id()
        state = state.then { ops.pinTab(it, tab) }.then { ops.onLocationChange(it, tab, "https://a.example/settings") }
        state = state.then { ops.setPinnedUrl(it, tab, null) }
        assertEquals("https://a.example/settings", state.tab(tab)!!.pinnedUrl)

        state = state.then { ops.setPinnedUrl(it, tab, "b.example/home") }
        assertEquals("https://b.example/home", state.tab(tab)!!.pinnedUrl)
        assertTrue(ops.setPinnedUrl(state, tab, "not a url").isRejected)
    }

    @Test
    fun `unpinning forgets the page and the folder, Essentials keep a page`() {
        var state = initialState(ops)
        val tab = ops.newTab(state, "https://a.example").also { state = it.state }.id()
        val folder = ops.createTabFolder(state, "Work").also { state = it.state }.id()
        state = state.then { ops.moveTabToFolder(it, tab, folder) }.then { ops.unpinTab(it, tab) }
        assertNull(state.tab(tab)!!.pinnedUrl)
        assertNull(state.tab(tab)!!.folderId)

        state = state.then { ops.addToEssentials(it, tab) }
        assertEquals("https://a.example", state.tab(tab)!!.pinnedUrl)
        assertTrue(ops.setPinnedUrl(state, tab, "https://c.example").let { !it.isRejected })
    }

    @Test
    fun `ordinary tabs have no saved page to reset to`() {
        var state = initialState(ops)
        val tab = ops.newTab(state, "https://a.example").also { state = it.state }.id()
        assertTrue(ops.resetPinnedTab(state, tab).isRejected)
        assertTrue(ops.setPinnedUrl(state, tab, null).isRejected)
    }

    @Test
    fun `moving a tab into a folder pins it and folders stay in their Space`() {
        var state = initialState(ops)
        val tab = ops.newTab(state, "https://a.example").also { state = it.state }.id()
        val folder = ops.createTabFolder(state, "Reading").also { state = it.state }.id()
        state = state.then { ops.moveTabToFolder(it, tab, folder) }
        val moved = state.tab(tab)!!
        assertTrue(moved.isPinned)
        assertEquals(folder, moved.folderId)
        assertEquals(listOf(tab), state.tabsInFolder(folder).map { it.id })
        assertTrue(state.loosePinnedTabs(state.activeSpace!!.id).isEmpty())

        val other = ops.createSpace(state, "Other", 0, ContainerChoice.New("Other", 0)).also { state = it.state }.id()
        val otherTab = ops.newTab(state, "https://b.example").also { state = it.state }.id()
        assertTrue("a folder only accepts tabs of its own Space", ops.moveTabToFolder(state, otherTab, folder).isRejected)
        state = state.then { ops.switchSpace(it, state.data.spaces.first { s -> s.id != other }.id) }
        state = state.then { ops.moveTabToSpace(it, tab, other) }
        assertNull("moving to another Space leaves the folder", state.tab(tab)!!.folderId)
    }

    @Test
    fun `removing a folder keeps its tabs pinned`() {
        var state = initialState(ops)
        val a = ops.newTab(state, "https://a.example").also { state = it.state }.id()
        val b = ops.newTab(state, "https://b.example").also { state = it.state }.id()
        val folder = ops.createTabFolder(state, "Group").also { state = it.state }.id()
        state = state.then { ops.moveTabToFolder(it, a, folder) }.then { ops.moveTabToFolder(it, b, folder) }
        state = state.then { ops.deleteTabFolder(it, folder) }
        assertTrue(state.data.tabFolders.isEmpty())
        assertEquals(setOf(a, b), state.loosePinnedTabs(state.activeSpace!!.id).map { it.id }.toSet())
        assertTrue(state.data.tabs.filter { it.id in setOf(a, b) }.all { it.isPinned && it.folderId == null })
    }

    @Test
    fun `folders collapse, rename, reorder, and go away with their Space`() {
        var state = initialState(ops)
        val first = ops.createTabFolder(state, "One").also { state = it.state }.id()
        val second = ops.createTabFolder(state, "Two").also { state = it.state }.id()
        state = state.then { ops.setTabFolderCollapsed(it, first, true) }
            .then { ops.renameTabFolder(it, second, "Second") }
            .then { ops.moveTabFolder(it, second, -1) }
        val spaceId = state.activeSpace!!.id
        assertEquals(listOf(second, first), state.tabFolders(spaceId).map { it.id })
        assertTrue(state.tabFolder(first)!!.collapsed)
        assertEquals("Second", state.tabFolder(second)!!.name)
        assertTrue(ops.renameTabFolder(state, first, " ").isRejected)

        state = state.then { ops.createSpace(it, "Spare", 0, ContainerChoice.New("Spare", 0)) }
        state = state.then { ops.deleteSpace(it, spaceId) }
        assertTrue(state.data.tabFolders.isEmpty())
    }

    @Test
    fun `restoration takes tabs out of folders that do not exist in their Space`() {
        var state = initialState(ops)
        val tab = ops.newTab(state, "https://a.example").also { state = it.state }.id()
        state = state.then { ops.pinTab(it, tab) }
        val data = state.data.copy(tabs = state.data.tabs.map { if (it.id == tab) it.copy(folderId = "missing") else it })
        val restored = Restoration.restore(data)
        assertNull(restored.tab(tab)!!.folderId)
        assertTrue(restored.tab(tab)!!.isPinned)
    }

    @Test
    fun `Spaces take an emoji or a custom color, and swipes wrap around`() {
        var state = initialState(ops)
        val first = state.activeSpace!!.id
        state = state.then { ops.setSpaceEmoji(it, first, "🚀") }
        assertEquals("🚀", state.space(first)!!.emoji)
        assertTrue(ops.setSpaceEmoji(state, first, "abc").isRejected)
        state = state.then { ops.setSpaceEmoji(it, first, null) }
        assertNull(state.space(first)!!.emoji)

        state = state.then { ops.setSpaceColor(it, first, 0x123456) }
        assertEquals(0xFF123456.toInt(), state.space(first)!!.color)

        val second = ops.createSpace(state, "Second", 0, ContainerChoice.New("Second", 0)).also { state = it.state }.id()
        assertEquals(second, state.activeSpace!!.id)
        state = state.then { ops.switchSpaceBy(it, 1) }
        assertEquals(first, state.activeSpace!!.id)
        state = state.then { ops.switchSpaceBy(it, -1) }
        assertEquals(second, state.activeSpace!!.id)
        assertFalse(ops.switchSpaceBy(state.copy(privateMode = true), 1).state.activeSpace!!.id != second)
    }
}
