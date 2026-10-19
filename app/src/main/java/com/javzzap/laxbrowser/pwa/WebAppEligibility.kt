package com.javzzap.laxbrowser.pwa

import com.javzzap.laxbrowser.domain.ManifestIcon
import com.javzzap.laxbrowser.domain.UrlInput
import com.javzzap.laxbrowser.domain.WebAppManifest
import java.net.URI

enum class Ineligibility {
    NO_MANIFEST,
    INSECURE,
    NO_NAME,
    DISPLAY_MODE,
    NO_ICON,
    CROSS_ORIGIN,
    PREFERS_NATIVE_APP,
}

sealed interface Eligibility {
    data class Eligible(val manifest: WebAppManifest, val icon: ManifestIcon) : Eligibility
    data class Ineligible(val reason: Ineligibility) : Eligibility
}

/**
 * Installability rules, mirroring Android Components' `WebAppManifest.isInstallable()`: a valid
 * manifest (delivered by Gecko) on a secure page, a name, an app-like display mode, and a raster
 * icon of at least [MIN_ICON_SIZE] px with purpose `any`. A manifest link alone is not enough.
 */
object WebAppEligibility {
    const val MIN_ICON_SIZE = 192
    private const val PREFERRED_MAX_ICON_SIZE = 1024
    private val installableDisplayModes = setOf("standalone", "fullscreen", "minimal-ui")

    fun check(manifest: WebAppManifest?, pageUrl: String, isSecure: Boolean): Eligibility {
        if (manifest == null) return Eligibility.Ineligible(Ineligibility.NO_MANIFEST)
        if (!isSecureOrigin(pageUrl, isSecure) || !isSecureOrigin(manifest.startUrl, true)) {
            return Eligibility.Ineligible(Ineligibility.INSECURE)
        }
        if (UrlInput.originOf(manifest.startUrl) != UrlInput.originOf(pageUrl)) {
            return Eligibility.Ineligible(Ineligibility.CROSS_ORIGIN)
        }
        if (!isInScope(manifest.startUrl, manifest.scope)) return Eligibility.Ineligible(Ineligibility.CROSS_ORIGIN)
        if (manifest.displayName == null) return Eligibility.Ineligible(Ineligibility.NO_NAME)
        if (manifest.display !in installableDisplayModes) return Eligibility.Ineligible(Ineligibility.DISPLAY_MODE)
        if (manifest.preferRelatedApplications) return Eligibility.Ineligible(Ineligibility.PREFERS_NATIVE_APP)
        val icon = bestIcon(manifest) ?: return Eligibility.Ineligible(Ineligibility.NO_ICON)
        return Eligibility.Eligible(manifest, icon)
    }

    /** The largest usable raster `any`-purpose icon of at least 192 px, preferring ones up to 1024 px. */
    fun bestIcon(manifest: WebAppManifest): ManifestIcon? {
        val candidates = manifest.icons
            .filter { "any" in it.purposes && !isSvg(it) && isSecureOrigin(it.src, true) }
            .mapNotNull { icon ->
                val size = icon.sizes.filter { !it.isAny }.maxOfOrNull { minOf(it.width, it.height) } ?: return@mapNotNull null
                if (size >= MIN_ICON_SIZE) icon to size else null
            }
        val preferred = candidates.filter { it.second <= PREFERRED_MAX_ICON_SIZE }
        return (preferred.maxByOrNull { it.second } ?: candidates.minByOrNull { it.second })?.first
    }

    /** True when [url] is same-origin with [scope] and its path starts with the scope's path. */
    fun isInScope(url: String, scope: String): Boolean {
        val u = parse(url) ?: return false
        val s = parse(scope) ?: return false
        if (!u.scheme.equals(s.scheme, ignoreCase = true)) return false
        if (!u.host.equals(s.host, ignoreCase = true)) return false
        if (effectivePort(u) != effectivePort(s)) return false
        val urlPath = u.rawPath.orEmpty().ifEmpty { "/" }
        val scopePath = s.rawPath.orEmpty().ifEmpty { "/" }
        return urlPath.startsWith(scopePath)
    }

    fun isSecureOrigin(url: String, pageReportedSecure: Boolean): Boolean {
        val uri = parse(url) ?: return false
        val host = uri.host?.lowercase() ?: return false
        return when (uri.scheme?.lowercase()) {
            "https" -> pageReportedSecure
            "http" -> host == "localhost" || host == "127.0.0.1" || host == "[::1]"
            else -> false
        }
    }

    private fun isSvg(icon: ManifestIcon): Boolean =
        icon.type?.lowercase()?.contains("svg") == true ||
            icon.src.substringBefore('?').substringBefore('#').lowercase().endsWith(".svg")

    private fun parse(url: String): URI? = try {
        URI(url).takeIf { it.scheme != null && it.host != null }
    } catch (_: Exception) {
        null
    }

    private fun effectivePort(uri: URI): Int = when {
        uri.port != -1 -> uri.port
        uri.scheme.equals("https", ignoreCase = true) -> 443
        uri.scheme.equals("http", ignoreCase = true) -> 80
        else -> -1
    }
}
