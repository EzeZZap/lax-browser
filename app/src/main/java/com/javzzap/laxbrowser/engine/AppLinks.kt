package com.javzzap.laxbrowser.engine

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.edit
import androidx.core.net.toUri
import com.javzzap.laxbrowser.domain.UrlInput

/** An installed app (not a browser) that can open a web address, for example YouTube or Maps. */
data class LinkApp(val packageName: String, val label: String)

/** When links that an app can open go to that app. */
enum class AppLinkMode { ASK, ALWAYS, NEVER }

/**
 * "Open links in apps", like Firefox: finds a non-browser app for a web address and remembers
 * the user's choice per app ("don't ask again"). Preferences are small and device-specific, so
 * they live in SharedPreferences rather than the browser database.
 */
class AppLinks(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var browsers: Set<String> = emptySet()
    private var browsersAt = 0L

    var mode: AppLinkMode
        get() = AppLinkMode.entries.firstOrNull { it.name == prefs.getString(KEY_MODE, null) } ?: AppLinkMode.ASK
        set(value) = prefs.edit { putString(KEY_MODE, value.name) }

    /** True: always open in the app; false: never ask, stay in Lax; null: no saved choice. */
    fun choiceFor(packageName: String): Boolean? =
        if (prefs.contains(CHOICE_PREFIX + packageName)) prefs.getBoolean(CHOICE_PREFIX + packageName, false) else null

    fun rememberChoice(packageName: String, openInApp: Boolean) = prefs.edit { putBoolean(CHOICE_PREFIX + packageName, openInApp) }

    fun rememberedCount(): Int = prefs.all.keys.count { it.startsWith(CHOICE_PREFIX) }

    fun clearChoices() = prefs.edit { prefs.all.keys.filter { it.startsWith(CHOICE_PREFIX) }.forEach(::remove) }

    /** The app for [url], or null when only browsers (or nothing) can open it. */
    fun appFor(url: String): LinkApp? {
        if (!UrlInput.isWebUrl(url)) return null
        val pm = context.packageManager
        val handlers = runCatching { pm.queryIntentActivities(viewIntent(url), PackageManager.MATCH_DEFAULT_ONLY) }.getOrNull().orEmpty()
        if (handlers.isEmpty()) return null
        val browserPackages = browserPackages(pm)
        val app = handlers.firstOrNull { it.activityInfo.packageName != context.packageName && it.activityInfo.packageName !in browserPackages }
            ?: return null
        return LinkApp(app.activityInfo.packageName, app.loadLabel(pm).toString())
    }

    /** Opens [url] in [app]. False when it could not be started (the page then loads in Lax). */
    fun open(app: LinkApp, url: String): Boolean = try {
        context.startActivity(viewIntent(url).setPackage(app.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    /** Apps that open any web address (browsers), so they are never offered as "the app". */
    private fun browserPackages(pm: PackageManager): Set<String> {
        val now = SystemClock.elapsedRealtime()
        if (now - browsersAt < BROWSER_CACHE_MS && browsers.isNotEmpty()) return browsers
        browsers = runCatching { pm.queryIntentActivities(viewIntent(GENERIC_URL), PackageManager.MATCH_ALL) }.getOrNull().orEmpty()
            .map { it.activityInfo.packageName }
            .toSet()
        browsersAt = now
        return browsers
    }

    private fun viewIntent(url: String) = Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)

    private companion object {
        const val PREFS = "app_links"
        const val KEY_MODE = "mode"
        const val CHOICE_PREFIX = "choice:"
        const val GENERIC_URL = "https://lax-browser-check.invalid/"
        const val BROWSER_CACHE_MS = 60_000L
    }
}
