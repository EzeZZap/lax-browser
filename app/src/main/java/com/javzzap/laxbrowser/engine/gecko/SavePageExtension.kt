package com.javzzap.laxbrowser.engine.gecko

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/** One file inside a saved-page archive (HTML or a linked resource). */
data class SavedPageFile(val path: String, val mime: String, val bytes: ByteArray)

/** Page HTML plus rewritten resource files, ready to ZIP. */
data class SavedPageArchive(val title: String, val html: ByteArray, val resources: List<SavedPageFile>)

/**
 * Built-in extension (assets/extensions/savepage) that, on request, collects the top-level page
 * and linked http(s) resources with the page's cookies, then streams them to the app.
 */
class SavePageExtension {
    private var extension: WebExtension? = null
    private var installing = false
    private val pendingAttach = mutableListOf<WeakReference<GeckoSession>>()
    private val ports = ConcurrentHashMap<GeckoSession, WebExtension.Port>()
    private val inFlight = ConcurrentHashMap<WebExtension.Port, Pending>()

    private class Pending(
        val deferred: CompletableDeferred<SavedPageArchive>,
        var title: String = "",
        var html: ByteArray? = null,
        val resources: MutableList<SavedPageFile> = mutableListOf(),
        var totalBytes: Long = 0,
    )

    fun install(runtime: GeckoRuntime) {
        if (installing || extension != null) return
        installing = true
        val controller = runtime.webExtensionController
        controller.ensureBuiltIn(LOCATION, ID)
            .then { installed -> installed?.let { controller.setAllowedInPrivateBrowsing(it, true) } }
            .accept(
                { installed ->
                    extension = installed
                    pendingAttach.mapNotNull { it.get() }.forEach(::attach)
                    pendingAttach.clear()
                },
                { error -> Log.w(TAG, "Save-page extension unavailable", error) },
            )
    }

    fun attach(session: GeckoSession) {
        val ext = extension
        if (ext == null) {
            pendingAttach += WeakReference(session)
            return
        }
        session.webExtensionController.setMessageDelegate(ext, delegateFor(session), NATIVE_APP)
    }

    /**
     * Asks the content script on [session] for an archive. Returns null when the helper is not
     * connected, the page fails, or the timeout elapses.
     */
    suspend fun collect(session: GeckoSession): SavedPageArchive? {
        val port = ports[session] ?: return null
        if (inFlight.containsKey(port)) return null
        val deferred = CompletableDeferred<SavedPageArchive>()
        inFlight[port] = Pending(deferred)
        return try {
            runCatching { port.postMessage(JSONObject().put("type", "save")) }.getOrElse {
                inFlight.remove(port)
                return null
            }
            withTimeoutOrNull(TIMEOUT_MS) { runCatching { deferred.await() }.getOrNull() }
        } finally {
            inFlight.remove(port)
        }
    }

    private fun delegateFor(session: GeckoSession) = object : WebExtension.MessageDelegate {
        override fun onConnect(port: WebExtension.Port) {
            if (port.name != NATIVE_APP) return
            if (port.sender.environmentType != WebExtension.MessageSender.ENV_TYPE_CONTENT_SCRIPT || !port.sender.isTopLevel) return
            ports[session] = port
            port.setDelegate(object : WebExtension.PortDelegate {
                override fun onPortMessage(message: Any, port: WebExtension.Port) {
                    handle(message, port)
                }

                override fun onDisconnect(port: WebExtension.Port) {
                    ports.remove(session, port)
                    inFlight.remove(port)?.deferred?.completeExceptionally(IllegalStateException("disconnected"))
                }
            })
        }

        override fun onMessage(nativeApp: String, message: Any, sender: WebExtension.MessageSender): GeckoResult<Any>? = null
    }

    private fun handle(message: Any, port: WebExtension.Port) {
        val pending = inFlight[port] ?: return
        val json = message as? JSONObject ?: return
        when (json.optString("type")) {
            "meta" -> {
                val html = json.optString("html")
                if (html.isEmpty() || html.length > MAX_HTML_CHARS) {
                    fail(pending, port, "bad html")
                    return
                }
                pending.title = json.optString("title").take(MAX_TITLE)
                pending.html = html.toByteArray(Charsets.UTF_8)
                pending.totalBytes += pending.html!!.size.toLong()
            }
            "resource" -> {
                if (pending.resources.size >= MAX_RESOURCES) return
                val path = sanitizePath(json.optString("path")) ?: return
                val data = json.optString("data")
                if (data.isEmpty() || data.length > MAX_B64_CHARS) return
                val bytes = runCatching { Base64.decode(data, Base64.DEFAULT) }.getOrNull() ?: return
                if (bytes.isEmpty() || bytes.size > MAX_RESOURCE_BYTES) return
                if (pending.totalBytes + bytes.size > MAX_TOTAL_BYTES) return
                pending.totalBytes += bytes.size
                val mime = json.optString("mime").takeIf { it.isNotBlank() && it.length <= 100 } ?: "application/octet-stream"
                pending.resources += SavedPageFile(path, mime, bytes)
            }
            "done" -> {
                val html = pending.html
                if (html == null) {
                    fail(pending, port, "missing html")
                    return
                }
                inFlight.remove(port)
                pending.deferred.complete(SavedPageArchive(pending.title, html, pending.resources.toList()))
            }
            "error" -> fail(pending, port, json.optString("message").ifBlank { "error" })
        }
    }

    private fun fail(pending: Pending, port: WebExtension.Port, reason: String) {
        inFlight.remove(port)
        pending.deferred.completeExceptionally(IllegalStateException(reason))
    }

    /** Only relative `index.html` siblings under `resources/` with no `..` segments. */
    private fun sanitizePath(raw: String): String? {
        val path = raw.trim().replace('\\', '/')
        if (path.isEmpty() || path.length > MAX_PATH || path.startsWith('/') || path.contains("..")) return null
        if (!path.startsWith("resources/")) return null
        val name = path.removePrefix("resources/")
        if (name.isEmpty() || '/' in name || name.any { it.code < 32 }) return null
        return "resources/$name"
    }

    companion object {
        private const val TAG = "SavePageExtension"
        private const val LOCATION = "resource://android/assets/extensions/savepage/"
        private const val ID = "savepage@laxbrowser.javzzap.com"
        private const val NATIVE_APP = "laxbrowser-save"
        private const val TIMEOUT_MS = 60_000L
        private const val MAX_TITLE = 200
        private const val MAX_HTML_CHARS = 5 * 1024 * 1024
        private const val MAX_B64_CHARS = (4 * 1024 * 1024 * 4) / 3 + 64
        private const val MAX_RESOURCE_BYTES = 4 * 1024 * 1024
        private const val MAX_TOTAL_BYTES = 40L * 1024 * 1024
        private const val MAX_RESOURCES = 80
        private const val MAX_PATH = 120
    }
}
