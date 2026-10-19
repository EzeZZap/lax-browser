package com.javzzap.laxbrowser.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import com.javzzap.laxbrowser.domain.ImportedLogin
import com.javzzap.laxbrowser.domain.LoginData
import com.javzzap.laxbrowser.domain.LoginImportSummary
import com.javzzap.laxbrowser.domain.Logins
import com.javzzap.laxbrowser.domain.SavedLogin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Saved logins, encrypted with an AES-GCM key kept in the Android Keystore (it never leaves the
 * device, so the file is useless if copied elsewhere; Android backups do not include it). The
 * data is a small JSON document kept in memory once loaded. Never logs usernames or passwords.
 */
class LoginStore(context: Context, private val scope: CoroutineScope, fileName: String = FILE_NAME) {
    private val file = File(context.filesDir, fileName)
    private val mutex = Mutex()
    private val mutableData = MutableStateFlow<LoginData?>(null)

    /** Null until loaded. */
    val data: StateFlow<LoginData?> = mutableData.asStateFlow()

    /** Loads (once) and returns the current data. */
    suspend fun load(): LoginData = mutex.withLock { loadLocked() }

    private suspend fun loadLocked(): LoginData {
        mutableData.value?.let { return it }
        val loaded = withContext(Dispatchers.IO) { read() }
        mutableData.value = loaded
        return loaded
    }

    suspend fun forDomain(domain: String): List<SavedLogin> = Logins.forDomain(load().logins, domain)

    suspend fun all(): List<SavedLogin> = load().logins

    suspend fun save(guid: String?, origin: String, formActionOrigin: String?, httpRealm: String?, username: String, password: String) =
        update { it.copy(logins = Logins.save(it.logins, guid, origin, formActionOrigin, httpRealm, username, password, System.currentTimeMillis()) { UUID.randomUUID().toString() }) }

    fun markUsed(guid: String?) {
        scope.launch { update { it.copy(logins = Logins.used(it.logins, guid, System.currentTimeMillis())) } }
    }

    /** Adds logins from a password file or backup; ones already saved are kept as they are. */
    suspend fun import(logins: List<ImportedLogin>, neverSave: Set<String> = emptySet()): LoginImportSummary {
        var summary = LoginImportSummary(0, 0)
        update { data ->
            val (merged, result) = Logins.merge(data.logins, logins, System.currentTimeMillis()) { UUID.randomUUID().toString() }
            summary = result
            data.copy(logins = merged, neverSave = data.neverSave + neverSave)
        }
        return summary
    }

    suspend fun delete(guid: String) = update { data -> data.copy(logins = data.logins.filter { it.guid != guid }) }

    suspend fun deleteAll() = update { it.copy(logins = emptyList()) }

    suspend fun isNeverSave(origin: String): Boolean = Logins.neverSaveKey(origin)?.let { it in load().neverSave } == true

    suspend fun neverSave(origin: String) {
        val key = Logins.neverSaveKey(origin) ?: return
        update { it.copy(neverSave = it.neverSave + key) }
    }

    suspend fun allowSaving(key: String) = update { it.copy(neverSave = it.neverSave - key) }

    private suspend fun update(transform: (LoginData) -> LoginData) {
        mutex.withLock {
            val updated = transform(loadLocked())
            mutableData.value = updated
            withContext(Dispatchers.IO) { write(updated) }
        }
    }

    private fun read(): LoginData {
        if (!file.exists()) return LoginData()
        return try {
            val bytes = file.readBytes()
            val iv = bytes.copyOfRange(0, IV_LENGTH)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv)) }
            Logins.decode(String(cipher.doFinal(bytes, IV_LENGTH, bytes.size - IV_LENGTH), Charsets.UTF_8))
        } catch (e: Exception) {
            // Unreadable (for example the key was reset): keep the file aside rather than overwrite it.
            Log.w(TAG, "Could not read saved logins (${e.javaClass.simpleName})")
            runCatching { file.renameTo(File(file.parentFile, "${file.name}.unreadable-${System.currentTimeMillis()}")) }
            LoginData()
        }
    }

    private fun write(data: LoginData) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(Logins.encode(data).toByteArray(Charsets.UTF_8))
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.outputStream().use { out ->
            out.write(cipher.iv)
            out.write(encrypted)
        }
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val TAG = "LoginStore"
        private const val FILE_NAME = "logins.enc"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "lax_logins"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val TAG_BITS = 128
    }
}
