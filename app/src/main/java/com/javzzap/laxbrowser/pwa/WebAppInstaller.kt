package com.javzzap.laxbrowser.pwa

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.scale
import com.javzzap.laxbrowser.domain.Clock
import com.javzzap.laxbrowser.domain.ManifestIcon
import com.javzzap.laxbrowser.domain.WebApp
import com.javzzap.laxbrowser.domain.WebAppManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Installs a web app as a pinned launcher shortcut that opens [WebAppActivity] in standalone
 * mode. This is the same integration Firefox for Android uses; it is not a WebAPK and the app does
 * not appear in the system app list. All network and file work runs on [Dispatchers.IO].
 */
class WebAppInstaller(private val context: Context, private val clock: Clock) {

    fun isLauncherSupported(): Boolean = ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    /**
     * Downloads and decodes a manifest icon. The request carries no cookies or credentials of any
     * container: it goes through [HttpURLConnection], which has no cookie handler in this app.
     */
    suspend fun fetchIcon(icon: ManifestIcon): Bitmap? = withContext(Dispatchers.IO) {
        if (!WebAppEligibility.isSecureOrigin(icon.src, true)) return@withContext null
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(icon.src).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
                useCaches = false
                setRequestProperty("Accept", "image/png,image/webp,image/*;q=0.8")
            }
            if (connection.responseCode !in 200..299) return@withContext null
            if (!WebAppEligibility.isSecureOrigin(connection.url.toString(), true)) return@withContext null
            val declared = connection.contentLengthLong
            if (declared > MAX_ICON_BYTES) return@withContext null
            val bytes = connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_ICON_BYTES) return@withContext null
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
            decode(bytes)
        } catch (e: IOException) {
            Log.w(TAG, "Icon download failed")
            null
        } catch (e: RuntimeException) {
            Log.w(TAG, "Icon could not be read")
            null
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun saveIcon(appId: String, bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, ICON_DIR).apply { mkdirs() }
        val file = File(dir, "$appId.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        file.name
    }

    suspend fun loadIcon(app: WebApp): Bitmap? = withContext(Dispatchers.IO) {
        val name = app.iconFile ?: return@withContext null
        val file = File(File(context.filesDir, ICON_DIR), name)
        if (!file.isFile) null else BitmapFactory.decodeFile(file.path)
    }

    fun newWebApp(id: String, manifest: WebAppManifest, containerId: String, iconFile: String?): WebApp {
        val now = clock.now()
        return WebApp(
            id = id,
            name = manifest.displayName ?: manifest.startUrl,
            shortName = manifest.shortName,
            startUrl = manifest.startUrl,
            scope = manifest.scope,
            display = manifest.display,
            themeColor = manifest.themeColor,
            backgroundColor = manifest.backgroundColor,
            iconFile = iconFile,
            manifestUrl = manifest.manifestUrl,
            containerId = containerId,
            installedAt = now,
            updatedAt = now,
        )
    }

    /** Asks the launcher to pin the shortcut. Returns false when the launcher refused the request. */
    fun requestPin(app: WebApp, icon: Bitmap?): Boolean {
        if (!isLauncherSupported()) return false
        return ShortcutManagerCompat.requestPinShortcut(context, shortcutFor(app, icon), null)
    }

    /** Updates name and icon of an existing pinned shortcut after a manifest change. */
    fun updateShortcut(app: WebApp, icon: Bitmap?) {
        runCatching { ShortcutManagerCompat.updateShortcuts(context, listOf(shortcutFor(app, icon))) }
    }

    /** IDs of shortcuts the launcher reports as pinned. This is the source of truth for "installed". */
    fun pinnedShortcutIds(): Set<String> = runCatching {
        ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED).map { it.id }.toSet()
    }.getOrDefault(emptySet())

    /**
     * Pinned shortcuts cannot be deleted by apps; they are disabled so the launcher shows them as
     * unavailable, and the stored icon is removed.
     */
    suspend fun uninstall(app: WebApp) {
        runCatching {
            ShortcutManagerCompat.disableShortcuts(context, listOf(app.id), context.getString(com.javzzap.laxbrowser.R.string.web_app_disabled_message))
        }
        withContext(Dispatchers.IO) {
            app.iconFile?.let { File(File(context.filesDir, ICON_DIR), it).delete() }
        }
    }

    private fun shortcutFor(app: WebApp, icon: Bitmap?): ShortcutInfoCompat {
        val intent = Intent(context, WebAppActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(launchUri(app.id))
        val iconCompat = icon?.let { IconCompat.createWithBitmap(it) }
            ?: IconCompat.createWithResource(context, com.javzzap.laxbrowser.R.mipmap.ic_launcher)
        return ShortcutInfoCompat.Builder(context, app.id)
            .setShortLabel((app.shortName ?: app.name).take(SHORT_LABEL_MAX))
            .setLongLabel(app.name.take(LONG_LABEL_MAX))
            .setIcon(iconCompat)
            .setIntent(intent)
            .build()
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= TARGET_ICON_PX && bounds.outHeight / (sample * 2) >= TARGET_ICON_PX) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        if (bitmap.width <= TARGET_ICON_PX && bitmap.height <= TARGET_ICON_PX) return bitmap
        return bitmap.scale(TARGET_ICON_PX, TARGET_ICON_PX * bitmap.height / bitmap.width)
    }

    companion object {
        private const val TAG = "WebAppInstaller"
        private const val ICON_DIR = "webapp-icons"
        private const val TIMEOUT_MS = 10_000
        private const val MAX_ICON_BYTES = 2 * 1024 * 1024
        private const val TARGET_ICON_PX = 256
        private const val SHORT_LABEL_MAX = 25
        private const val LONG_LABEL_MAX = 60
        const val LAUNCH_SCHEME = "laxbrowser-webapp"

        fun launchUri(appId: String): Uri = Uri.Builder().scheme(LAUNCH_SCHEME).authority("app").appendPath(appId).build()

        fun appIdFrom(uri: Uri?): String? {
            if (uri == null || uri.scheme != LAUNCH_SCHEME || uri.authority != "app") return null
            return uri.pathSegments.singleOrNull()?.takeIf { it.isNotBlank() }
        }
    }
}
