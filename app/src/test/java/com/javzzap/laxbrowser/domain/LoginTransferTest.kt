package com.javzzap.laxbrowser.domain

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.SecureRandom
import java.util.Random

class LoginTransferTest {
    @Test
    fun `firefox export is read with realms, form origins, and times`() {
        val csv = "\"url\",\"username\",\"password\",\"httpRealm\",\"formActionOrigin\",\"guid\",\"timeCreated\",\"timeLastUsed\",\"timePasswordChanged\"\r\n" +
            "\"https://accounts.example.com\",\"ana@example.com\",\"p,a\"\"ss\",\"\",\"https://accounts.example.com\",\"{1}\",\"1000\",\"2000\",\"1000\"\r\n" +
            "\"https://router.local\",\"admin\",\"secret\",\"Router\",\"\",\"{2}\",\"3000\",\"3000\",\"3000\"\r\n" +
            "\"chrome://FirefoxAccounts\",\"\",\"token\",\"Firefox Accounts credentials\",\"\",\"{3}\",\"1\",\"1\",\"1\"\r\n"
        val result = LoginCsv.parse(csv)
        assertEquals(LoginCsvFormat.FIREFOX, result.format)
        assertEquals(1, result.skipped)
        assertEquals(2, result.logins.size)
        val first = result.logins[0]
        assertEquals("https://accounts.example.com", first.origin)
        assertEquals("ana@example.com", first.username)
        assertEquals("p,a\"ss", first.password)
        assertEquals("https://accounts.example.com", first.formActionOrigin)
        assertNull(first.httpRealm)
        assertEquals(1000L, first.createdAt)
        assertEquals(2000L, first.lastUsedAt)
        assertEquals("Router", result.logins[1].httpRealm)
    }

    @Test
    fun `chrome export keeps passwords exactly and skips app logins`() {
        val csv = "name,url,username,password,note\n" +
            "example.com,https://www.example.com/login?next=1,bob, spaces kept ,\n" +
            "App,android://hash@com.example.app/,bob,pw,\n" +
            "\"multi\nline\",https://shop.example:8443/cart,carol,\"line1\nline2\",note\n"
        val result = LoginCsv.parse(csv)
        assertEquals(LoginCsvFormat.CHROMIUM, result.format)
        assertEquals(1, result.skipped)
        assertEquals(listOf("https://www.example.com", "https://shop.example:8443"), result.logins.map { it.origin })
        assertEquals(" spaces kept ", result.logins[0].password)
        assertEquals("line1\nline2", result.logins[1].password)
    }

    @Test
    fun `bitwarden export uses the first address and only logins`() {
        val csv = "\uFEFFfolder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp\n" +
            ",,login,Mail,,,0,\"https://mail.example,https://alt.example\",me,pw1,\n" +
            ",,note,Secret note,text,,0,,,,\n" +
            ",,login,No scheme,,,0,forum.example,me,pw2,\n"
        val result = LoginCsv.parse(csv)
        assertEquals(LoginCsvFormat.BITWARDEN, result.format)
        assertEquals(listOf("https://mail.example", "https://forum.example"), result.logins.map { it.origin })
        assertEquals(1, result.skipped)
    }

    @Test
    fun `files without address and password columns are not logins`() {
        assertNull(LoginCsv.parse("title,url\nA,https://a.example\n").format)
        assertTrue(LoginCsv.parse("").logins.isEmpty())
    }

    @Test
    fun `export round-trips through the importer`() {
        val logins = listOf(
            SavedLogin("g1", "https://b.example", null, null, "x\"y", "pa,ss\nword", 5, 6),
            SavedLogin("g2", "https://a.example", "https://a.example", "Realm", "", "pw", 7, 8),
        )
        val parsed = LoginCsv.parse(LoginCsv.export(logins))
        assertEquals(LoginCsvFormat.FIREFOX, parsed.format)
        assertEquals(listOf("https://a.example", "https://b.example"), parsed.logins.map { it.origin })
        assertEquals("pa,ss\nword", parsed.logins[1].password)
        assertEquals("x\"y", parsed.logins[1].username)
        assertEquals("Realm", parsed.logins[0].httpRealm)
        assertEquals(7L, parsed.logins[0].createdAt)
    }

    @Test
    fun `merging skips logins that are already saved`() {
        val existing = listOf(SavedLogin("g1", "https://a.example", null, null, "ana", "old", 1, 1))
        val incoming = listOf(
            ImportedLogin("https://a.example", null, null, "ana", "new"),
            ImportedLogin("https://a.example", null, null, "bob", "pw"),
            ImportedLogin("https://a.example", null, null, "bob", "duplicate in file"),
            ImportedLogin("https://a.example", null, "Realm", "ana", "http auth"),
        )
        var next = 0
        val (merged, summary) = Logins.merge(existing, incoming, now = 50) { "new${next++}" }
        assertEquals(LoginImportSummary(imported = 2, skipped = 2), summary)
        assertEquals("old", merged.first { it.guid == "g1" }.password)
        assertEquals(listOf("g1", "new0", "new1"), merged.map { it.guid })
        assertEquals(50L, merged[1].createdAt)
    }

    @Test
    fun `backup logins decrypt only with the right password`() {
        val data = LoginData(listOf(SavedLogin("g1", "https://a.example", null, null, "ana", "pw", 1, 2, 3)), setOf("https://never.example"))
        val section = LoginBackup.encrypt(data, "correct horse".toCharArray(), SecureRandom(), iterations = 100_000)
        assertEquals(1, LoginBackup.count(section))
        assertFalse(section.has("logins"))
        assertEquals(data, LoginBackup.decrypt(JSONObject(section.toString()), "correct horse".toCharArray()))
        try {
            LoginBackup.decrypt(section, "wrong horse".toCharArray())
            fail("A wrong password must not decrypt")
        } catch (_: LoginBackup.WrongPasswordException) {
        }
    }

    @Test
    fun `damaged or tampered backup logins are rejected`() {
        val section = LoginBackup.encrypt(LoginData(), "password1".toCharArray(), SecureRandom(), iterations = 100_000)
        val weak = JSONObject(section.toString()).put("iterations", 10)
        assertRejected { LoginBackup.decrypt(weak, "password1".toCharArray()) }
        val broken = JSONObject(section.toString()).put("salt", "not base64!")
        assertRejected { LoginBackup.decrypt(broken, "password1".toCharArray()) }
    }

    @Test
    fun `backups carry the encrypted logins section`() {
        val data = BrowserData(spaces = listOf(Space("s", "Home", "c", 0, 0, null)))
        val text = Backup.encode(data, "1.0", 0, JSONObject().put("count", 2))
        assertEquals(2, Backup.encryptedLogins(text)?.let(LoginBackup::count))
        assertNull(Backup.encryptedLogins(Backup.encode(data, "1.0", 0)))
    }

    @Test
    fun `generated passwords have the requested length and characters`() {
        val random = Random(42)
        repeat(200) {
            val password = PasswordGenerator.generate(length = 12, random = random)
            assertEquals(12, password.length)
            assertTrue(password.any { it.isLowerCase() })
            assertTrue(password.any { it.isUpperCase() })
            assertTrue(password.any { it.isDigit() })
            assertTrue(password.any { !it.isLetterOrDigit() })
            assertTrue(password.none { it in "lIO01" })
        }
        val lettersOnly = PasswordGenerator.generate(length = 100, digits = false, symbols = false, random = random)
        assertEquals(PasswordGenerator.MAX_LENGTH, lettersOnly.length)
        assertTrue(lettersOnly.all { it.isLetter() })
        assertEquals(PasswordGenerator.MIN_LENGTH, PasswordGenerator.generate(length = 2, random = random).length)
    }

    private fun assertRejected(block: () -> Unit) {
        try {
            block()
            fail("Expected the section to be rejected")
        } catch (_: Backup.InvalidBackupException) {
        }
    }
}
