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

class ExtensionTabsTest {
    private val ops = BrowserOperations(FakeClock(), SequentialIds())

    @Test
    fun `extension tabs open in the active space with its container`() {
        val state = initialState(ops)
        val space = state.activeSpace!!
        assertEquals(space.containerId, ops.extensionTabContainer(state))

        val next = ops.onExtensionOpenedTab(state, "ext-tab", "https://welcome.example", select = true).state
        val tab = next.tab("ext-tab")!!
        assertEquals(space.id, tab.spaceId)
        assertEquals(space.containerId, tab.containerId)
        assertFalse(tab.isPrivate || tab.isPinned || tab.isEssential)
        assertEquals("ext-tab", next.activeTab!!.id)
        assertTrue("the engine already created its session", next.runtime("ext-tab").loaded)
    }

    @Test
    fun `background extension tabs keep the current selection`() {
        var state = initialState(ops).then { ops.newTab(it, "https://current.example") }
        val current = state.activeTab!!.id
        state = state.then { ops.onExtensionOpenedTab(it, "ext-tab", "https://other.example", select = false) }
        assertEquals(current, state.activeTab!!.id)
        assertTrue(state.runtime("ext-tab").loaded)
    }

    @Test
    fun `extension tabs never become private and open behind private browsing`() {
        var state = initialState(ops).then { ops.setPrivateMode(it, true) }
        state = state.then { ops.onExtensionOpenedTab(it, "ext-tab", "https://welcome.example", select = true) }
        val tab = state.tab("ext-tab")!!
        assertFalse(tab.isPrivate)
        assertTrue(state.privateMode)
        assertNull("private browsing keeps showing its own tabs", state.activeTab)
        assertTrue(tab in state.data.tabs)
    }

    @Test
    fun `extension tabs are refused when the space has no container`() {
        var state = initialState(ops)
        val containerId = state.activeSpace!!.containerId
        state = state.withData { data -> data.copy(containers = data.containers.filterNot { it.id == containerId }) }
        assertNull(ops.extensionTabContainer(state))
        val reduction = ops.onExtensionOpenedTab(state, "ext-tab", "https://welcome.example", select = true)
        assertTrue(reduction.isRejected)
        assertNull(reduction.state.tab("ext-tab"))
    }

    @Test
    fun `an existing tab id is not reused`() {
        val state = initialState(ops).then { ops.onExtensionOpenedTab(it, "ext-tab", "https://a.example", select = true) }
        val reduction = ops.onExtensionOpenedTab(state, "ext-tab", "https://b.example", select = true)
        assertTrue(reduction.isRejected)
        assertEquals("https://a.example", reduction.state.tab("ext-tab")!!.url)
    }
}
