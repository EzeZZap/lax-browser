package com.javzzap.laxbrowser.engine.gecko

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.core.graphics.scale
import com.javzzap.laxbrowser.domain.UrlInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.WebRequest
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Loads favicons by URL: memory cache, then disk cache, then network. Downloads go through Gecko's
 * network stack with [GeckoWebExecutor.FETCH_FLAGS_ANONYMOUS], so no cookies or credentials from
 * any container are sent and no container storage is touched. Main-thread API; decoding and file
 * work run on [Dispatchers.IO].
 */
class FaviconRepository(
    context: Context,
    private val runtimeProvider: () -> GeckoRuntime,
    private val scope: CoroutineScope,
) {
    private val dir = File(context.cacheDir, DIR_NAME)
    private val memory = LruCache<String, Bitmap>(MEMORY_ENTRIES)
    private val inFlight = HashMap<String, Deferred<Bitmap?>>()
    private val failed = HashSet<String>()
    private val executor by lazy { GeckoWebExecutor(runtimeProvider()) }

    fun cached(url: String): Bitmap? = memory.get(url)

    /**
     * [ephemeral] icons (private tabs) are downloaded through Gecko's private network context and
     * only kept in memory; nothing about them is written to disk.
     */
    suspend fun load(url: String, ephemeral: Boolean = false): Bitmap? {
        memory.get(url)?.let { return it }
        if (url in failed || !UrlInput.isWebUrl(url)) return null
        val job = inFlight.getOrPut(url) {
            scope.async {
                val bitmap = if (ephemeral) {
                    readDisk(url) ?: download(url, private = true)
                } else {
                    readDisk(url) ?: download(url, private = false)?.also { writeDisk(url, it) }
                }
                if (bitmap == null) failed += url else memory.put(url, bitmap)
                bitmap
            }
        }
        return try {
            job.await()
        } finally {
            inFlight.remove(url)
        }
    }

    // GeckoWebExecutor documents that FETCH_* flags may be combined, but its @IntDef lacks flag = true.
    @SuppressLint("WrongConstant")
    private suspend fun download(url: String, private: Boolean): Bitmap? {
        val request = WebRequest.Builder(url)
            .header("Accept", "image/png,image/x-icon,image/webp,image/*;q=0.8")
            .cacheMode(WebRequest.CACHE_MODE_DEFAULT)
            .build()
        val flags = GeckoWebExecutor.FETCH_FLAGS_ANONYMOUS or (if (private) GeckoWebExecutor.FETCH_FLAGS_PRIVATE else 0)
        val response = try {
            executor.fetch(request, flags).await()
        } catch (e: Exception) {
            null
        } ?: return null
        val body = response.body
        if (response.statusCode !in 200..299 || body == null || !UrlInput.isWebUrl(response.uri)) {
            withContext(Dispatchers.IO) { runCatching { body?.close() } }
            return null
        }
        return withContext(Dispatchers.IO) {
            try {
                body.use { readBounded(it) }?.let(::decode)
            } catch (e: Exception) {
                Log.w(TAG, "Icon could not be read")
                null
            }
        }
    }

    private suspend fun readDisk(url: String): Bitmap? = withContext(Dispatchers.IO) {
        val file = fileFor(url)
        if (file.isFile) BitmapFactory.decodeFile(file.path) else null
    }

    private suspend fun writeDisk(url: String, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        runCatching {
            dir.mkdirs()
            fileFor(url).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val files = dir.listFiles().orEmpty()
            if (files.size > MAX_DISK_ENTRIES) {
                files.sortedBy { it.lastModified() }.take(files.size - MAX_DISK_ENTRIES).forEach { it.delete() }
            }
        }
    }

    private fun fileFor(url: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        return File(dir, digest.joinToString("") { "%02x".format(it) } + ".png")
    }

    private fun readBounded(input: InputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_BYTES) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= TARGET_PX && bounds.outHeight / (sample * 2) >= TARGET_PX) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        if (bitmap.width <= TARGET_PX && bitmap.height <= TARGET_PX) return bitmap
        return bitmap.scale(TARGET_PX, (TARGET_PX * bitmap.height / bitmap.width).coerceAtLeast(1))
    }

    companion object {
        private const val TAG = "FaviconRepository"
        private const val DIR_NAME = "favicons"
        private const val MEMORY_ENTRIES = 128
        private const val MAX_DISK_ENTRIES = 500
        private const val MAX_BYTES = 512 * 1024
        private const val TARGET_PX = 128
    }
}
