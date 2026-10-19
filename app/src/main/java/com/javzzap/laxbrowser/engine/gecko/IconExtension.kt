package com.javzzap.laxbrowser.engine.gecko

import android.util.Log
import com.javzzap.laxbrowser.domain.IconCandidate
import com.javzzap.laxbrowser.pwa.WebAppManifestParser
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import java.lang.ref.WeakReference

/**
 * GeckoView has no favicon or page zoom API, so a built-in extension (assets/extensions/icons)
 * reports the icons each top-level page declares and applies per-site zoom. Messages are
 * untrusted page-derived data: only well-formed messages from the top-level content script are
 * accepted, and the page URL is taken from the sender rather than from the message.
 */
class IconExtension(
    private val onIcons: (session: GeckoSession, pageUrl: String, candidates: List<IconCandidate>) -> Unit,
    private val zoomFor: (pageUrl: String) -> Int,
) {
    private var extension: WebExtension? = null
    private var installing = false
    private val pending = mutableListOf<WeakReference<GeckoSession>>()

    /** The zoom port of each page, opened by zoom.js when the page starts loading. */
    private val zoomPorts = LinkedHashSet<WebExtension.Port>()

    /** Sends every open page its site's current zoom level. */
    fun refreshZoom() {
        zoomPorts.forEach(::sendZoom)
    }

    private fun sendZoom(port: WebExtension.Port) {
        val percent = zoomFor(port.sender.url)
        runCatching { port.postMessage(JSONObject().put("type", "zoom").put("percent", percent)) }
    }

    fun install(runtime: GeckoRuntime) {
        if (installing || extension != null) return
        installing = true
        val controller = runtime.webExtensionController
        controller.ensureBuiltIn(LOCATION, ID)
            // Icons of private pages are only kept in memory, so the helper may run in private tabs.
            .then { installed -> installed?.let { controller.setAllowedInPrivateBrowsing(it, true) } }
            .accept(
                { installed ->
                    extension = installed
                    pending.mapNotNull { it.get() }.forEach(::attach)
                    pending.clear()
                },
                { error -> Log.w(TAG, "Icon extension unavailable", error) },
            )
    }

    /** Starts receiving icon reports for [session]. Safe to call before the extension is ready. */
    fun attach(session: GeckoSession) {
        val ext = extension
        if (ext == null) {
            pending += WeakReference(session)
            return
        }
        session.webExtensionController.setMessageDelegate(ext, delegateFor(session), NATIVE_APP)
    }

    private fun delegateFor(session: GeckoSession) = object : WebExtension.MessageDelegate {
        override fun onMessage(nativeApp: String, message: Any, sender: WebExtension.MessageSender): GeckoResult<Any>? {
            if (nativeApp != NATIVE_APP) return null
            if (sender.environmentType != WebExtension.MessageSender.ENV_TYPE_CONTENT_SCRIPT || !sender.isTopLevel) return null
            val json = message as? JSONObject ?: return null
            if (json.optString("type") != "icons") return null
            val icons = json.optJSONArray("icons") ?: return null
            val candidates = (0 until minOf(icons.length(), MAX_ICONS)).mapNotNull { index ->
                val icon = icons.optJSONObject(index) ?: return@mapNotNull null
                val href = icon.optString("href").takeIf { it.isNotBlank() && it.length <= MAX_URL_LENGTH } ?: return@mapNotNull null
                IconCandidate(
                    href = href,
                    sizes = WebAppManifestParser.parseIconSizes(icon.optString("sizes")),
                    type = icon.optString("type").takeIf { it.isNotBlank() },
                    rel = icon.optString("rel"),
                )
            }
            onIcons(session, sender.url, candidates)
            return null
        }

        override fun onConnect(port: WebExtension.Port) {
            if (port.name != NATIVE_APP) return
            if (port.sender.environmentType != WebExtension.MessageSender.ENV_TYPE_CONTENT_SCRIPT || !port.sender.isTopLevel) return
            zoomPorts += port
            port.setDelegate(object : WebExtension.PortDelegate {
                override fun onDisconnect(port: WebExtension.Port) {
                    zoomPorts -= port
                }
            })
            sendZoom(port)
        }
    }

    companion object {
        private const val TAG = "IconExtension"
        private const val LOCATION = "resource://android/assets/extensions/icons/"
        private const val ID = "icons@laxbrowser.javzzap.com"
        private const val NATIVE_APP = "laxbrowser"
        private const val MAX_ICONS = 32
        private const val MAX_URL_LENGTH = 2048
    }
}
