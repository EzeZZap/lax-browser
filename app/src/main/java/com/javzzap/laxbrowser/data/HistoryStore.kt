package com.javzzap.laxbrowser.data

import com.javzzap.laxbrowser.domain.HistoryEntry
import com.javzzap.laxbrowser.domain.HistoryPolicy
import com.javzzap.laxbrowser.domain.UrlInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

/** Browsing history. Kept outside [com.javzzap.laxbrowser.domain.BrowserData] because it can grow large. */
interface HistoryStore {
    suspend fun recordVisit(url: String, title: String, at: Long)
    suspend fun updateTitle(url: String, title: String)
    suspend fun updateFavicon(url: String, iconUrl: String)
    fun observe(query: String, limit: Int = DEFAULT_LIMIT): Flow<List<HistoryEntry>>
    suspend fun visited(urls: List<String>): Set<String>
    suspend fun delete(id: Long)

    /** Deletes every visit at or after [since]; 0 deletes all history. */
    suspend fun clearSince(since: Long)

    /** Distinct pages whose address or title contains [query], for address bar suggestions. */
    suspend fun search(query: String, limit: Int): List<HistoryEntry> = emptyList()

    companion object {
        const val DEFAULT_LIMIT = 500

        val None = object : HistoryStore {
            override suspend fun recordVisit(url: String, title: String, at: Long) = Unit
            override suspend fun updateTitle(url: String, title: String) = Unit
            override suspend fun updateFavicon(url: String, iconUrl: String) = Unit
            override fun observe(query: String, limit: Int): Flow<List<HistoryEntry>> = emptyFlow()
            override suspend fun visited(urls: List<String>): Set<String> = emptySet()
            override suspend fun delete(id: Long) = Unit
            override suspend fun clearSince(since: Long) = Unit
        }
    }
}

class RoomHistoryStore(private val dao: HistoryDao) : HistoryStore {

    override suspend fun recordVisit(url: String, title: String, at: Long) {
        val last = dao.latestFor(url)
        if (last != null && at - last.visitedAt in 0..HistoryPolicy.DUPLICATE_WINDOW_MS) {
            dao.touch(last.id, at)
            return
        }
        dao.insert(HistoryEntity(url = url, title = title.ifBlank { UrlInput.displayHost(url) }, faviconUrl = last?.faviconUrl, visitedAt = at))
    }

    override suspend fun updateTitle(url: String, title: String) {
        if (title.isBlank()) return
        dao.latestFor(url)?.let { dao.updateTitle(it.id, title.take(MAX_TITLE)) }
    }

    override suspend fun updateFavicon(url: String, iconUrl: String) {
        dao.latestFor(url)?.let { if (it.faviconUrl != iconUrl) dao.updateFavicon(it.id, iconUrl) }
    }

    override fun observe(query: String, limit: Int): Flow<List<HistoryEntry>> {
        val trimmed = query.trim()
        val pattern = if (trimmed.isEmpty()) "" else likePattern(trimmed)
        return dao.observe(pattern, limit).map { rows -> rows.map { HistoryEntry(it.id, it.url, it.title, it.faviconUrl, it.visitedAt) } }
    }

    override suspend fun search(query: String, limit: Int): List<HistoryEntry> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return dao.search(likePattern(trimmed), limit).map { HistoryEntry(0, it.url, it.title, it.faviconUrl, it.lastVisit) }
    }

    private fun likePattern(text: String) = "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

    override suspend fun visited(urls: List<String>): Set<String> =
        urls.chunked(SQL_VARIABLE_CHUNK).flatMap { dao.visited(it) }.toSet()

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun clearSince(since: Long) = dao.deleteSince(since)

    private companion object {
        const val MAX_TITLE = 500
        const val SQL_VARIABLE_CHUNK = 500
    }
}
