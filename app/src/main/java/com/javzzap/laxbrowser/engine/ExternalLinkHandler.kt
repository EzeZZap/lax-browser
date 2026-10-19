package com.javzzap.laxbrowser.engine

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.javzzap.laxbrowser.domain.UrlInput
import java.net.URISyntaxException

/**
 * Hands non-web links (tel:, mailto:, intent:, market:, ...) from untrusted pages to other apps.
 * Intents are sanitized the way Chrome does it: no explicit component or selector, BROWSABLE
 * category, no URI permission grants, and never resolved back into this app.
 */
class ExternalLinkHandler(private val context: Context) {

    sealed interface Result {
        data object Launched : Result
        data class Fallback(val url: String) : Result
        data object Blocked : Result
    }

    fun isWebScheme(uri: String): Boolean = schemeOf(uri) in engineSchemes

    fun open(uri: String): Result {
        val intent = toSafeIntent(uri) ?: return Result.Blocked
        val fallback = intent.getStringExtra(EXTRA_FALLBACK_URL)?.takeIf { UrlInput.isWebUrl(it) }
        // Package visibility can hide other apps from resolveActivity, but never this app.
        if (intent.resolveActivity(context.packageManager)?.packageName == context.packageName) {
            return fallback?.let { Result.Fallback(it) } ?: Result.Blocked
        }
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Result.Launched
        } catch (_: ActivityNotFoundException) {
            fallback?.let { Result.Fallback(it) } ?: Result.Blocked
        } catch (_: SecurityException) {
            Result.Blocked
        }
    }

    internal fun toSafeIntent(uri: String): Intent? {
        val scheme = schemeOf(uri) ?: return null
        if (scheme in engineSchemes || scheme in blockedSchemes) return null
        val intent = if (scheme == "intent") {
            try {
                Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
            } catch (_: URISyntaxException) {
                return null
            }
        } else {
            Intent(Intent.ACTION_VIEW, uri.toUri())
        }
        intent.data?.scheme?.lowercase()?.let { if (it in blockedSchemes) return null }
        intent.component = null
        intent.selector = null
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.flags = intent.flags and GRANT_FLAGS.inv()
        return intent
    }

    private fun schemeOf(uri: String): String? =
        uri.substringBefore(':', missingDelimiterValue = "").lowercase().takeIf { it.isNotEmpty() && ':' in uri }

    companion object {
        private const val EXTRA_FALLBACK_URL = "browser_fallback_url"

        /** Schemes Gecko handles itself. */
        private val engineSchemes = setOf(
            "http", "https", "about", "data", "blob", "javascript", "view-source", "moz-extension", "resource", "chrome",
        )

        private val blockedSchemes = setOf("file", "content", "jar")

        private const val GRANT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
    }
}
