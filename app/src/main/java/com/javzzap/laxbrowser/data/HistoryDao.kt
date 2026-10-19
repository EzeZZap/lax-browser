package com.javzzap.laxbrowser.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert suspend fun insert(entry: HistoryEntity): Long

    @Query("SELECT * FROM history WHERE url = :url ORDER BY visited_at DESC LIMIT 1")
    suspend fun latestFor(url: String): HistoryEntity?

    @Query("UPDATE history SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String)

    @Query("UPDATE history SET favicon_url = :iconUrl WHERE id = :id")
    suspend fun updateFavicon(id: Long, iconUrl: String)

    @Query("UPDATE history SET visited_at = :visitedAt WHERE id = :id")
    suspend fun touch(id: Long, visitedAt: Long)

    @Query(
        "SELECT * FROM history WHERE (:pattern = '' OR url LIKE :pattern ESCAPE '\\' OR title LIKE :pattern ESCAPE '\\') " +
            "ORDER BY visited_at DESC LIMIT :limit",
    )
    fun observe(pattern: String, limit: Int): Flow<List<HistoryEntity>>

    @Query("SELECT DISTINCT url FROM history WHERE url IN (:urls)")
    suspend fun visited(urls: List<String>): List<String>

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM history WHERE visited_at >= :since")
    suspend fun deleteSince(since: Long)

    /** Distinct pages matching [pattern], most visited and most recent first. */
    @Query(
        "SELECT url, MAX(title) AS title, MAX(favicon_url) AS favicon_url, COUNT(*) AS visits, MAX(visited_at) AS last_visit " +
            "FROM history WHERE url LIKE :pattern ESCAPE '\\' OR title LIKE :pattern ESCAPE '\\' " +
            "GROUP BY url ORDER BY visits DESC, last_visit DESC LIMIT :limit",
    )
    suspend fun search(pattern: String, limit: Int): List<HistoryMatch>
}

data class HistoryMatch(
    val url: String,
    val title: String,
    @androidx.room.ColumnInfo(name = "favicon_url") val faviconUrl: String?,
    val visits: Int,
    @androidx.room.ColumnInfo(name = "last_visit") val lastVisit: Long,
)
