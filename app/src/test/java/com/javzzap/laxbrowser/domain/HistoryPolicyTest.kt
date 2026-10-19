package com.javzzap.laxbrowser.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class HistoryPolicyTest {
    private val utc = TimeZone.getTimeZone("UTC")

    // 2026-10-17T15:30:00Z
    private val now = 1_792_251_000_000L

    @Test
    fun `clear ranges start at the right time`() {
        assertEquals(now - 3_600_000L, HistoryPolicy.since(HistoryRange.LAST_HOUR, now, utc))
        assertEquals(1_792_195_200_000L, HistoryPolicy.since(HistoryRange.TODAY, now, utc))
        assertEquals(1_792_195_200_000L - 6 * 86_400_000L, HistoryPolicy.since(HistoryRange.LAST_7_DAYS, now, utc))
        assertEquals(0L, HistoryPolicy.since(HistoryRange.ALL, now, utc))
    }

    @Test
    fun `only top level successful web visits are recorded`() {
        assertTrue(HistoryPolicy.shouldRecord("https://example.com", topLevel = true, redirectSource = false, error = false))
        assertFalse(HistoryPolicy.shouldRecord("https://example.com", topLevel = false, redirectSource = false, error = false))
        assertFalse(HistoryPolicy.shouldRecord("https://example.com", topLevel = true, redirectSource = true, error = false))
        assertFalse(HistoryPolicy.shouldRecord("https://example.com", topLevel = true, redirectSource = false, error = true))
        assertFalse(HistoryPolicy.shouldRecord("about:blank", topLevel = true, redirectSource = false, error = false))
    }
}
