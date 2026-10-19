package com.javzzap.laxbrowser.ui.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeSwipeDetectorTest {
    private val width = 1000f
    private val gestureInset = 40f
    private val config = EdgeSwipeConfig(edgeWidthPx = 60f, touchSlopPx = 10f, commitDistancePx = 200f)
    private val detector = EdgeSwipeDetector(config)

    private fun down(x: Float, y: Float = 500f, back: Boolean = true, forward: Boolean = true) =
        detector.onDown(x, y, width, gestureInset, gestureInset, back, forward)

    @Test
    fun `swipe from the left zone navigates back after the commit distance`() {
        assertTrue(down(50f))
        assertEquals(SwipeEvent.None, detector.onMove(55f, 500f))
        assertEquals(SwipeEvent.Started(SwipeDirection.BACK), detector.onMove(70f, 502f))
        assertEquals(SwipeEvent.Progress(SwipeDirection.BACK, 0.5f), detector.onMove(150f, 505f))
        assertEquals(SwipeEvent.Navigate(SwipeDirection.BACK), detector.onUp(260f))
    }

    @Test
    fun `swipe from the right zone navigates forward`() {
        assertTrue(down(950f))
        assertEquals(SwipeEvent.Started(SwipeDirection.FORWARD), detector.onMove(930f, 500f))
        assertEquals(SwipeEvent.Navigate(SwipeDirection.FORWARD), detector.onUp(700f))
    }

    @Test
    fun `releasing before the commit distance cancels`() {
        down(50f)
        detector.onMove(80f, 500f)
        assertEquals(SwipeEvent.Cancelled, detector.onUp(120f))
    }

    @Test
    fun `no history in a direction disables that zone`() {
        assertFalse(down(50f, back = false))
        assertEquals(SwipeEvent.None, detector.onMove(200f, 500f))
        assertFalse(down(950f, forward = false))
    }

    @Test
    fun `touches inside the system gesture inset are ignored`() {
        assertFalse(down(20f))
        assertFalse(down(985f))
    }

    @Test
    fun `touches outside the edge zones belong to the page`() {
        assertFalse(down(500f))
        assertFalse(down(gestureInset + config.edgeWidthPx + 1f))
    }

    @Test
    fun `vertical scrolling from the edge is not intercepted`() {
        down(50f)
        assertEquals(SwipeEvent.None, detector.onMove(55f, 560f))
        assertFalse(detector.isDragging)
        assertEquals(SwipeEvent.None, detector.onMove(200f, 560f))
    }

    @Test
    fun `outward or diagonal drags are left to the page`() {
        down(80f)
        assertEquals(SwipeEvent.None, detector.onMove(60f, 500f))
        assertFalse(detector.isDragging)

        down(50f)
        assertEquals(SwipeEvent.None, detector.onMove(80f, 530f))
        assertFalse(detector.isDragging)
    }

    @Test
    fun `cancel resets an active drag`() {
        down(50f)
        detector.onMove(80f, 500f)
        assertEquals(SwipeEvent.Cancelled, detector.onCancel())
        assertFalse(detector.isDragging)
    }
}
