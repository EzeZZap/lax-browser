package com.javzzap.laxbrowser.data

import android.content.Context
import android.util.Log
import com.javzzap.laxbrowser.domain.ClosedTab
import com.javzzap.laxbrowser.domain.RecentlyClosedCodec
import com.javzzap.laxbrowser.domain.RecentlyClosedStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Recently closed tabs in a small JSON file (not part of Android backups). */
class FileRecentlyClosedStore(context: Context) : RecentlyClosedStore {
    private val file = File(context.filesDir, "recently_closed.json")

    override suspend fun load(): List<ClosedTab> = withContext(Dispatchers.IO) {
        runCatching { if (file.exists()) RecentlyClosedCodec.decode(file.readText()) else emptyList() }
            .onFailure { Log.w(TAG, "Could not read recently closed tabs", it) }
            .getOrDefault(emptyList())
    }

    override suspend fun save(tabs: List<ClosedTab>) = withContext(Dispatchers.IO) {
        runCatching {
            if (tabs.isEmpty()) {
                file.delete()
            } else {
                val temp = File(file.parentFile, "${file.name}.tmp")
                temp.writeText(RecentlyClosedCodec.encode(tabs))
                if (!temp.renameTo(file)) {
                    file.delete()
                    temp.renameTo(file)
                }
            }
        }.onFailure { Log.w(TAG, "Could not save recently closed tabs", it) }
        Unit
    }

    private companion object {
        const val TAG = "RecentlyClosed"
    }
}
