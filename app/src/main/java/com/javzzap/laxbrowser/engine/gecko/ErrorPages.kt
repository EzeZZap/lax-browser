package com.javzzap.laxbrowser.engine.gecko

import android.content.Context
import androidx.annotation.StringRes
import com.javzzap.laxbrowser.R
import org.mozilla.geckoview.WebRequestError
import java.util.Base64

/** What went wrong when a page failed to load, as shown to the user. */
enum class LoadErrorKind(@StringRes val title: Int, @StringRes val body: Int, val canRetry: Boolean = true) {
    NOT_FOUND(R.string.error_not_found_title, R.string.error_not_found_body),
    OFFLINE(R.string.error_offline_title, R.string.error_offline_body),
    CONNECTION(R.string.error_connection_title, R.string.error_connection_body),
    TIMEOUT(R.string.error_timeout_title, R.string.error_timeout_body),
    INSECURE(R.string.error_insecure_title, R.string.error_insecure_body, canRetry = false),
    HTTPS_ONLY(R.string.error_https_only_title, R.string.error_https_only_body, canRetry = false),
    HARMFUL(R.string.error_harmful_title, R.string.error_harmful_body, canRetry = false),
    INVALID(R.string.error_invalid_title, R.string.error_invalid_body, canRetry = false),
    CRASHED(R.string.error_crashed_title, R.string.error_crashed_body),
    OTHER(R.string.error_other_title, R.string.error_other_body),
    ;

    companion object {
        /** Maps GeckoView's error code (and category as a fallback). Pure, so it is unit-tested. */
        fun of(code: Int, category: Int): LoadErrorKind = when (code) {
            WebRequestError.ERROR_UNKNOWN_HOST, WebRequestError.ERROR_UNKNOWN_PROXY_HOST -> NOT_FOUND
            WebRequestError.ERROR_OFFLINE -> OFFLINE
            WebRequestError.ERROR_NET_TIMEOUT -> TIMEOUT
            WebRequestError.ERROR_CONNECTION_REFUSED, WebRequestError.ERROR_NET_RESET, WebRequestError.ERROR_NET_INTERRUPT,
            WebRequestError.ERROR_PROXY_CONNECTION_REFUSED, WebRequestError.ERROR_PORT_BLOCKED, WebRequestError.ERROR_REDIRECT_LOOP,
            -> CONNECTION
            WebRequestError.ERROR_SECURITY_SSL, WebRequestError.ERROR_SECURITY_BAD_CERT, WebRequestError.ERROR_BAD_HSTS_CERT -> INSECURE
            WebRequestError.ERROR_HTTPS_ONLY -> HTTPS_ONLY
            WebRequestError.ERROR_SAFEBROWSING_MALWARE_URI, WebRequestError.ERROR_SAFEBROWSING_UNWANTED_URI,
            WebRequestError.ERROR_SAFEBROWSING_HARMFUL_URI, WebRequestError.ERROR_SAFEBROWSING_PHISHING_URI, WebRequestError.ERROR_HARMFULADDON_URI,
            -> HARMFUL
            WebRequestError.ERROR_MALFORMED_URI, WebRequestError.ERROR_UNKNOWN_PROTOCOL -> INVALID
            WebRequestError.ERROR_CONTENT_CRASHED -> CRASHED
            else -> when (category) {
                WebRequestError.ERROR_CATEGORY_SECURITY -> INSECURE
                WebRequestError.ERROR_CATEGORY_NETWORK, WebRequestError.ERROR_CATEGORY_PROXY -> CONNECTION
                WebRequestError.ERROR_CATEGORY_SAFEBROWSING -> HARMFUL
                WebRequestError.ERROR_CATEGORY_URI -> INVALID
                else -> OTHER
            }
        }

        /**
         * Whether the secure connection itself failed, so an address Lax upgraded to https:// may
         * be tried over http://. An unknown host is not one: plain HTTP would fail the same way.
         */
        fun allowsHttpFallback(code: Int): Boolean = code in setOf(
            WebRequestError.ERROR_CONNECTION_REFUSED,
            WebRequestError.ERROR_NET_TIMEOUT,
            WebRequestError.ERROR_NET_RESET,
            WebRequestError.ERROR_NET_INTERRUPT,
            WebRequestError.ERROR_SECURITY_SSL,
            WebRequestError.ERROR_SECURITY_BAD_CERT,
        )
    }
}

/**
 * Lax-styled error pages, used instead of Gecko's built-in ones. The page is a self-contained
 * `data:` document (no network or app resources), adapts to light and dark mode, and only
 * contains escaped text: the failed address comes from the page load and is untrusted.
 */
class ErrorPages(context: Context) {
    private val resources = context.applicationContext.resources

    fun pageFor(url: String?, error: WebRequestError): String {
        val kind = LoadErrorKind.of(error.code, error.category)
        return dataUri(html(kind, url))
    }

    private fun html(kind: LoadErrorKind, url: String?): String {
        val address = url?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        val retry = if (kind.canRetry && address != null) {
            """<a class="primary" href="${escape(address)}">${escape(resources.getString(R.string.error_try_again))}</a>"""
        } else {
            ""
        }
        val back = """<a class="secondary" href="javascript:history.back()">${escape(resources.getString(R.string.error_go_back))}</a>"""
        return """
            <!DOCTYPE html>
            <html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
            <meta name="color-scheme" content="light dark"><title>${escape(resources.getString(kind.title))}</title>
            <style>
              :root { --bg:#FCF9FF; --fg:#1C1A24; --muted:#4A4558; --accent:#6A4FD8; --card:#F1EDF8; }
              @media (prefers-color-scheme: dark) { :root { --bg:#13111A; --fg:#E6E1EE; --muted:#CAC4D4; --accent:#CDBDFF; --card:#1F1C28; } }
              html,body { margin:0; height:100%; background:var(--bg); color:var(--fg); font-family:system-ui,sans-serif; }
              main { min-height:100%; box-sizing:border-box; display:flex; flex-direction:column; justify-content:center; padding:32px 28px; max-width:560px; margin:0 auto; }
              .badge { width:72px; height:72px; border-radius:22px; background:linear-gradient(135deg,#C4A6FB,#A3BDE8 50%,#66E8B8); display:flex; align-items:center; justify-content:center; margin-bottom:28px; }
              h1 { font-size:24px; line-height:1.25; margin:0 0 12px; }
              p { color:var(--muted); font-size:16px; line-height:1.5; margin:0 0 16px; }
              .url { background:var(--card); border-radius:12px; padding:10px 14px; font-size:13px; word-break:break-all; color:var(--muted); margin-bottom:28px; }
              .actions { display:flex; gap:12px; flex-wrap:wrap; }
              a { text-decoration:none; font-weight:600; font-size:15px; padding:12px 22px; border-radius:999px; }
              .primary { background:var(--accent); color:var(--bg); }
              .secondary { color:var(--accent); border:1.5px solid var(--accent); }
            </style></head>
            <body><main>
              <div class="badge"><svg width="40" height="40" viewBox="36 26 38 56" aria-hidden="true"><path fill="#fff" d="M46,30 H50.5 A6,6 0 0 1 56.5,36 V65 A3,3 0 0 0 59.5,68 H66 A3,3 0 0 1 69,71 V75 A3,3 0 0 1 66,78 H46 A6,6 0 0 1 40,72 V36 A6,6 0 0 1 46,30 Z"/></svg></div>
              <h1>${escape(resources.getString(kind.title))}</h1>
              <p>${escape(resources.getString(kind.body))}</p>
              ${address?.let { """<div class="url">${escape(it)}</div>""" }.orEmpty()}
              <div class="actions">$retry$back</div>
            </main></body></html>
        """.trimIndent()
    }

    companion object {
        fun dataUri(html: String): String =
            "data:text/html;charset=utf-8;base64," + Base64.getEncoder().encodeToString(html.toByteArray(Charsets.UTF_8))

        fun escape(text: String): String = buildString(text.length) {
            for (c in text) {
                when (c) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&#39;")
                    else -> append(c)
                }
            }
        }
    }
}
