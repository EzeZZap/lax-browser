package com.javzzap.laxbrowser.platform

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A newer release found on GitHub. [apkUrl] is the APK for this device's CPU, when published. */
data class UpdateInfo(val version: String, val notes: String, val pageUrl: String, val apkUrl: String?)

/**
 * Checks GitHub Releases of [repo] ("owner/name") for a version newer than the installed one. Only
 * the public release list is requested; nothing about the user is sent. Builds without a repo
 * (for example F-Droid builds, which F-Droid updates itself) never check.
 */
class UpdateChecker(context: Context, private val repo: String, private val currentVersion: String) {
    private val appContext = context.applicationContext
    private val prefs by lazy { appContext.getSharedPreferences("updates", Context.MODE_PRIVATE) }

    val isAvailable: Boolean get() = repo.isNotBlank()

    /** Whether an automatic check is due (once a day). Blocking: reads preferences. */
    fun isDue(now: Long): Boolean = isAvailable && now - prefs.getLong(KEY_LAST_CHECK, 0) >= CHECK_INTERVAL_MS

    /** The newest release if it is newer than this build and not dismissed; null otherwise. */
    suspend fun check(now: Long, includeDismissed: Boolean = false): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Lax-update-check")
            }
            try {
                if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) return@runCatching null
                if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                prefs.edit { putLong(KEY_LAST_CHECK, now) }
                val info = parse(body) ?: return@runCatching null
                val newer = Versions.isNewer(info.version, currentVersion)
                prefs.edit { if (newer) putString(KEY_LATEST, info.version) else remove(KEY_LATEST) }
                val dismissed = prefs.getString(KEY_DISMISSED, null)
                info.takeIf { newer && (includeDismissed || it.version != dismissed) }
            } finally {
                connection.disconnect()
            }
        }
    }

    /**
     * The newest release found by the last check, if it is still newer than this build (also when
     * its dialog was dismissed). Blocking: reads preferences.
     */
    fun knownNewerVersion(): String? =
        prefs.getString(KEY_LATEST, null)?.takeIf { isAvailable && Versions.isNewer(it, currentVersion) }

    /** Stops reminding about [version]; a later release is offered again. */
    fun dismiss(version: String) {
        prefs.edit { putString(KEY_DISMISSED, version) }
    }

    private fun parse(body: String): UpdateInfo? {
        val json = JSONObject(body)
        if (json.optBoolean("draft") || json.optBoolean("prerelease")) return null
        val tag = json.optString("tag_name").takeIf { it.isNotBlank() } ?: return null
        val page = json.optString("html_url").takeIf { it.startsWith("https://github.com/") } ?: return null
        val assets = json.optJSONArray("assets")
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        val apk = (0 until (assets?.length() ?: 0)).mapNotNull { assets?.optJSONObject(it) }
            .map { it.optString("name") to it.optString("browser_download_url") }
            .filter { (name, url) -> name.endsWith(".apk") && url.startsWith("https://github.com/") }
            .let { list -> list.firstOrNull { abi.isNotEmpty() && abi in it.first } ?: list.singleOrNull() }
            ?.second
        return UpdateInfo(tag.removePrefix("v"), json.optString("body").take(MAX_NOTES), page, apk)
    }

    companion object {
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_DISMISSED = "dismissed_version"
        private const val KEY_LATEST = "latest_version"
        private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
        private const val TIMEOUT_MS = 15_000
        private const val MAX_NOTES = 4000
    }
}

/** Semantic-ish version comparison: "1.10.0" is newer than "1.9.2"; a leading "v" is ignored. */
object Versions {
    fun isNewer(candidate: String, current: String): Boolean {
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parts(version: String): List<Int> =
        version.trim().removePrefix("v").substringBefore('-').substringBefore('+').split('.').map { it.toIntOrNull() ?: 0 }
}
