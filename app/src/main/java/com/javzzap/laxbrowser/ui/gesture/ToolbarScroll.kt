package com.javzzap.laxbrowser.ui.gesture

/**
 * How much of the dynamic toolbar is hidden, following the page scroll: scrolling down hides it
 * pixel by pixel, scrolling up reveals it, and it is always fully shown at the top of the page.
 */
class ToolbarScroll {
    private var lastScrollY: Int? = null

    /** Returns the new hidden amount, between 0 and [maxPx]. */
    fun onScroll(scrollY: Int, hiddenPx: Float, maxPx: Float): Float {
        val previous = lastScrollY
        lastScrollY = scrollY
        if (scrollY <= 0) return 0f
        if (previous == null) return hiddenPx
        return (hiddenPx + (scrollY - previous)).coerceIn(0f, maxPx)
    }

    /** Forgets the last position, for example after switching tabs. */
    fun reset() {
        lastScrollY = null
    }

    companion object {
        /** Where a partly hidden toolbar settles once scrolling stops. */
        fun snapTarget(hiddenPx: Float, maxPx: Float): Float = if (hiddenPx > maxPx / 2f) maxPx else 0f
    }
}
