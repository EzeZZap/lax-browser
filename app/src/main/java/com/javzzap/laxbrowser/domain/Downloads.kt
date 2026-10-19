package com.javzzap.laxbrowser.domain

enum class DownloadStatus { IN_PROGRESS, COMPLETED, FAILED, CANCELLED }

/** A file download. [contentUri] is the saved file in the shared Downloads collection. */
data class Download(
    val id: String,
    val url: String,
    val fileName: String,
    val mimeType: String?,
    val totalBytes: Long?,
    val downloadedBytes: Long,
    val status: DownloadStatus,
    val contentUri: String?,
    val createdAt: Long,
)

object DownloadNames {
    private const val MAX_NAME = 120
    private val UNSAFE = Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]")

    /**
     * A safe file name from the server's Content-Disposition header or the URL path. Path
     * separators and control characters are removed so a page cannot choose another folder.
     */
    fun fileName(contentDisposition: String?, url: String, mimeType: String?): String {
        val fromHeader = contentDisposition?.let(::fromDisposition)
        val fromUrl = url.substringBefore('#').substringBefore('?').substringAfterLast('/')
            .let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
        val raw = fromHeader?.takeIf { it.isNotBlank() } ?: fromUrl.takeIf { it.isNotBlank() } ?: "download"
        var name = raw.replace(UNSAFE, "_").trim().trimStart('.').ifBlank { "download" }
        if ('.' !in name) extensionFor(mimeType)?.let { name = "$name.$it" }
        if (name.length > MAX_NAME) {
            val ext = name.substringAfterLast('.', "").take(10)
            name = name.take(MAX_NAME - ext.length - 1).trimEnd('.') + if (ext.isNotEmpty()) ".$ext" else ""
        }
        return name
    }

    private fun fromDisposition(header: String): String? {
        // RFC 6266: filename* (RFC 5987, percent-encoded) takes precedence over filename.
        Regex("filename\\*\\s*=\\s*([^']*)'[^']*'([^;]+)", RegexOption.IGNORE_CASE).find(header)?.let { match ->
            val charset = match.groupValues[1].ifBlank { "UTF-8" }
            return runCatching { java.net.URLDecoder.decode(match.groupValues[2].trim(), charset) }.getOrNull()
        }
        Regex("filename\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE).find(header)?.let { return it.groupValues[1] }
        Regex("filename\\s*=\\s*([^;]+)", RegexOption.IGNORE_CASE).find(header)?.let { return it.groupValues[1].trim() }
        return null
    }

    private fun extensionFor(mimeType: String?): String? = when (mimeType?.substringBefore(';')?.trim()?.lowercase()) {
        "application/pdf" -> "pdf"
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "image/svg+xml" -> "svg"
        "text/plain" -> "txt"
        "text/html" -> "html"
        "application/zip" -> "zip"
        "audio/mpeg" -> "mp3"
        "video/mp4" -> "mp4"
        "application/vnd.android.package-archive" -> "apk"
        else -> null
    }
}
