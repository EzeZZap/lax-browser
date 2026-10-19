package com.javzzap.laxbrowser.pwa

import com.javzzap.laxbrowser.domain.IconSize
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebAppManifestTest {
    private val page = "https://app.example/index.html"

    private fun manifest(json: String) = WebAppManifestParser.parse(JSONObject(json))

    private val valid = """
        {
          "name": "Example App",
          "short_name": "Example",
          "start_url": "https://app.example/start",
          "scope": "https://app.example/",
          "display": "standalone",
          "theme_color": "#FF112233",
          "icons": [
            {"src": "https://app.example/icon-96.png", "sizes": "96x96"},
            {"src": "https://app.example/icon-512.png", "sizes": ["512x512"], "purpose": "any maskable"},
            {"src": "https://app.example/icon-192.png", "sizes": "192x192", "type": "image/png"},
            {"src": "https://app.example/icon.svg", "sizes": "any", "type": "image/svg+xml"}
          ]
        }
    """.trimIndent()

    @Test
    fun `parses gecko manifest json`() {
        val m = manifest(valid)!!
        assertEquals("Example App", m.displayName)
        assertEquals("https://app.example/", m.scope)
        assertEquals(0xFF112233.toInt(), m.themeColor)
        assertEquals(4, m.icons.size)
        assertEquals(setOf("any", "maskable"), m.icons[1].purposes)
        assertEquals(setOf("any"), m.icons[0].purposes)
    }

    @Test
    fun `malformed manifests are rejected or sanitized`() {
        assertNull(manifest("""{"name": "No start"}"""))
        val m = manifest("""{"start_url": "https://a.example/app/page", "icons": [{"sizes": "x"}, "junk", {"src": "https://a.example/i.png", "sizes": "big"}]}""")!!
        assertEquals("https://a.example/app/", m.scope)
        assertEquals("browser", m.display)
        assertEquals(1, m.icons.size)
        assertTrue(m.icons.single().sizes.isEmpty())
    }

    @Test
    fun `icon sizes accept strings and arrays`() {
        assertEquals(listOf(IconSize(48, 48), IconSize(96, 96)), WebAppManifestParser.parseIconSizes("48x48 96x96"))
        assertEquals(listOf(IconSize(0, 0, isAny = true)), WebAppManifestParser.parseIconSizes("any"))
        assertTrue(WebAppManifestParser.parseIconSizes("0x0 -1x5 axb").isEmpty())
    }

    @Test
    fun `valid manifest on a secure page is eligible with the best raster icon`() {
        val result = WebAppEligibility.check(manifest(valid), page, isSecure = true)
        assertTrue(result is Eligibility.Eligible)
        assertEquals("https://app.example/icon-512.png", (result as Eligibility.Eligible).icon.src)
    }

    @Test
    fun `a manifest link alone is not enough`() {
        assertEquals(Eligibility.Ineligible(Ineligibility.NO_MANIFEST), WebAppEligibility.check(null, page, true))
    }

    @Test
    fun `insecure pages are not eligible`() {
        assertEquals(Eligibility.Ineligible(Ineligibility.INSECURE), WebAppEligibility.check(manifest(valid), page, isSecure = false))
        assertEquals(
            Eligibility.Ineligible(Ineligibility.INSECURE),
            WebAppEligibility.check(manifest(valid), "http://app.example/", isSecure = false),
        )
    }

    @Test
    fun `browser display mode and missing icons are not eligible`() {
        val browser = manifest(valid.replace("\"standalone\"", "\"browser\""))
        assertEquals(Eligibility.Ineligible(Ineligibility.DISPLAY_MODE), WebAppEligibility.check(browser, page, true))

        val smallIcons = manifest(
            """{"name": "A", "start_url": "https://app.example/", "display": "standalone",
               "icons": [{"src": "https://app.example/i.png", "sizes": "96x96"}, {"src": "https://app.example/m.png", "sizes": "512x512", "purpose": "maskable"}]}""",
        )
        assertEquals(Eligibility.Ineligible(Ineligibility.NO_ICON), WebAppEligibility.check(smallIcons, page, true))
    }

    @Test
    fun `nameless and cross origin manifests are not eligible`() {
        val nameless = manifest(valid.replace("\"name\": \"Example App\",", "").replace("\"short_name\": \"Example\",", ""))
        assertEquals(Eligibility.Ineligible(Ineligibility.NO_NAME), WebAppEligibility.check(nameless, page, true))

        val otherOrigin = manifest(valid.replace("https://app.example/start", "https://evil.example/start"))
        assertEquals(Eligibility.Ineligible(Ineligibility.CROSS_ORIGIN), WebAppEligibility.check(otherOrigin, page, true))
    }

    @Test
    fun `scope matching uses origin and path prefix`() {
        assertTrue(WebAppEligibility.isInScope("https://app.example/a/b", "https://app.example/a/"))
        assertTrue(WebAppEligibility.isInScope("https://app.example:443/a/", "https://app.example/a/"))
        assertFalse(WebAppEligibility.isInScope("https://app.example/b", "https://app.example/a/"))
        assertFalse(WebAppEligibility.isInScope("https://other.example/a/", "https://app.example/a/"))
        assertFalse(WebAppEligibility.isInScope("http://app.example/a/", "https://app.example/a/"))
        assertFalse(WebAppEligibility.isInScope("not a url", "https://app.example/"))
    }
}
