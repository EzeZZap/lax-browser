package com.javzzap.laxbrowser.platform

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A newer release found on GitHub. [apkUrl] is the APK for this device's CPU, when published. */
data class UpdateInfo(
    val version: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String?,
    val isPrerelease: Boolean = false,
)

/**
 * Checks GitHub Releases of [repo] ("owner/name") for a version newer than the installed one. Only
 * the public release list is requested; nothing about the user is sent. Builds without a repo
 * (`-PlaxUpdateRepo=`) never check.
 *
 * Stable checks use `/releases/latest` (never a prerelease). With [includePrereleases], the
 * release list is scanned so testing builds tagged as GitHub prereleases can be offered.
 */
class UpdateChecker(context: Context, private val repo: String, private val currentVersion: String) {
    private val appContext = context.applicationContext
    private val prefs by lazy { appContext.getSharedPreferences("updates", Context.MODE_PRIVATE) }

    val isAvailable: Boolean get() = repo.isNotBlank()

    /** Whether an automatic check is due (once a day). Blocking: reads preferences. */
    fun isDue(now: Long): Boolean = isAvailable && now - prefs.getLong(KEY_LAST_CHECK, 0) >= CHECK_INTERVAL_MS

    /** The newest release if it is newer than this build and not dismissed; null otherwise. */
    suspend fun check(
        now: Long,
        includeDismissed: Boolean = false,
        includePrereleases: Boolean = false,
    ): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val path = if (includePrereleases) "releases?per_page=30" else "releases/latest"
            val connection = (URL("https://api.github.com/repos/$repo/$path").openConnection() as HttpURLConnection).apply {
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
                val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
                val info = if (includePrereleases) {
                    GitHubReleases.newestFromList(body, abi)
                } else {
                    GitHubReleases.fromLatest(body, abi)
                } ?: return@runCatching null
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

    companion object {
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_DISMISSED = "dismissed_version"
        private const val KEY_LATEST = "latest_version"
        private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
        private const val TIMEOUT_MS = 15_000
    }
}

/** Parses GitHub Releases JSON into [UpdateInfo]. Kept separate so unit tests need no network. */
object GitHubReleases {
    private const val MAX_NOTES = 4000

    fun fromLatest(body: String, abi: String): UpdateInfo? = parseRelease(JSONObject(body), abi, allowPrerelease = false)

    fun newestFromList(body: String, abi: String): UpdateInfo? {
        val array = JSONArray(body)
        var best: UpdateInfo? = null
        for (i in 0 until array.length()) {
            val candidate = parseRelease(array.optJSONObject(i) ?: continue, abi, allowPrerelease = true) ?: continue
            if (best == null || Versions.compare(candidate.version, best.version) > 0) best = candidate
        }
        return best
    }

    private fun parseRelease(json: JSONObject, abi: String, allowPrerelease: Boolean): UpdateInfo? {
        if (json.optBoolean("draft")) return null
        val prerelease = json.optBoolean("prerelease")
        if (prerelease && !allowPrerelease) return null
        val tag = json.optString("tag_name").takeIf { it.isNotBlank() } ?: return null
        val page = json.optString("html_url").takeIf { it.startsWith("https://github.com/") } ?: return null
        val assets = json.optJSONArray("assets")
        val apk = (0 until (assets?.length() ?: 0)).mapNotNull { assets?.optJSONObject(it) }
            .map { it.optString("name") to it.optString("browser_download_url") }
            .filter { (name, url) -> name.endsWith(".apk") && url.startsWith("https://github.com/") }
            .let { list -> list.firstOrNull { abi.isNotEmpty() && abi in it.first } ?: list.singleOrNull() }
            ?.second
        return UpdateInfo(
            version = tag.removePrefix("v"),
            notes = json.optString("body").take(MAX_NOTES),
            pageUrl = page,
            apkUrl = apk,
            isPrerelease = prerelease,
        )
    }
}

/**
 * Semantic-ish version comparison: "1.10.0" is newer than "1.9.2"; a leading "v" is ignored.
 * Prerelease suffixes follow SemVer: `1.0.0` > `1.0.0-testing.2` > `1.0.0-testing.1`.
 */
object Versions {
    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0

    fun compare(a: String, b: String): Int {
        val left = parse(a)
        val right = parse(b)
        for (i in 0 until maxOf(left.core.size, right.core.size)) {
            val x = left.core.getOrElse(i) { 0 }
            val y = right.core.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return when {
            left.pre == null && right.pre == null -> 0
            left.pre == null -> 1
            right.pre == null -> -1
            else -> comparePre(left.pre, right.pre)
        }
    }

    private data class Parsed(val core: List<Int>, val pre: List<String>?)

    private fun parse(version: String): Parsed {
        val cleaned = version.trim().removePrefix("v").substringBefore('+')
        val dash = cleaned.indexOf('-')
        val corePart = if (dash >= 0) cleaned.substring(0, dash) else cleaned
        val prePart = if (dash >= 0) cleaned.substring(dash + 1) else null
        val core = corePart.split('.').map { it.toIntOrNull() ?: 0 }
        val pre = prePart?.split('.')?.filter { it.isNotEmpty() }?.takeIf { it.isNotEmpty() }
        return Parsed(core, pre)
    }

    private fun comparePre(a: List<String>, b: List<String>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrNull(i)
            val y = b.getOrNull(i)
            when {
                x == null -> return -1
                y == null -> return 1
                else -> {
                    val xn = x.toIntOrNull()
                    val yn = y.toIntOrNull()
                    val cmp = when {
                        xn != null && yn != null -> xn.compareTo(yn)
                        xn != null -> -1
                        yn != null -> 1
                        else -> x.compareTo(y)
                    }
                    if (cmp != 0) return cmp
                }
            }
        }
        return 0
    }
}
