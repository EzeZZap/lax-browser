package com.javzzap.laxbrowser.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestorationTest {
    private val container = Container("c1", "Personal", 0, 0)
    private val space = Space("s1", "Personal", "c1", 0, 0, "t1")

    private fun tab(id: String, spaceId: String?, containerId: String = "c1", essential: Boolean = false, pinned: Boolean = false) =
        Tab(id, spaceId, containerId, "https://$id.example", id, null, 0, TabKind.WEB, pinned, essential, 0, 0)

    @Test
    fun `tabs of a missing space are quarantined, not reassigned`() {
        val data = BrowserData(
            containers = listOf(container),
            spaces = listOf(space),
            tabs = listOf(tab("t1", "s1"), tab("orphan", "deleted-space")),
            settings = BrowserSettings(activeSpaceId = "s1"),
        )
        val state = Restoration.restore(data)
        assertEquals(setOf("orphan"), state.quarantinedTabIds)
        assertNull(state.tab("orphan"))
        assertTrue("the row is kept for later recovery", state.data.tabs.any { it.id == "orphan" })
        assertEquals(listOf("t1"), state.regularTabs("s1").map { it.id })
    }

    @Test
    fun `invalid selection and active space are repaired`() {
        val data = BrowserData(
            containers = listOf(container),
            spaces = listOf(space.copy(selectedTabId = "gone")),
            tabs = listOf(tab("t1", "s1")),
            settings = BrowserSettings(activeSpaceId = "missing"),
        )
        val state = Restoration.restore(data)
        assertEquals("s1", state.activeSpace!!.id)
        assertNull(state.activeSpace!!.selectedTabId)
    }

    @Test
    fun `a missing container blocks its space and tabs instead of attaching them elsewhere`() {
        val data = BrowserData(
            containers = emptyList(),
            spaces = listOf(space),
            tabs = listOf(tab("t1", "s1")),
            settings = BrowserSettings(activeSpaceId = "s1"),
        )
        val state = Restoration.restore(data)
        assertTrue(state.isSpaceBlocked(state.activeSpace!!))
        assertTrue(state.isBlocked(state.tab("t1")!!))
        assertEquals("c1", state.tab("t1")!!.containerId)
    }

    @Test
    fun `restored tabs start unloaded and pinned flags survive`() {
        val data = BrowserData(
            containers = listOf(container),
            spaces = listOf(space),
            tabs = listOf(tab("t1", "s1", pinned = true), tab("e1", null, essential = true)),
            settings = BrowserSettings(activeSpaceId = "s1"),
        )
        val state = Restoration.restore(data)
        assertFalse(state.runtime("t1").loaded)
        assertTrue(state.tab("t1")!!.isPinned)
        assertEquals(listOf("e1"), state.essentials.map { it.id })
        assertEquals(TabLifecycle.UNLOADED, state.lifecycle(state.tab("t1")!!))
    }

    @Test
    fun `an essential that also claims a space is quarantined`() {
        val data = BrowserData(
            containers = listOf(container),
            spaces = listOf(space.copy(selectedTabId = null)),
            tabs = listOf(tab("bad", "s1", essential = true)),
        )
        assertEquals(setOf("bad"), Restoration.restore(data).quarantinedTabIds)
    }
}
