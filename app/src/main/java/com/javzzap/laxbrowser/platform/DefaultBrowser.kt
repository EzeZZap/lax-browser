package com.javzzap.laxbrowser.platform

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri

/** Checks and requests the "default browser" role. */
object DefaultBrowser {
    fun isDefault(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = context.getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_BROWSER)) return roles.isRoleHeld(RoleManager.ROLE_BROWSER)
        }
        val resolved = context.packageManager.resolveActivity(Intent(Intent.ACTION_VIEW, "https://example.com".toUri()), PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    /**
     * The intent that asks the user to make Lax the default browser: Android's role dialog on
     * Android 10+, otherwise the system's default apps screen.
     */
    fun requestIntent(context: Context): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = context.getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_BROWSER) && !roles.isRoleHeld(RoleManager.ROLE_BROWSER)) {
                return roles.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
            }
        }
        return Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
    }
}

/** Open-source components shipped in Lax and their licenses (texts in assets/licenses). */
object Licenses {
    data class Component(val name: String, val license: License, val url: String)

    enum class License(val title: String, val asset: String) {
        MPL_2("Mozilla Public License 2.0", "licenses/mpl-2.0.txt"),
        APACHE_2("Apache License 2.0", "licenses/apache-2.0.txt"),
        OFL_1_1("SIL Open Font License 1.1", "licenses/ofl-1.1.txt"),
    }

    val components = listOf(
        Component("GeckoView (Mozilla)", License.MPL_2, "https://hg.mozilla.org/mozilla-central/file/tip/mobile/android/geckoview"),
        Component("Space Grotesk typeface", License.OFL_1_1, "https://github.com/floriankarsten/space-grotesk"),
        Component("Android Jetpack (AndroidX: Compose, Room, AppCompat, Browser, Media, Emoji2, Lifecycle, Core, SplashScreen)", License.APACHE_2, "https://developer.android.com/jetpack/androidx"),
        Component("Material Design icons", License.APACHE_2, "https://github.com/google/material-design-icons"),
        Component("Kotlin and kotlinx.coroutines", License.APACHE_2, "https://github.com/JetBrains/kotlin"),
        Component("ZXing", License.APACHE_2, "https://github.com/zxing/zxing"),
        Component("ZXing Android Embedded", License.APACHE_2, "https://github.com/journeyapps/zxing-android-embedded"),
    )
}
