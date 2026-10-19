package com.javzzap.laxbrowser.domain

import java.util.Calendar
import java.util.TimeZone

/** One visit to a page. History is browser-wide, like bookmarks, and is never kept for private tabs. */
data class HistoryEntry(
    val id: Long,
    val url: String,
    val title: String,
    val faviconUrl: String?,
    val visitedAt: Long,
)

enum class HistoryRange { LAST_HOUR, TODAY, LAST_7_DAYS, ALL }

object HistoryPolicy {
    /** A repeated visit to the same address within this window updates the last entry instead. */
    const val DUPLICATE_WINDOW_MS: Long = 60_000L
    private const val HOUR_MS: Long = 60L * 60L * 1000L

    /** Start of the time range to clear; 0 clears everything. */
    fun since(range: HistoryRange, now: Long, zone: TimeZone = TimeZone.getDefault()): Long = when (range) {
        HistoryRange.LAST_HOUR -> now - HOUR_MS
        HistoryRange.TODAY -> startOfDay(now, zone)
        HistoryRange.LAST_7_DAYS -> startOfDay(now, zone, daysBack = 6)
        HistoryRange.ALL -> 0L
    }

    fun startOfDay(time: Long, zone: TimeZone = TimeZone.getDefault(), daysBack: Int = 0): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = time
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -daysBack)
        }.timeInMillis

    /** Only top-level, successful, non-redirecting web visits are recorded. */
    fun shouldRecord(url: String, topLevel: Boolean, redirectSource: Boolean, error: Boolean): Boolean =
        topLevel && !redirectSource && !error && UrlInput.isWebUrl(url)
}
