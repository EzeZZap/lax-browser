package com.javzzap.laxbrowser.domain

import com.javzzap.laxbrowser.testing.FakeClock
import com.javzzap.laxbrowser.testing.SequentialIds
import com.javzzap.laxbrowser.testing.initialState
import com.javzzap.laxbrowser.testing.then
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchPolicyTest {
    private val hour = LaunchPolicy.NEW_TAB_PAGE_THRESHOLD_MS
    private val clock = FakeClock()
    private val ops = BrowserOperations(clock, SequentialIds())

    @Test
    fun `policy opens the NTP without usable state or after an hour`() {
        assertTrue(LaunchPolicy.shouldOpenNewTabPage(hasUsableState = false, lastBackgroundAt = null, now = 0))
        assertFalse(LaunchPolicy.shouldOpenNewTabPage(hasUsableState = true, lastBackgroundAt = null, now = 10 * hour))
        assertFalse(LaunchPolicy.shouldOpenNewTabPage(true, lastBackgroundAt = 0, now = hour - 1))
        assertTrue(LaunchPolicy.shouldOpenNewTabPage(true, lastBackgroundAt = 0, now = hour))
        assertTrue(LaunchPolicy.shouldOpenNewTabPage(true, lastBackgroundAt = 0, now = 5 * hour))
    }

    @Test
    fun `first launch opens a new tab page`() {
        val state = ops.onForeground(initialState(ops), clock.now()).state
        val tab = state.activeTab!!
        assertEquals(TabKind.NEW_TAB, tab.kind)
        assertEquals(1, state.data.tabs.size)
    }

    @Test
    fun `returning within an hour does not create another new tab page`() {
        var state = initialState(ops).then { ops.newTab(it, "https://example.com") }
        state = state.then { ops.onBackground(it, clock.now()) }
        clock.advance(hour - 1)
        val after = ops.onForeground(state, clock.now()).state
        assertEquals(state.data.tabs, after.data.tabs)
        assertEquals(state.activeTab, after.activeTab)
    }

    @Test
    fun `returning after an hour opens a fresh NTP without touching existing tabs`() {
        var state = initialState(ops)
            .then { ops.newTab(it, "https://a.example") }
            .then { ops.newTab(it, "https://b.example") }
        val before = state.data.tabs
        state = state.then { ops.onBackground(it, clock.now()) }
        clock.advance(hour)

        val after = ops.onForeground(state, clock.now()).state
        assertEquals(before.size + 1, after.data.tabs.size)
        assertTrue(after.data.tabs.containsAll(before))
        assertEquals(TabKind.NEW_TAB, after.activeTab!!.kind)
        assertEquals(state.activeSpace!!.id, after.activeTab!!.spaceId)
    }

    @Test
    fun `an untouched NTP is reused instead of piling up`() {
        var state = initialState(ops).then { ops.newTab(it, null) }.then { ops.newTab(it, "https://a.example") }
        state = state.then { ops.onBackground(it, clock.now()) }
        clock.advance(2 * hour)
        val after = ops.onForeground(state, clock.now()).state
        assertEquals(state.data.tabs.size, after.data.tabs.size)
        assertEquals(TabKind.NEW_TAB, after.activeTab!!.kind)
    }
}
