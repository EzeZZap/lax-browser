package com.javzzap.laxbrowser.domain

object LaunchPolicy {
    const val NEW_TAB_PAGE_THRESHOLD_MS: Long = 60L * 60L * 1000L

    /**
     * Opens the NTP when there is no usable state (first launch) or when the app has been in the
     * background for at least [thresholdMs]. A missing background timestamp never triggers it.
     */
    fun shouldOpenNewTabPage(
        hasUsableState: Boolean,
        lastBackgroundAt: Long?,
        now: Long,
        thresholdMs: Long = NEW_TAB_PAGE_THRESHOLD_MS,
    ): Boolean {
        if (!hasUsableState) return true
        if (lastBackgroundAt == null) return false
        return now - lastBackgroundAt >= thresholdMs
    }
}
