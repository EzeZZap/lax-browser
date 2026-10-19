package com.javzzap.laxbrowser.ui.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max

enum class AddressSwipeDirection { LEFT, RIGHT, UP, DOWN }

/** Decides which way a finished drag on the address went. */
object AddressSwipe {
    /** One axis must clearly dominate, so a diagonal drag does nothing. */
    private const val DOMINANCE = 1.5f

    fun direction(dx: Float, dy: Float, threshold: Float): AddressSwipeDirection? {
        val ax = abs(dx)
        val ay = abs(dy)
        if (max(ax, ay) < threshold) return null
        return when {
            ax >= ay * DOMINANCE -> if (dx < 0) AddressSwipeDirection.LEFT else AddressSwipeDirection.RIGHT
            ay >= ax * DOMINANCE -> if (dy < 0) AddressSwipeDirection.UP else AddressSwipeDirection.DOWN
            else -> null
        }
    }
}

/**
 * Swipes on the address. Once a drag passes the touch slop it is consumed before children see
 * it, so the address is not also tapped. [onDrag] gets the distance so far (zero when the finger
 * lifts) for visual feedback; [onSwipe] runs when a drag long enough in one direction ends.
 */
fun Modifier.addressSwipes(
    enabled: Boolean,
    onDrag: (Offset) -> Unit,
    onSwipe: (AddressSwipeDirection) -> Unit,
): Modifier = if (!enabled) {
    this
} else {
    pointerInput(Unit) {
        val threshold = SWIPE_DISTANCE.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var total = Offset.Zero
            var dragging = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        if (dragging) {
                            change.consume()
                            AddressSwipe.direction(total.x, total.y, threshold)?.let(onSwipe)
                        }
                        break
                    }
                    total += change.positionChange()
                    if (!dragging && total.getDistance() > viewConfiguration.touchSlop) dragging = true
                    if (dragging) {
                        change.consume()
                        onDrag(total)
                    }
                }
            } finally {
                onDrag(Offset.Zero)
            }
        }
    }
}

private val SWIPE_DISTANCE = 40.dp
