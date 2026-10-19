package com.javzzap.laxbrowser.platform

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.IntentCompat
import androidx.core.content.pm.PackageInfoCompat
import com.javzzap.laxbrowser.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Where an in-app update is; null when none is running. */
sealed interface UpdateProgress {
    val version: String

    data class Downloading(override val version: String, val fraction: Float?) : UpdateProgress

    /** Downloaded and checked; installing waits for the user (for example after closing the system dialog). */
    data class Ready(override val version: String) : UpdateProgress
    data class NeedsPermission(override val version: String) : UpdateProgress
    data class Installing(override val version: String) : UpdateProgress
    data class Failed(override val version: String, val reason: Reason) : UpdateProgress

    enum class Reason { DOWNLOAD, VERIFY, INSTALL }
}

/**
 * Downloads a GitHub release APK and hands it to the system installer, which asks the user to
 * confirm. Before installing, the APK must be Lax with a higher version code and signed with the
 * same certificate as the installed app (Android enforces the same, this just fails earlier with
 * a clear message). Only GitHub builds use this; F-Droid installs its own updates.
 */
class UpdateInstaller(context: Context, private val scope: CoroutineScope) {
    private val appContext = context.applicationContext
    private val mutableProgress = MutableStateFlow<UpdateProgress?>(null)
    val progress: StateFlow<UpdateProgress?> = mutableProgress.asStateFlow()
    private var job: Job? = null
    private var ready: Pair<String, File>? = null

    private val directory get() = File(appContext.cacheDir, "updates")

    /** Whether the user allowed Lax to install apps (Settings > Apps > Special access). */
    fun canInstallApps(): Boolean = appContext.packageManager.canRequestPackageInstalls()

    fun start(info: UpdateInfo) {
        val url = info.apkUrl ?: return
        if (job?.isActive == true) return
        job = scope.launch {
            mutableProgress.value = UpdateProgress.Downloading(info.version, null)
            val file = withContext(Dispatchers.IO) {
                runCatching { download(url, info.version) }.onFailure { Log.w(TAG, "Update download failed (${it.javaClass.simpleName})") }.getOrNull()
            }
            if (file == null) {
                fail(info.version, UpdateProgress.Reason.DOWNLOAD)
                return@launch
            }
            if (!withContext(Dispatchers.IO) { runCatching { isValidUpdate(file) }.getOrDefault(false) }) {
                file.delete()
                fail(info.version, UpdateProgress.Reason.VERIFY)
                return@launch
            }
            ready = info.version to file
            install()
        }
    }

    /** Installs the downloaded APK, or asks for the install permission first. */
    fun install() {
        val (version, file) = ready ?: return
        if (!canInstallApps()) {
            mutableProgress.value = UpdateProgress.NeedsPermission(version)
            return
        }
        mutableProgress.value = UpdateProgress.Installing(version)
        scope.launch {
            val committed = withContext(Dispatchers.IO) {
                runCatching { commit(file) }.onFailure { Log.w(TAG, "Update install failed (${it.javaClass.simpleName})") }.isSuccess
            }
            if (!committed) fail(version, UpdateProgress.Reason.INSTALL)
        }
    }

    /** Stops a download, or closes a finished or failed update. */
    fun dismiss() {
        job?.cancel()
        job = null
        ready = null
        mutableProgress.value = null
        scope.launch(Dispatchers.IO) { directory.deleteRecursively() }
    }

    internal fun onInstallStatus(status: Int) {
        val version = mutableProgress.value?.version ?: return
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> dismiss()
            // The user closed the system dialog: keep the APK so "Install" can be tapped again.
            PackageInstaller.STATUS_FAILURE_ABORTED -> mutableProgress.value = UpdateProgress.Ready(version)
            else -> fail(version, UpdateProgress.Reason.INSTALL)
        }
    }

    private fun fail(version: String, reason: UpdateProgress.Reason) {
        mutableProgress.value = UpdateProgress.Failed(version, reason)
    }

    private suspend fun download(url: String, version: String): File {
        directory.deleteRecursively()
        directory.mkdirs()
        val file = File(directory, "lax-${version.filter { it.isLetterOrDigit() || it == '.' || it == '-' }}.apk")
        // GitHub redirects release downloads to its file host; HttpURLConnection follows https-to-https redirects.
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", "Lax-updater")
        }
        try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            val total = connection.contentLengthLong.takeIf { it > 0 }
            if (total != null && total > MAX_BYTES) error("Too large")
            var copied = 0L
            var lastReported = -1
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (copied > MAX_BYTES) error("Too large")
                        val percent = total?.let { (copied * 100 / it).toInt() } ?: -1
                        if (percent != lastReported) {
                            lastReported = percent
                            mutableProgress.value = UpdateProgress.Downloading(version, total?.let { copied.toFloat() / it })
                        }
                        currentCoroutineContext().ensureActive()
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        return file
    }

    private fun isValidUpdate(file: File): Boolean {
        val pm = appContext.packageManager
        val archive = packageInfo { flags -> pm.getPackageArchiveInfo(file.path, flags) } ?: return false
        val installed = packageInfo { flags -> pm.getPackageInfo(appContext.packageName, flags) } ?: return false
        if (archive.packageName != appContext.packageName) return false
        if (PackageInfoCompat.getLongVersionCode(archive) <= PackageInfoCompat.getLongVersionCode(installed)) return false
        val expected = certificates(installed)
        return expected.isNotEmpty() && certificates(archive) == expected
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(read: (Int) -> PackageInfo?): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) read(PackageManager.GET_SIGNING_CERTIFICATES) else read(PackageManager.GET_SIGNATURES)

    @Suppress("DEPRECATION")
    private fun certificates(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.signingInfo?.apkContentsSigners else info.signatures
        val sha256 = MessageDigest.getInstance("SHA-256")
        return signatures.orEmpty().mapTo(HashSet()) { signature -> sha256.digest(signature.toByteArray()).joinToString("") { "%02x".format(it) } }
    }

    private fun commit(file: File) {
        val installer = appContext.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(appContext.packageName)
            setSize(file.length())
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("lax.apk", 0, file.length()).use { output ->
                file.inputStream().use { it.copyTo(output) }
                session.fsync(output)
            }
            val intent = Intent(appContext, UpdateInstallReceiver::class.java).setPackage(appContext.packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            session.commit(PendingIntent.getBroadcast(appContext, sessionId, intent, flags).intentSender)
        }
    }

    companion object {
        private const val TAG = "UpdateInstaller"
        private const val TIMEOUT_MS = 30_000
        private const val MAX_BYTES = 400L * 1024 * 1024
    }
}

/** Results from the system installer: opens its confirmation screen, or reports failures. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java) ?: return
            runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return
        }
        context.appContainer.updateInstaller.onInstallStatus(status)
    }
}
