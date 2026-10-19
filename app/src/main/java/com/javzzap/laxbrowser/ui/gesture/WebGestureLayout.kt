package com.javzzap.laxbrowser.ui.gesture

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.javzzap.laxbrowser.engine.gecko.LaxGeckoView

/**
 * Hosts the web content and recognizes two browser gestures on top of it:
 *
 * - **History swipes** from the edge zones described in [EdgeSwipeConfig]. A swipe is not taken
 *   when Gecko reports that the content under the finger can scroll in that direction (carousels,
 *   horizontally scrolled pages).
 * - **Pull-to-refresh** when Gecko reports the page is at the top and allows the browser to handle
 *   vertical overscroll (see [LaxGeckoView.canOverscrollTop]).
 *
 * Touches are only stolen from the page once a detector has seen a clear gesture; everything else
 * reaches the page unchanged.
 */
class WebGestureLayout(context: Context) : FrameLayout(context) {

    val geckoView = LaxGeckoView(context)

    var canGoBack: () -> Boolean = { false }
    var canGoForward: () -> Boolean = { false }
    var pullToRefreshEnabled: () -> Boolean = { false }
    var onSwipeProgress: (SwipeDirection?, Float) -> Unit = { _, _ -> }
    var onNavigate: (SwipeDirection) -> Unit = {}
    var onPullProgress: (distancePx: Float?, fraction: Float) -> Unit = { _, _ -> }
    var onRefresh: () -> Unit = {}

    /** The dynamic toolbar max height last given to Gecko, to avoid resetting its clipping. */
    var appliedToolbarMaxHeight = -1

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val edge = EdgeSwipeDetector(
        EdgeSwipeConfig(
            edgeWidthPx = EDGE_WIDTH_DP * density,
            touchSlopPx = touchSlop,
            commitDistancePx = COMMIT_DISTANCE_DP * density,
        ),
    )
    private val pull = PullToRefreshDetector(
        PullToRefreshConfig(
            touchSlopPx = touchSlop,
            triggerDistancePx = PULL_TRIGGER_DP * density,
            maxPullPx = PULL_MAX_DP * density,
        ),
    )
    private val location = IntArray(2)

    init {
        addView(geckoView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> startCandidates(ev)
            MotionEvent.ACTION_POINTER_DOWN -> {
                edge.onCancel()
                pull.onCancel()
            }
            MotionEvent.ACTION_MOVE -> return detectStart(ev)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                edge.onCancel()
                pull.onCancel()
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when {
            edge.isDragging -> handleEdgeDrag(ev)
            pull.isDragging -> handlePull(ev)
            else -> return false
        }
        return true
    }

    private fun detectStart(ev: MotionEvent): Boolean {
        val detail = geckoView.currentTouchResult
        val edgeEvent = edge.onMove(ev.x, ev.y)
        if (edgeEvent is SwipeEvent.Started) {
            val contentScrolls = when (edgeEvent.direction) {
                SwipeDirection.BACK -> LaxGeckoView.canScrollLeft(detail)
                SwipeDirection.FORWARD -> LaxGeckoView.canScrollRight(detail)
            }
            if (contentScrolls) {
                edge.onCancel()
            } else {
                pull.onCancel()
                parent?.requestDisallowInterceptTouchEvent(true)
                onSwipeProgress(edgeEvent.direction, 0f)
                return true
            }
        }
        val canOverscrollTop = if (detail == null) null else LaxGeckoView.canOverscrollTop(detail)
        if (pull.onMove(ev.x, ev.y, canOverscrollTop) is PullEvent.Started) {
            edge.onCancel()
            parent?.requestDisallowInterceptTouchEvent(true)
            onPullProgress(0f, 0f)
            return true
        }
        return false
    }

    private fun handleEdgeDrag(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val event = edge.onMove(ev.x, ev.y)
                if (event is SwipeEvent.Progress) onSwipeProgress(event.direction, event.fraction)
            }
            MotionEvent.ACTION_UP -> {
                val event = edge.onUp(ev.x)
                onSwipeProgress(null, 0f)
                if (event is SwipeEvent.Navigate) onNavigate(event.direction)
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                edge.onCancel()
                onSwipeProgress(null, 0f)
            }
        }
    }

    private fun handlePull(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val event = pull.onMove(ev.x, ev.y, true)
                if (event is PullEvent.Progress) onPullProgress(event.distancePx, event.fraction)
            }
            MotionEvent.ACTION_UP -> {
                val event = pull.onUp()
                onPullProgress(null, 0f)
                if (event is PullEvent.Refresh) onRefresh()
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                pull.onCancel()
                onPullProgress(null, 0f)
            }
        }
    }

    private fun startCandidates(ev: MotionEvent) {
        val gestures = ViewCompat.getRootWindowInsets(this)?.getInsets(WindowInsetsCompat.Type.systemGestures())
        getLocationInWindow(location)
        val windowWidth = rootView.width
        val leftInset = ((gestures?.left ?: 0) - location[0]).coerceAtLeast(0)
        val rightInset = ((gestures?.right ?: 0) - (windowWidth - location[0] - width)).coerceAtLeast(0)
        edge.onDown(
            x = ev.x,
            y = ev.y,
            width = width.toFloat(),
            leftInset = leftInset.toFloat(),
            rightInset = rightInset.toFloat(),
            canGoBack = canGoBack(),
            canGoForward = canGoForward(),
        )
        pull.onDown(ev.x, ev.y, pullToRefreshEnabled())
    }

    companion object {
        /** Width of each edge zone, measured from the inner side of the system gesture inset. */
        const val EDGE_WIDTH_DP = 24f

        /** Inward travel required before release triggers navigation. */
        const val COMMIT_DISTANCE_DP = 96f

        /** Displayed pull distance required before release refreshes. */
        const val PULL_TRIGGER_DP = 72f

        const val PULL_MAX_DP = 120f
    }
}
