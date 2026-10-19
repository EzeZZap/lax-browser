package com.javzzap.laxbrowser.engine.gecko

import org.mozilla.geckoview.GeckoSession

/** Translation state of one tab, from Gecko's on-device translation engine. */
data class TranslationInfo(
    val pageLanguage: String? = null,
    val userLanguage: String? = null,
    val translatedTo: String? = null,
    val inProgress: Boolean = false,
    val error: Boolean = false,
)

data class TranslationLanguage(val code: String, val name: String)

data class TranslationLanguages(val from: List<TranslationLanguage>, val to: List<TranslationLanguage>) {
    val isSupported: Boolean get() = from.isNotEmpty() && to.isNotEmpty()
}

/**
 * A link preview over the current page. Its [session] uses the opener's container ([containerId])
 * or private browsing, and can become a tab without reloading.
 */
data class GlanceState(
    val openerTabId: String,
    val containerId: String,
    val isPrivate: Boolean,
    val session: GeckoSession,
    val url: String,
    val title: String,
    val progress: Int,
    val canGoBack: Boolean = false,
)
