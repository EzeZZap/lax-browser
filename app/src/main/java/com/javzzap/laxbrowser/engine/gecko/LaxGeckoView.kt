package com.javzzap.laxbrowser.engine.gecko

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.PanZoomController
import org.mozilla.geckoview.PanZoomController.InputResultDetail

/**
 * A [GeckoView] that records how Gecko handled the start of each touch, so gesture code can tell
 * whether the page consumed it (scrollers, carousels, pages with touch handlers) and whether the
 * page is scrolled to the top. Only ACTION_DOWN asks for the detailed result, as GeckoView advises.
 */
class LaxGeckoView(context: Context) : GeckoView(context) {

    init {
        // Lets the user's Android autofill service (Bitwarden, Google Password Manager, ...) see
        // and fill page forms through GeckoView's virtual view structure.
        setAutofillEnabled(true)
    }

    /** The result for the current touch, or null until Gecko has answered. */
    var currentTouchResult: InputResultDetail? = null
        private set

    private var currentDownTime = 0L

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return super.onTouchEvent(event)
        currentTouchResult = null
        val downTime = event.downTime
        currentDownTime = downTime
        onTouchEventForDetailResult(event).accept { detail ->
            if (currentDownTime == downTime) currentTouchResult = detail
        }
        return session != null
    }

    companion object {
        /** True when the page lets the browser handle a pull past the top (pull-to-refresh). */
        fun canOverscrollTop(detail: InputResultDetail?): Boolean {
            detail ?: return false
            val result = detail.handledResult()
            if (result != PanZoomController.INPUT_RESULT_HANDLED && result != PanZoomController.INPUT_RESULT_UNHANDLED) return false
            val canScrollUp = (detail.scrollableDirections() and PanZoomController.SCROLLABLE_FLAG_TOP) != 0
            val canOverscrollVertically = (detail.overscrollDirections() and PanZoomController.OVERSCROLL_FLAG_VERTICAL) != 0
            return !canScrollUp && canOverscrollVertically
        }

        /** True when the content under the finger can itself scroll toward the left. */
        fun canScrollLeft(detail: InputResultDetail?): Boolean =
            detail != null && (detail.scrollableDirections() and PanZoomController.SCROLLABLE_FLAG_LEFT) != 0

        /** True when the content under the finger can itself scroll toward the right. */
        fun canScrollRight(detail: InputResultDetail?): Boolean =
            detail != null && (detail.scrollableDirections() and PanZoomController.SCROLLABLE_FLAG_RIGHT) != 0
    }
}
