package com.javzzap.laxbrowser.engine.gecko

import com.javzzap.laxbrowser.domain.SearchEngines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.WebRequest

/**
 * Fetches search suggestions from the user's search engine through Gecko's network stack. Requests
 * are anonymous (no cookies from any container). Lax never asks for suggestions in private tabs.
 */
class SearchSuggestions(private val runtime: () -> GeckoRuntime) {
    private val executor by lazy { GeckoWebExecutor(runtime()) }

    /** Main-thread call; reading and parsing the response run on [Dispatchers.IO]. */
    suspend fun fetch(template: String, query: String): List<String> {
        if (query.isBlank() || query.length > MAX_QUERY) return emptyList()
        val request = WebRequest.Builder(SearchEngines.searchUrl(template, query.trim()))
            .header("Accept", "application/json")
            .build()
        val response = runCatching { executor.fetch(request, GeckoWebExecutor.FETCH_FLAGS_ANONYMOUS).await() }.getOrNull() ?: return emptyList()
        if (response.statusCode !in 200..299) return emptyList()
        val body = response.body ?: return emptyList()
        return withContext(Dispatchers.IO) {
            val text = runCatching { body.use { readLimited(it) } }.getOrNull() ?: return@withContext emptyList()
            SearchEngines.parseSuggestions(text, query)
        }
    }

    private fun readLimited(input: java.io.InputStream): String {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (out.size() < MAX_BYTES) {
            val read = input.read(buffer, 0, minOf(buffer.size, MAX_BYTES - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private companion object {
        const val MAX_QUERY = 200
        const val MAX_BYTES = 64 * 1024
    }
}
