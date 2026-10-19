package com.javzzap.laxbrowser.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A row that can be dragged to the right to run [onSwiped], revealing [label] and [icon] behind it.
 * Only drags that start rightward are claimed, so leftward drags (closing the drawer) still work.
 * The same action is offered to accessibility services as a custom action.
 */
@Composable
fun SwipeToAct(
    label: String,
    icon: ImageVector,
    background: Color,
    contentColor: Color,
    onSwiped: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    thresholdFraction: Float = 0.4f,
    content: @Composable () -> Unit,
) {
    val offset = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val latestOnSwiped by rememberUpdatedState(onSwiped)

    Box(
        modifier
            .onSizeChanged { width = it.width }
            .semantics {
                if (enabled) customActions = listOf(CustomAccessibilityAction(label) { latestOnSwiped(); true })
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                        if (over > 0f) change.consume()
                    } ?: return@awaitEachGesture
                    horizontalDrag(drag.id) { change ->
                        val delta = change.position.x - change.previousPosition.x
                        change.consume()
                        scope.launch { offset.snapTo((offset.value + delta).coerceIn(0f, width.toFloat())) }
                    }
                    scope.launch {
                        if (width > 0 && offset.value >= width * thresholdFraction) {
                            offset.animateTo(width.toFloat())
                            latestOnSwiped()
                            offset.snapTo(0f)
                        } else {
                            offset.animateTo(0f)
                        }
                    }
                }
            },
    ) {
        if (offset.value > 0f) {
            Row(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(background)
                    .padding(start = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, tint = contentColor)
                Spacer(Modifier.width(8.dp))
                Text(label, color = contentColor, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
        Box(Modifier.offset { IntOffset(offset.value.roundToInt(), 0) }) { content() }
    }
}
