package com.javzzap.laxbrowser.ui.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.javzzap.laxbrowser.R
import com.javzzap.laxbrowser.engine.gecko.GeckoEngine
import com.javzzap.laxbrowser.ui.gesture.SwipeDirection
import com.javzzap.laxbrowser.ui.gesture.WebGestureLayout
import kotlin.math.roundToInt

/**
 * Displays the session of [tabId] under a dynamic toolbar of [toolbarMaxPx] that is currently
 * hidden by [toolbarHiddenPx()]. Gecko is told the toolbar's maximum height so the page layout stays
 * stable while it slides. With a top toolbar the view is shifted down by the visible toolbar
 * height; in both positions the obscured part is reported through vertical clipping so fixed
 * elements stay visible.
 */
@Composable
fun WebContent(
    engine: GeckoEngine,
    tabId: String,
    canGoBack: Boolean,
    canGoForward: Boolean,
    pullToRefresh: Boolean,
    toolbarAtTop: Boolean,
    toolbarMaxPx: Int,
    toolbarHiddenPx: () -> Float,
    onSwipeNavigate: (SwipeDirection) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val generation by engine.sessionGeneration.collectAsStateWithLifecycle()
    val session = remember(tabId, generation) { engine.session(tabId) }
    val latestCanGoBack by rememberUpdatedState(canGoBack)
    val latestCanGoForward by rememberUpdatedState(canGoForward)
    val latestPullToRefresh by rememberUpdatedState(pullToRefresh)
    val latestOnNavigate by rememberUpdatedState(onSwipeNavigate)
    val latestOnRefresh by rememberUpdatedState(onRefresh)
    var swipeDirection by remember { mutableStateOf<SwipeDirection?>(null) }
    var swipeFraction by remember { mutableFloatStateOf(0f) }
    var pullDistance by remember { mutableStateOf<Float?>(null) }
    var pullFraction by remember { mutableFloatStateOf(0f) }

    Box(modifier.fillMaxSize().clipToBounds()) {
        AndroidView(
            modifier = Modifier.fillMaxSize().testTag("web_content"),
            factory = { context ->
                WebGestureLayout(context).apply {
                    this.canGoBack = { latestCanGoBack }
                    this.canGoForward = { latestCanGoForward }
                    pullToRefreshEnabled = { latestPullToRefresh }
                    onSwipeProgress = { direction, fraction ->
                        swipeDirection = direction
                        swipeFraction = fraction
                    }
                    onNavigate = { latestOnNavigate(it) }
                    onPullProgress = { distance, fraction ->
                        pullDistance = distance
                        pullFraction = fraction
                    }
                    this.onRefresh = { latestOnRefresh() }
                }
            },
            update = { layout ->
                val view = layout.geckoView
                if (session == null) {
                    if (view.session != null) view.releaseSession()
                } else if (view.session !== session) {
                    view.setSession(session)
                    layout.appliedToolbarMaxHeight = -1
                }
                if (layout.appliedToolbarMaxHeight != toolbarMaxPx) {
                    view.setDynamicToolbarMaxHeight(toolbarMaxPx)
                    layout.appliedToolbarMaxHeight = toolbarMaxPx
                }
                val hidden = toolbarHiddenPx().coerceIn(0f, toolbarMaxPx.toFloat())
                layout.translationY = if (toolbarAtTop) toolbarMaxPx - hidden else 0f
                view.setVerticalClipping(-hidden.roundToInt())
            },
            onRelease = { layout -> layout.geckoView.releaseSession() },
        )
        swipeDirection?.let { SwipeIndicator(it, swipeFraction) }
        pullDistance?.let { distance ->
            val topOffset = if (toolbarAtTop) toolbarMaxPx - toolbarHiddenPx() else 0f
            PullIndicator(distance + topOffset, pullFraction)
        }
    }
}

@Composable
private fun SwipeIndicator(direction: SwipeDirection, fraction: Float) {
    val back = direction == SwipeDirection.BACK
    val committed = fraction >= 1f
    Box(Modifier.fillMaxSize()) {
        Surface(
            shape = CircleShape,
            color = if (committed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 4.dp,
            modifier = Modifier
                .align(if (back) Alignment.CenterStart else Alignment.CenterEnd)
                .offset(x = ((if (back) 1 else -1) * (fraction * 48f - 24f)).dp)
                .padding(4.dp)
                .size(44.dp)
                .alpha(0.4f + 0.6f * fraction),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (back) Icons.AutoMirrored.Filled.ArrowBack else Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(if (back) R.string.release_to_go_back else R.string.release_to_go_forward),
                    tint = if (committed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PullIndicator(offsetPx: Float, fraction: Float) {
    val committed = fraction >= 1f
    val size = with(LocalDensity.current) { 40.dp.roundToPx() }
    Box(Modifier.fillMaxSize()) {
        Surface(
            shape = CircleShape,
            color = if (committed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, (offsetPx - size).roundToInt()) }
                .size(40.dp)
                .alpha(0.3f + 0.7f * fraction),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.release_to_refresh),
                    tint = if (committed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.rotate(fraction * 270f),
                )
            }
        }
    }
}
