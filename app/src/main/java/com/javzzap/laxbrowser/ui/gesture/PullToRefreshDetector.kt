package com.javzzap.laxbrowser.ui.gesture

import kotlin.math.abs

/**
 * A pull starts only when the page reports it cannot scroll further up and lets the browser
 * handle vertical overscroll, after moving [touchSlopPx] downward within [maxSlope] (|dx| / |dy|).
 * The displayed distance is the finger travel times [resistance], capped at [maxPullPx]. Releasing
 * after [triggerDistancePx] of displayed distance refreshes.
 */
data class PullToRefreshConfig(
    val touchSlopPx: Float,
    val triggerDistancePx: Float,
    val maxPullPx: Float,
    val resistance: Float = 0.5f,
    val maxSlope: Float = 0.5f,
)

sealed interface PullEvent {
    data object None : PullEvent
    data object Started : PullEvent
    data class Progress(val distancePx: Float, val fraction: Float) : PullEvent
    data object Refresh : PullEvent
    data object Cancelled : PullEvent
}

class PullToRefreshDetector(var config: PullToRefreshConfig) {
    private enum class Phase { IDLE, CANDIDATE, DRAGGING }

    private var phase = Phase.IDLE
    private var startX = 0f
    private var startY = 0f
    private var dragStartY = 0f
    private var distance = 0f

    val isDragging: Boolean get() = phase == Phase.DRAGGING

    fun onDown(x: Float, y: Float, enabled: Boolean) {
        phase = if (enabled) Phase.CANDIDATE else Phase.IDLE
        startX = x
        startY = y
        distance = 0f
    }

    /** [canOverscrollTop] is Gecko's answer for this touch; null while it is still unknown. */
    fun onMove(x: Float, y: Float, canOverscrollTop: Boolean?): PullEvent = when (phase) {
        Phase.IDLE -> PullEvent.None
        Phase.CANDIDATE -> {
            val dy = y - startY
            val dx = abs(x - startX)
            when {
                dy < -config.touchSlopPx -> reset(PullEvent.None)
                dx > config.touchSlopPx && dx > dy * config.maxSlope -> reset(PullEvent.None)
                dy > config.touchSlopPx && dx <= dy * config.maxSlope -> when (canOverscrollTop) {
                    true -> {
                        phase = Phase.DRAGGING
                        dragStartY = y
                        PullEvent.Started
                    }
                    false -> reset(PullEvent.None)
                    null -> PullEvent.None
                }
                else -> PullEvent.None
            }
        }
        Phase.DRAGGING -> {
            distance = ((y - dragStartY) * config.resistance).coerceIn(0f, config.maxPullPx)
            PullEvent.Progress(distance, (distance / config.triggerDistancePx).coerceIn(0f, 1f))
        }
    }

    fun onUp(): PullEvent = when (phase) {
        Phase.DRAGGING -> reset(if (distance >= config.triggerDistancePx) PullEvent.Refresh else PullEvent.Cancelled)
        else -> reset(PullEvent.None)
    }

    fun onCancel(): PullEvent = reset(if (phase == Phase.DRAGGING) PullEvent.Cancelled else PullEvent.None)

    private fun reset(event: PullEvent): PullEvent {
        phase = Phase.IDLE
        distance = 0f
        return event
    }
}
