package com.javzzap.laxbrowser.domain

import com.javzzap.laxbrowser.engine.gecko.LoadErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.geckoview.WebRequestError

class LoginsAndRecoveryTest {
    private var nextId = 0
    private fun id() = "g${nextId++}"

    private fun login(origin: String, username: String) = SavedLogin(id(), origin, null, null, username, "secret", 1, 1)

    @Test
    fun `logins match their domain and subdomains only`() {
        val logins = listOf(login("https://example.com", "a"), login("https://accounts.example.com", "b"), login("https://notexample.com", "c"))
        assertEquals(listOf("a", "b"), Logins.forDomain(logins, "example.com").map { it.username })
        assertEquals(listOf("b"), Logins.forDomain(logins, "accounts.example.com").map { it.username })
        assertTrue(Logins.forDomain(logins, "").isEmpty())
    }

    @Test
    fun `saving updates the same login and adds new ones`() {
        var logins = Logins.save(emptyList(), null, "https://example.com", "https://example.com", null, "me", "one", 10, ::id)
        assertEquals(1, logins.size)
        val guid = logins.single().guid
        logins = Logins.save(logins, null, "https://example.com", null, null, "me", "two", 20, ::id)
        assertEquals("the same origin and username changes the password", listOf("two"), logins.map { it.password })
        assertEquals(guid, logins.single().guid)
        assertEquals("https://example.com", logins.single().formActionOrigin)
        logins = Logins.save(logins, guid, "https://example.com", null, null, "renamed", "three", 30, ::id)
        assertEquals(listOf("renamed"), logins.map { it.username })
        logins = Logins.save(logins, null, "https://example.com", null, null, "other", "four", 40, ::id)
        assertEquals(2, logins.size)
        logins = Logins.used(logins, guid, 50)
        assertEquals(1, logins.first { it.guid == guid }.timesUsed)
    }

    @Test
    fun `login data round trips and never-save keys are origins`() {
        val data = LoginData(listOf(login("https://example.com", "me")), setOf("https://news.example"))
        assertEquals(data, Logins.decode(Logins.encode(data)))
        assertEquals("https://example.com", Logins.neverSaveKey("https://Example.com/login?x=1"))
        assertNull(Logins.neverSaveKey("about:blank"))
        assertTrue(runCatching { Logins.decode("""{"format":99}""") }.isFailure)
    }

    @Test
    fun `recently closed tabs survive encoding and junk is skipped`() {
        val tab = Tab("t1", "s1", "c1", "https://a.example/page", "A", null, 3, TabKind.WEB, isPinned = true, isEssential = false, createdAt = 1, lastAccessedAt = 2)
        val closed = listOf(ClosedTab(tab, "{\"history\":[]}"))
        assertEquals(closed, RecentlyClosedCodec.decode(RecentlyClosedCodec.encode(closed)))
        val privateTab = tab.copy(id = "t2", containerId = PRIVATE_CONTEXT_ID)
        val badUrl = tab.copy(id = "t3", url = "javascript:alert(1)")
        assertEquals(listOf("t1"), RecentlyClosedCodec.decode(RecentlyClosedCodec.encode(closed + ClosedTab(privateTab, null) + ClosedTab(badUrl, null))).map { it.tab.id })
        assertTrue(RecentlyClosedCodec.decode("not json").isEmpty())
    }

    @Test
    fun `load errors map to friendly kinds`() {
        assertEquals(LoadErrorKind.NOT_FOUND, LoadErrorKind.of(WebRequestError.ERROR_UNKNOWN_HOST, WebRequestError.ERROR_CATEGORY_URI))
        assertEquals(LoadErrorKind.OFFLINE, LoadErrorKind.of(WebRequestError.ERROR_OFFLINE, WebRequestError.ERROR_CATEGORY_NETWORK))
        assertEquals(LoadErrorKind.INSECURE, LoadErrorKind.of(WebRequestError.ERROR_SECURITY_BAD_CERT, WebRequestError.ERROR_CATEGORY_SECURITY))
        assertEquals(LoadErrorKind.HARMFUL, LoadErrorKind.of(WebRequestError.ERROR_SAFEBROWSING_PHISHING_URI, WebRequestError.ERROR_CATEGORY_SAFEBROWSING))
        assertEquals(LoadErrorKind.CONNECTION, LoadErrorKind.of(-1, WebRequestError.ERROR_CATEGORY_NETWORK))
        assertFalse("certificate errors offer no retry", LoadErrorKind.INSECURE.canRetry)
    }
}
