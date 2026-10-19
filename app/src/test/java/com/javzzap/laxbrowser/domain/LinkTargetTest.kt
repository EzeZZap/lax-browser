package com.javzzap.laxbrowser.domain

import com.javzzap.laxbrowser.testing.FakeClock
import com.javzzap.laxbrowser.testing.SequentialIds
import com.javzzap.laxbrowser.testing.initialState
import com.javzzap.laxbrowser.testing.then
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkTargetTest {
    private val ops = BrowserOperations(FakeClock(), SequentialIds())

    private fun Reduction.id() = (outcome as Outcome.Created).id

    @Test
    fun `targets round trip through their stored keys`() {
        listOf(LinkTarget.ActiveSpace, LinkTarget.Ask, LinkTarget.PrivateTab, LinkTarget.InSpace("abc")).forEach {
            assertEquals(it, LinkTarget.parse(it.key))
        }
        assertEquals(LinkTarget.ActiveSpace, LinkTarget.parse(null))
        assertEquals(LinkTarget.ActiveSpace, LinkTarget.parse("space:"))
        assertEquals(LinkTarget.ActiveSpace, LinkTarget.parse("something-new"))
    }

    @Test
    fun `a link opens in the chosen Space with that Space's container`() {
        var state = initialState(ops)
        val home = state.activeSpace!!
        val work = ops.createSpace(state, "Work", 0, ContainerChoice.New("Work", 0)).also { state = it.state }.id()
        state = state.then { ops.switchSpace(it, home.id) }
        state = ops.openLinkIn(state, "https://mail.example/", LinkTarget.InSpace(work), applyRules = false).state
        val tab = state.activeTab!!
        assertEquals(work, tab.spaceId)
        assertEquals(state.space(work)!!.containerId, tab.containerId)
        assertEquals("https://mail.example/", tab.url)
    }

    @Test
    fun `a link can open in a private tab, and a missing Space falls back to the active one`() {
        var state = initialState(ops)
        state = ops.openLinkIn(state, "https://secret.example/", LinkTarget.PrivateTab, applyRules = true).state
        assertTrue(state.privateMode)
        assertTrue(state.activeTab!!.isPrivate)
        state = ops.openLinkIn(state, "https://news.example/", LinkTarget.InSpace("gone"), applyRules = true).state
        assertFalse(state.privateMode)
        assertEquals(state.activeSpace!!.id, state.activeTab!!.spaceId)
        assertEquals("https://news.example/", state.activeTab!!.url)
    }

    @Test
    fun `deleting the Space chosen for links resets the setting`() {
        var state = initialState(ops)
        val work = ops.createSpace(state, "Work", 0, ContainerChoice.New("Work", 0)).also { state = it.state }.id()
        state = state.then { ops.updateSettings(it) { s -> s.copy(externalLinks = LinkTarget.InSpace(work)) } }
        state = state.then { ops.deleteSpace(it, work) }
        assertEquals(LinkTarget.ActiveSpace, state.data.settings.externalLinks)
    }
}
