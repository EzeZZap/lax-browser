package com.javzzap.laxbrowser.ui.gesture

import kotlin.math.abs

enum class SwipeDirection { BACK, FORWARD }

/**
 * Edge zones: a strip of [edgeWidthPx] starting just inside the system gesture inset on each side
 * (left = back, right = forward). Touches inside the system gesture inset itself are ignored.
 *
 * A candidate becomes a swipe once it moves [touchSlopPx] inward while staying within
 * [maxSlope] (|dy| / |dx|). It navigates on release only after [commitDistancePx] of travel.
 */
data class EdgeSwipeConfig(
    val edgeWidthPx: Float,
    val touchSlopPx: Float,
    val commitDistancePx: Float,
    val maxSlope: Float = 0.5f,
)

sealed interface SwipeEvent {
    data object None : SwipeEvent
    data class Started(val direction: SwipeDirection) : SwipeEvent
    data class Progress(val direction: SwipeDirection, val fraction: Float) : SwipeEvent
    data class Navigate(val direction: SwipeDirection) : SwipeEvent
    data object Cancelled : SwipeEvent
}

class EdgeSwipeDetector(var config: EdgeSwipeConfig) {
    private enum class Phase { IDLE, CANDIDATE, DRAGGING }

    private var phase = Phase.IDLE
    private var direction = SwipeDirection.BACK
    private var startX = 0f
    private var startY = 0f

    val isDragging: Boolean get() = phase == Phase.DRAGGING

    /** Returns true when the touch starts in an enabled edge zone and may become a swipe. */
    fun onDown(
        x: Float,
        y: Float,
        width: Float,
        leftInset: Float,
        rightInset: Float,
        canGoBack: Boolean,
        canGoForward: Boolean,
    ): Boolean {
        phase = Phase.IDLE
        val inLeftZone = x >= leftInset && x < leftInset + config.edgeWidthPx
        val inRightZone = x <= width - rightInset && x > width - rightInset - config.edgeWidthPx
        direction = when {
            inLeftZone && canGoBack -> SwipeDirection.BACK
            inRightZone && canGoForward -> SwipeDirection.FORWARD
            else -> return false
        }
        startX = x
        startY = y
        phase = Phase.CANDIDATE
        return true
    }

    fun onMove(x: Float, y: Float): SwipeEvent {
        val inward = inwardDistance(x)
        return when (phase) {
            Phase.IDLE -> SwipeEvent.None
            Phase.CANDIDATE -> {
                val vertical = abs(y - startY)
                when {
                    inward < -config.touchSlopPx -> reset(SwipeEvent.None)
                    vertical > config.touchSlopPx && vertical > inward * config.maxSlope -> reset(SwipeEvent.None)
                    inward > config.touchSlopPx && vertical <= inward * config.maxSlope -> {
                        phase = Phase.DRAGGING
                        SwipeEvent.Started(direction)
                    }
                    else -> SwipeEvent.None
                }
            }
            Phase.DRAGGING -> SwipeEvent.Progress(direction, fraction(inward))
        }
    }

    fun onUp(x: Float): SwipeEvent = when (phase) {
        Phase.DRAGGING ->
            reset(if (inwardDistance(x) >= config.commitDistancePx) SwipeEvent.Navigate(direction) else SwipeEvent.Cancelled)
        else -> reset(SwipeEvent.None)
    }

    fun onCancel(): SwipeEvent = reset(if (phase == Phase.DRAGGING) SwipeEvent.Cancelled else SwipeEvent.None)

    private fun inwardDistance(x: Float): Float =
        if (direction == SwipeDirection.BACK) x - startX else startX - x

    private fun fraction(inward: Float): Float = (inward / config.commitDistancePx).coerceIn(0f, 1f)

    private fun reset(event: SwipeEvent): SwipeEvent {
        phase = Phase.IDLE
        return event
    }
}
