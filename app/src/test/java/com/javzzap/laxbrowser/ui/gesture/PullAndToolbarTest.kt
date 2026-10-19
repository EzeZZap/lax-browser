package com.javzzap.laxbrowser.ui.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PullAndToolbarTest {
    private val pull = PullToRefreshDetector(PullToRefreshConfig(touchSlopPx = 10f, triggerDistancePx = 100f, maxPullPx = 150f))

    @Test
    fun `pulling down at the top refreshes after the trigger distance`() {
        pull.onDown(500f, 300f, enabled = true)
        assertEquals(PullEvent.Started, pull.onMove(502f, 320f, canOverscrollTop = true))
        assertEquals(PullEvent.Progress(50f, 0.5f), pull.onMove(502f, 420f, true))
        assertEquals(PullEvent.Progress(150f, 1f), pull.onMove(502f, 900f, true))
        assertEquals(PullEvent.Refresh, pull.onUp())
    }

    @Test
    fun `a short pull cancels`() {
        pull.onDown(500f, 300f, enabled = true)
        pull.onMove(500f, 320f, true)
        pull.onMove(500f, 400f, true)
        assertEquals(PullEvent.Cancelled, pull.onUp())
    }

    @Test
    fun `no pull when the page can scroll up, is disabled, or the drag is horizontal`() {
        pull.onDown(500f, 300f, enabled = true)
        assertEquals(PullEvent.None, pull.onMove(500f, 330f, canOverscrollTop = false))
        assertFalse(pull.isDragging)

        pull.onDown(500f, 300f, enabled = false)
        assertEquals(PullEvent.None, pull.onMove(500f, 400f, true))

        pull.onDown(500f, 300f, enabled = true)
        assertEquals(PullEvent.None, pull.onMove(560f, 320f, true))
        assertEquals(PullEvent.None, pull.onMove(560f, 400f, true))
        assertFalse(pull.isDragging)
    }

    @Test
    fun `waits while gecko has not answered yet`() {
        pull.onDown(500f, 300f, enabled = true)
        assertEquals(PullEvent.None, pull.onMove(500f, 330f, canOverscrollTop = null))
        assertEquals(PullEvent.Started, pull.onMove(500f, 340f, canOverscrollTop = true))
        assertTrue(pull.isDragging)
    }

    @Test
    fun `toolbar hides while scrolling down and returns when scrolling up or at the top`() {
        val scroll = ToolbarScroll()
        var hidden = scroll.onScroll(0, 0f, 100f)
        assertEquals(0f, hidden)
        hidden = scroll.onScroll(40, hidden, 100f)
        assertEquals(40f, hidden)
        hidden = scroll.onScroll(400, hidden, 100f)
        assertEquals(100f, hidden)
        hidden = scroll.onScroll(370, hidden, 100f)
        assertEquals(70f, hidden)
        hidden = scroll.onScroll(0, hidden, 100f)
        assertEquals(0f, hidden)
    }

    @Test
    fun `toolbar ignores the first event after a reset and snaps to the nearest end`() {
        val scroll = ToolbarScroll()
        scroll.onScroll(500, 0f, 100f)
        scroll.reset()
        assertEquals(30f, scroll.onScroll(900, 30f, 100f))
        assertEquals(100f, ToolbarScroll.snapTarget(60f, 100f))
        assertEquals(0f, ToolbarScroll.snapTarget(40f, 100f))
    }
}
