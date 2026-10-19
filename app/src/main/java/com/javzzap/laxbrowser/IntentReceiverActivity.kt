package com.javzzap.laxbrowser

import android.app.Activity
import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsIntent
import com.javzzap.laxbrowser.customtabs.CustomTabActivity
import com.javzzap.laxbrowser.domain.UrlInput

/**
 * Entry point for intents from other apps. It has no UI: it routes links to a Custom Tab (when
 * the caller asked for one) or to the main browser, and shared or selected text to a search.
 * Only the address and known, typed extras are forwarded; the rest of the untrusted intent is
 * dropped.
 */
class IntentReceiverActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        route(intent)
        finish()
    }

    private fun route(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val url = intent.dataString?.takeIf(UrlInput::isWebUrl) ?: return
                val fromShortcut = intent.getBooleanExtra(EXTRA_FROM_SHORTCUT, false)
                if (!fromShortcut && intent.hasExtra(CustomTabsIntent.EXTRA_SESSION)) {
                    // Custom Tabs open in the calling app's task, so no NEW_TASK flag.
                    startActivity(CustomTabActivity.intent(this, url, intent))
                } else {
                    startActivity(mainIntent().setAction(Intent.ACTION_VIEW).setData(intent.data))
                }
            }
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let(::openText)
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.let(::openText)
            Intent.ACTION_WEB_SEARCH -> intent.getStringExtra(SearchManager.QUERY)?.let(::openText)
        }
    }

    private fun openText(text: String) {
        if (text.isBlank()) return
        // Shared through the "Open in Lax…" entry: the browser asks where to open it.
        val ask = intent.component?.className?.endsWith(ASK_ALIAS) == true
        startActivity(
            mainIntent()
                .setAction(MainActivity.ACTION_OPEN_TEXT)
                .putExtra(MainActivity.EXTRA_TEXT, text.take(MAX_TEXT))
                .putExtra(MainActivity.EXTRA_ASK_TARGET, ask),
        )
    }

    private fun mainIntent() = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    companion object {
        const val EXTRA_FROM_SHORTCUT = "com.javzzap.laxbrowser.FROM_SHORTCUT"
        private const val MAX_TEXT = 2000
        private const val ASK_ALIAS = ".ShareOpenInActivity"
    }
}
