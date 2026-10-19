package com.javzzap.laxbrowser.downloads

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import com.javzzap.laxbrowser.R
import com.javzzap.laxbrowser.data.DownloadStore
import com.javzzap.laxbrowser.domain.Download
import com.javzzap.laxbrowser.domain.DownloadNames
import com.javzzap.laxbrowser.domain.DownloadStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** A download handed over by the engine. [body] is read off the main thread and then closed. */
data class DownloadRequest(
    val url: String,
    val contentType: String?,
    val contentDisposition: String?,
    val contentLength: Long?,
    val body: InputStream,
    val isPrivate: Boolean,
)

/** A download in progress, for the ongoing notification. */
data class ActiveDownload(val id: String, val fileName: String, val downloadedBytes: Long, val totalBytes: Long?, val isPrivate: Boolean)

sealed interface DownloadEvent {
    data class Started(val fileName: String) : DownloadEvent
    data class Completed(val download: Download) : DownloadEvent
    data class Failed(val id: String, val fileName: String) : DownloadEvent
}

/**
 * Saves downloads to the shared Downloads collection (MediaStore on Android 10+, the app's own
 * Downloads folder before that, so no storage permission is needed). Downloads from private tabs
 * are saved but not added to the download list.
 */
class Downloader(context: Context, private val store: DownloadStore, private val scope: CoroutineScope) {
    private val appContext = context.applicationContext
    private val jobs = ConcurrentHashMap<String, Job>()
    private val mutableEvents = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<DownloadEvent> = mutableEvents.asSharedFlow()

    private val mutableActive = MutableStateFlow<Map<String, ActiveDownload>>(emptyMap())

    /** Downloads in progress. While any runs, [DownloadService] keeps the app alive in the foreground. */
    val active: StateFlow<Map<String, ActiveDownload>> = mutableActive.asStateFlow()

    private fun updateActive(id: String, transform: (ActiveDownload?) -> ActiveDownload?) {
        mutableActive.update { current ->
            val next = transform(current[id])
            if (next == null) current - id else current + (id to next)
        }
    }

    fun observe(): Flow<List<Download>> = store.observe()

    /** Marks downloads left unfinished by a previous process as failed. */
    fun recover() {
        scope.launch(Dispatchers.IO) { runCatching { store.failInterrupted() } }
    }

    /**
     * Starts saving [request] and returns its [id]. Callers that follow the download pass their
     * own ID, so they are listening before a quick download can already have finished.
     */
    fun start(request: DownloadRequest, id: String = UUID.randomUUID().toString()): String {
        val name = DownloadNames.fileName(request.contentDisposition, request.url, request.contentType)
        val mime = request.contentType?.substringBefore(';')?.trim()?.takeIf { it.contains('/') }
        val download = Download(
            id = id,
            url = request.url,
            fileName = name,
            mimeType = mime,
            totalBytes = request.contentLength?.takeIf { it > 0 },
            downloadedBytes = 0,
            status = DownloadStatus.IN_PROGRESS,
            contentUri = null,
            createdAt = System.currentTimeMillis(),
        )
        mutableEvents.tryEmit(DownloadEvent.Started(name))
        updateActive(download.id) { ActiveDownload(download.id, name, 0, download.totalBytes, request.isPrivate) }
        DownloadService.start(appContext)
        jobs[download.id] = scope.launch(Dispatchers.IO) {
            if (!request.isPrivate) runCatching { store.add(download) }
            var target: Target? = null
            var written = 0L
            try {
                target = createTarget(name, mime)
                request.body.use { input ->
                    target.open().use { output ->
                        written = copy(input, output) { bytes ->
                            updateActive(download.id) { it?.copy(downloadedBytes = bytes) }
                            if (!request.isPrivate) store.progress(download.id, bytes)
                        }
                    }
                }
                target.publish()
                val finished = download.copy(status = DownloadStatus.COMPLETED, downloadedBytes = written, contentUri = target.uri.toString())
                if (!request.isPrivate) store.finish(download.id, DownloadStatus.COMPLETED, written, finished.contentUri)
                notifyFinished(finished)
                mutableEvents.tryEmit(DownloadEvent.Completed(finished))
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    target?.discard()
                    if (!request.isPrivate) store.finish(download.id, DownloadStatus.CANCELLED, written, null)
                }
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Download failed", e)
                target?.discard()
                if (!request.isPrivate) runCatching { store.finish(download.id, DownloadStatus.FAILED, written, null) }
                mutableEvents.tryEmit(DownloadEvent.Failed(download.id, name))
            } finally {
                jobs.remove(download.id)
                updateActive(download.id) { null }
            }
        }
        return download.id
    }

    fun cancel(id: String) {
        jobs[id]?.cancel()
    }

    /** Opens a finished download with another app. Returns false when no app can open it. */
    fun open(download: Download): Boolean {
        val uri = download.contentUri?.let(Uri::parse) ?: return false
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, download.mimeType ?: appContext.contentResolver.getType(uri) ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    /** Removes a download from the list, and its file too when [deleteFile] is true. */
    fun delete(download: Download, deleteFile: Boolean) {
        scope.launch(Dispatchers.IO) {
            if (deleteFile) {
                download.contentUri?.let(Uri::parse)?.let { uri -> runCatching { appContext.contentResolver.delete(uri, null, null) } }
            }
            store.delete(download.id)
        }
    }

    private interface Target {
        val uri: Uri
        fun open(): OutputStream
        fun publish()
        fun discard()
    }

    private fun createTarget(name: String, mime: String?): Target =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) mediaStoreTarget(name, mime) else fileTarget(name)

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun mediaStoreTarget(name: String, mime: String?): Target {
        val resolver = appContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            mime?.let { put(MediaStore.MediaColumns.MIME_TYPE, it) }
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Could not create the download file")
        return object : Target {
            override val uri = uri
            override fun open(): OutputStream = resolver.openOutputStream(uri) ?: error("Could not write the download file")
            override fun publish() {
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            }
            override fun discard() {
                runCatching { resolver.delete(uri, null, null) }
            }
        }
    }

    private fun fileTarget(name: String): Target {
        val dir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: File(appContext.filesDir, "downloads")
        dir.mkdirs()
        var file = File(dir, name)
        var counter = 1
        while (file.exists()) file = File(dir, "${name.substringBeforeLast('.')} (${counter++})" + name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" })
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.files", file)
        return object : Target {
            override val uri = uri
            override fun open(): OutputStream = file.outputStream()
            override fun publish() = Unit
            override fun discard() {
                file.delete()
            }
        }
    }

    private suspend fun copy(input: InputStream, output: OutputStream, onProgress: suspend (Long) -> Unit): Long {
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        var lastReport = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            total += read
            val now = System.currentTimeMillis()
            if (now - lastReport > PROGRESS_INTERVAL_MS) {
                lastReport = now
                onProgress(total)
            }
            // Cancelling the download job stops here.
            kotlinx.coroutines.yield()
        }
        return total
    }

    private fun notifyFinished(download: Download) {
        val manager = appContext.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, appContext.getString(R.string.channel_downloads), NotificationManager.IMPORTANCE_LOW),
            )
        }
        val uri = download.contentUri?.let(Uri::parse) ?: return
        val open = Intent(Intent.ACTION_VIEW).setDataAndType(uri, download.mimeType ?: "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val pending = PendingIntent.getActivity(appContext, download.id.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(download.fileName)
            .setContentText(appContext.getString(R.string.download_complete))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(appContext).notify(download.id, NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Notifications are not allowed; the in-app message still appears.
        }
    }

    companion object {
        private const val TAG = "Downloader"
        private const val BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_INTERVAL_MS = 500L
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 2
    }
}
