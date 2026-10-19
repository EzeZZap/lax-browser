package com.javzzap.laxbrowser.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlInputTest {
    private fun search(q: String) = UrlInput.searchUrl(q)

    @Test
    fun `only addresses typed without a scheme may fall back to http`() {
        assertEquals("https://neverssl.com", UrlInput.upgradedAddress("neverssl.com"))
        assertEquals("https://192.168.1.1:8080", UrlInput.upgradedAddress(" 192.168.1.1:8080 "))
        assertNull(UrlInput.upgradedAddress("https://example.com"))
        assertNull(UrlInput.upgradedAddress("http://example.com"))
        assertNull(UrlInput.upgradedAddress("how to bake bread"))
        assertEquals("http://neverssl.com/page?a=1", UrlInput.httpFallbackFor("https://neverssl.com/page?a=1"))
        assertNull(UrlInput.httpFallbackFor("http://neverssl.com"))
    }

    @Test
    fun `only failed secure connections retry over http`() {
        val kinds = com.javzzap.laxbrowser.engine.gecko.LoadErrorKind
        assertTrue(kinds.allowsHttpFallback(org.mozilla.geckoview.WebRequestError.ERROR_CONNECTION_REFUSED))
        assertTrue(kinds.allowsHttpFallback(org.mozilla.geckoview.WebRequestError.ERROR_SECURITY_BAD_CERT))
        assertFalse(kinds.allowsHttpFallback(org.mozilla.geckoview.WebRequestError.ERROR_UNKNOWN_HOST))
        assertFalse(kinds.allowsHttpFallback(org.mozilla.geckoview.WebRequestError.ERROR_HTTPS_ONLY))
    }

    @Test
    fun `blank input yields nothing`() {
        assertNull(UrlInput.normalize("   "))
    }

    @Test
    fun `full urls are kept`() {
        assertEquals("https://example.com/a?b=c#d", UrlInput.normalize("https://example.com/a?b=c#d"))
        assertEquals("http://example.com", UrlInput.normalize("  http://example.com  "))
        assertEquals("https://example.com", UrlInput.normalize("HTTPS://example.com"))
        assertEquals("about:config", UrlInput.normalize("about:config"))
        assertEquals("mailto:someone@example.com", UrlInput.normalize("mailto:someone@example.com"))
    }

    @Test
    fun `hosts get https`() {
        assertEquals("https://example.com", UrlInput.normalize("example.com"))
        assertEquals("https://example.com/path?q=1", UrlInput.normalize("example.com/path?q=1"))
        assertEquals("https://localhost:8080", UrlInput.normalize("localhost:8080"))
        assertEquals("https://192.168.0.1", UrlInput.normalize("192.168.0.1"))
        assertEquals("https://[::1]:8080/x", UrlInput.normalize("[::1]:8080/x"))
        assertEquals("https://sub.example.co.uk:8443/", UrlInput.normalize("sub.example.co.uk:8443/"))
    }

    @Test
    fun `internationalized domains are treated as hosts`() {
        assertEquals("https://bücher.de", UrlInput.normalize("bücher.de"))
        assertEquals("https://пример.рф/путь", UrlInput.normalize("пример.рф/путь"))
        assertEquals("https://xn--bcher-kva.de", UrlInput.normalize("xn--bcher-kva.de"))
    }

    @Test
    fun `queries become searches`() {
        assertEquals(search("hello world"), UrlInput.normalize("hello world"))
        assertEquals(search("kotlin"), UrlInput.normalize("kotlin"))
        assertEquals(search("1.5"), UrlInput.normalize("1.5"))
        assertEquals(search("what is example.com"), UrlInput.normalize("what is example.com"))
        assertEquals(search("javascript:alert(1)"), UrlInput.normalize("javascript:alert(1)"))
        assertEquals(search("file:///etc/passwd"), UrlInput.normalize("file:///etc/passwd"))
    }

    @Test
    fun `search queries are encoded`() {
        assertEquals("https://duckduckgo.com/?q=a+%26+b", UrlInput.normalize("a & b"))
    }

    @Test
    fun `web url validation and normalization for bookmarks`() {
        assertEquals("https://example.com", UrlInput.normalizeWebUrl("example.com"))
        assertNull(UrlInput.normalizeWebUrl("not a url"))
        assertNull(UrlInput.normalizeWebUrl("about:blank"))
        assertTrue(UrlInput.isWebUrl("https://example.com"))
        assertFalse(UrlInput.isWebUrl("https://"))
        assertFalse(UrlInput.isWebUrl("ftp://example.com"))
    }

    @Test
    fun `host and origin extraction`() {
        assertEquals("example.com", UrlInput.hostOf("https://user@Example.com:8080/a"))
        assertEquals("https://example.com:8080", UrlInput.originOf("https://example.com:8080/a?b"))
        assertEquals("example.com", UrlInput.displayHost("https://www.example.com/"))
    }
}
