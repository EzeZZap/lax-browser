package com.javzzap.laxbrowser.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.javzzap.laxbrowser.domain.Download
import com.javzzap.laxbrowser.domain.DownloadStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
interface DownloadDao {
    @Upsert suspend fun upsert(item: DownloadEntity)

    @Query("SELECT * FROM downloads ORDER BY created_at DESC")
    fun observe(): Flow<List<DownloadEntity>>

    @Query("UPDATE downloads SET downloaded_bytes = :bytes WHERE id = :id")
    suspend fun progress(id: String, bytes: Long)

    @Query("UPDATE downloads SET status = :status, downloaded_bytes = :bytes, content_uri = :contentUri WHERE id = :id")
    suspend fun finish(id: String, status: String, bytes: Long, contentUri: String?)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: String)

    /** Downloads still marked in progress belong to a previous process and can no longer finish. */
    @Query("UPDATE downloads SET status = 'FAILED' WHERE status = 'IN_PROGRESS'")
    suspend fun failInterrupted()
}

/** The download list. Kept outside browser data, like history. */
class DownloadStore(private val dao: DownloadDao) {
    fun observe(): Flow<List<Download>> = dao.observe().map { rows -> rows.mapNotNull { it.toDomain() } }

    suspend fun add(download: Download) = dao.upsert(download.toEntity())

    suspend fun progress(id: String, bytes: Long) = dao.progress(id, bytes)

    suspend fun finish(id: String, status: DownloadStatus, bytes: Long, contentUri: String?) =
        dao.finish(id, status.name, bytes, contentUri)

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun failInterrupted() = dao.failInterrupted()
}

private fun DownloadEntity.toDomain(): Download? {
    val status = DownloadStatus.entries.firstOrNull { it.name == status } ?: return null
    return Download(id, url, fileName, mimeType, totalBytes, downloadedBytes, status, contentUri, createdAt)
}

private fun Download.toEntity() =
    DownloadEntity(id, url, fileName, mimeType, totalBytes, downloadedBytes, status.name, contentUri, createdAt)

@Dao
interface ExtensionPrefDao {
    @Query("SELECT id FROM extension_prefs WHERE pinned = 1")
    suspend fun pinnedIds(): List<String>

    @Upsert suspend fun upsert(item: ExtensionPrefEntity)

    @Query("DELETE FROM extension_prefs WHERE id = :id")
    suspend fun delete(id: String)
}
