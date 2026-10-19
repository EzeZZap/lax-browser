package com.javzzap.laxbrowser.domain

import com.javzzap.laxbrowser.testing.FakeClock
import com.javzzap.laxbrowser.testing.SequentialIds
import com.javzzap.laxbrowser.testing.initialState
import com.javzzap.laxbrowser.testing.then
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitAndGlanceTest {
    private val ops = BrowserOperations(FakeClock(), SequentialIds())

    private fun Reduction.id() = (outcome as Outcome.Created).id

    @Test
    fun `split view shows two tabs of the Space and both stay loaded`() {
        var state = initialState(ops)
        val left = ops.newTab(state, "https://left.example").also { state = it.state }.id()
        val right = ops.newTab(state, "https://right.example").also { state = it.state }.id()
        state = state.then { ops.selectTab(it, left) }.then { ops.unloadTab(it, right) }
        state = state.then { ops.openSplit(it, right) }
        assertEquals(left, state.activeTab!!.id)
        assertEquals(right, state.splitTab!!.id)
        assertEquals(setOf(left, right), state.visibleTabIds)
        assertTrue(state.runtime(right).loaded)
    }

    @Test
    fun `selecting the other half moves focus and keeps the split`() {
        var state = initialState(ops)
        val left = ops.newTab(state, "https://left.example").also { state = it.state }.id()
        val right = ops.newTab(state, "https://right.example").also { state = it.state }.id()
        state = state.then { ops.selectTab(it, left) }.then { ops.openSplit(it, right) }
        state = state.then { ops.selectTab(it, right) }
        assertEquals(right, state.activeTab!!.id)
        assertEquals(left, state.splitTab!!.id)
    }

    @Test
    fun `closing the focused half leaves the other half on screen`() {
        var state = initialState(ops)
        val left = ops.newTab(state, "https://left.example").also { state = it.state }.id()
        val right = ops.newTab(state, "https://right.example").also { state = it.state }.id()
        state = state.then { ops.selectTab(it, left) }.then { ops.openSplit(it, right) }
        state = state.then { ops.closeTab(it, left) }
        assertEquals(right, state.activeTab!!.id)
        assertNull(state.splitTab)
    }

    @Test
    fun `unloading the second half ends split view and it does not come back`() {
        var state = initialState(ops)
        val left = ops.newTab(state, "https://left.example").also { state = it.state }.id()
        val right = ops.newTab(state, "https://right.example").also { state = it.state }.id()
        state = state.then { ops.selectTab(it, left) }.then { ops.openSplit(it, right) }.then { ops.unloadTab(it, right) }
        assertNull(state.splitTab)
        state = state.then { ops.selectTab(it, right) }.then { ops.selectTab(it, left) }
        assertNull(state.splitTab)
    }

    @Test
    fun `split view is per Space and rejects tabs from elsewhere`() {
        var state = initialState(ops)
        val left = ops.newTab(state, "https://left.example").also { state = it.state }.id()
        val right = ops.newTab(state, "https://right.example").also { state = it.state }.id()
        state = state.then { ops.selectTab(it, left) }.then { ops.openSplit(it, right) }
        val first = state.activeSpace!!.id
        val other = ops.createSpace(state, "Other", 0, ContainerChoice.New("Other", 0)).also { state = it.state }.id()
        ops.newTab(state, "https://elsewhere.example").also { state = it.state }
        assertNull("the other Space has no split", state.splitTab)
        assertTrue(ops.openSplit(state, left).isRejected)
        state = state.then { ops.switchSpace(it, first) }
        assertEquals(right, state.splitTab!!.id)
        assertTrue(other.isNotEmpty())
    }

    @Test
    fun `a link opens next to the active tab`() {
        var state = initialState(ops)
        val left = ops.newTab(state, "https://left.example").also { state = it.state }.id()
        val created = ops.openUrlInSplit(state, "https://link.example").also { state = it.state }.id()
        assertEquals(left, state.activeTab!!.id)
        assertEquals(created, state.splitTab!!.id)
        assertTrue(ops.openUrlInSplit(state, "javascript:alert(1)").isRejected)
    }

    @Test
    fun `glance uses the opener's container and becomes a tab next to it`() {
        var state = initialState(ops)
        val opener = ops.newTab(state, "https://opener.example").also { state = it.state }.id()
        assertEquals(state.activeSpace!!.containerId, ops.glanceContainer(state, opener))
        state = state.then { ops.onGlancePromoted(it, "glance", opener, "https://preview.example") }
        val tab = state.tab("glance")!!
        assertEquals(state.activeSpace!!.id, tab.spaceId)
        assertEquals(state.activeSpace!!.containerId, tab.containerId)
        assertEquals("glance", state.activeTab!!.id)
    }

    @Test
    fun `glance from private tabs stays private`() {
        var state = initialState(ops).then { ops.setPrivateMode(it, true) }
        val opener = ops.newTab(state, "https://secret.example").also { state = it.state }.id()
        assertEquals(PRIVATE_CONTEXT_ID, ops.glanceContainer(state, opener))
        state = state.then { ops.onGlancePromoted(it, "glance", opener, "https://preview.example") }
        assertTrue(state.tab("glance")!!.isPrivate)
        assertTrue(state.data.tabs.none { it.id == "glance" })
    }
}
