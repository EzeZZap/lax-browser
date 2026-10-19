package com.javzzap.laxbrowser.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.javzzap.laxbrowser.domain.ImportedLogin
import com.javzzap.laxbrowser.domain.LoginImportSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Saved logins with the real Android Keystore: encrypted on disk, readable by a new store. */
@RunWith(AndroidJUnit4::class)
class LoginStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob())
    private val fileName = "logins-test-${UUID.randomUUID()}.enc"

    private fun store() = LoginStore(context, scope, fileName)

    @After
    fun tearDown() {
        scope.cancel()
        context.filesDir.listFiles { file -> file.name.startsWith(fileName) }?.forEach(File::delete)
    }

    @Test
    fun loginsAreEncryptedOnDiskAndReadByANewStore() = runBlocking {
        store().save(null, "https://example.com", "https://example.com", null, "ana@example.com", "hunter2-secret")

        val bytes = File(context.filesDir, fileName).readBytes()
        val raw = String(bytes, Charsets.ISO_8859_1)
        assertFalse("the password must not be stored in plain text", raw.contains("hunter2-secret"))
        assertFalse(raw.contains("ana@example.com"))

        val login = store().all().single()
        assertEquals("https://example.com", login.origin)
        assertEquals("ana@example.com", login.username)
        assertEquals("hunter2-secret", login.password)
    }

    @Test
    fun importKeepsSavedPasswordsAndPersists() = runBlocking {
        val first = store()
        first.save(null, "https://a.example", null, null, "ana", "saved")
        val summary = first.import(
            listOf(
                ImportedLogin("https://a.example", null, null, "ana", "from file"),
                ImportedLogin("https://b.example", null, null, "bob", "new"),
            ),
            neverSave = setOf("https://never.example"),
        )
        assertEquals(LoginImportSummary(imported = 1, skipped = 1), summary)

        val reloaded = store().load()
        assertEquals("saved", reloaded.logins.first { it.username == "ana" }.password)
        assertEquals("new", reloaded.logins.first { it.username == "bob" }.password)
        assertTrue(reloaded.neverSave.contains("https://never.example"))
    }

    @Test
    fun anUnreadableFileIsSetAsideInsteadOfOverwritten() = runBlocking {
        File(context.filesDir, fileName).writeBytes(ByteArray(64) { it.toByte() })

        assertTrue(store().all().isEmpty())
        val setAside = context.filesDir.listFiles { file -> file.name.startsWith("$fileName.unreadable-") }.orEmpty()
        assertEquals(1, setAside.size)
        assertEquals(64L, setAside.single().length())
    }
}
