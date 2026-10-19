package com.javzzap.laxbrowser.data

import com.javzzap.laxbrowser.domain.BrowserData
import com.javzzap.laxbrowser.domain.DataDiff

/** Persistence boundary. Implementations must be called off the main thread. */
interface BrowserRepository {
    suspend fun load(): BrowserData
    suspend fun apply(diff: DataDiff)
    suspend fun loadSessionState(tabId: String): String?
    suspend fun saveSessionStates(states: Map<String, String>, now: Long)

    /** Replaces all browser data (except installed web apps) with [data] in one transaction. */
    suspend fun replaceAll(data: BrowserData)
}
