package com.javzzap.laxbrowser.platform

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.javzzap.laxbrowser.domain.Nextcloud
import com.javzzap.laxbrowser.domain.NextcloudAccount
import com.javzzap.laxbrowser.domain.NextcloudConfig
import com.javzzap.laxbrowser.domain.NextcloudLoginFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The Nextcloud account and backup settings, encrypted with an AES-GCM key kept in the Android
 * Keystore (files/nextcloud.enc), like saved logins. The app password is never logged.
 */
class NextcloudStore(context: Context) {
    private val file = File(context.filesDir, "nextcloud.enc")

    suspend fun load(): NextcloudConfig = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext NextcloudConfig()
        runCatching {
            val bytes = file.readBytes()
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_LENGTH)) }
            Nextcloud.decode(String(cipher.doFinal(bytes, IV_LENGTH, bytes.size - IV_LENGTH), Charsets.UTF_8))
        }.getOrElse {
            Log.w(TAG, "Could not read the Nextcloud settings (${it.javaClass.simpleName})")
            NextcloudConfig()
        }
    }

    suspend fun save(config: NextcloudConfig) = withContext(Dispatchers.IO) {
        if (config.account == null && !config.autoBackup && config.loginsPassword == null) {
            file.delete()
            return@withContext
        }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(Nextcloud.encode(config).toByteArray(Charsets.UTF_8))
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.outputStream().use {
            it.write(cipher.iv)
            it.write(encrypted)
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

    private companion object {
        const val TAG = "NextcloudStore"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "lax_nextcloud"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
    }
}

/**
 * Talks to a Nextcloud server over HTTPS: Login Flow v2 for connecting, and a plain WebDAV
 * upload and download of one backup file in the user's files. Nothing else is sent.
 */
class NextcloudClient {
    class HttpException(val code: Int) : Exception("HTTP $code")

    suspend fun startLogin(server: String): NextcloudLoginFlow = withContext(Dispatchers.IO) {
        val body = request("POST", Nextcloud.loginFlowUrl(server), body = ByteArray(0)).second
        Nextcloud.parseLoginFlow(body) ?: throw HttpException(0)
    }

    /** The approved account, or null while the user has not finished logging in. */
    suspend fun pollLogin(flow: NextcloudLoginFlow): NextcloudAccount? = withContext(Dispatchers.IO) {
        val (code, body) = request("POST", flow.pollEndpoint, body = "token=${flow.token}".toByteArray(), contentType = "application/x-www-form-urlencoded", allow = setOf(404))
        if (code == 404) null else Nextcloud.parseLoginResult(body) ?: throw HttpException(code)
    }

    /** Checks an account typed by hand; throws for a wrong password or server. */
    suspend fun verify(account: NextcloudAccount) {
        withContext(Dispatchers.IO) { request("GET", Nextcloud.userUrl(account.server), account = account, ocs = true) }
    }

    suspend fun upload(account: NextcloudAccount, text: String) {
        withContext(Dispatchers.IO) {
            request("PUT", Nextcloud.backupUrl(account), account = account, body = text.toByteArray(Charsets.UTF_8), contentType = "application/json")
        }
    }

    /** The backup's text, or null when there is no backup yet. */
    suspend fun download(account: NextcloudAccount): String? = withContext(Dispatchers.IO) {
        val (code, body) = request("GET", Nextcloud.backupUrl(account), account = account, allow = setOf(404))
        body.takeIf { code != 404 }
    }

    private fun request(
        method: String,
        url: String,
        account: NextcloudAccount? = null,
        body: ByteArray? = null,
        contentType: String? = null,
        ocs: Boolean = false,
        allow: Set<Int> = emptySet(),
    ): Pair<Int, String> {
        require(url.startsWith("https://")) { "Nextcloud requests must use HTTPS" }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = false
            // Shown in Nextcloud's list of connected devices.
            setRequestProperty("User-Agent", "Lax (Android)")
            account?.let {
                val token = Base64.encodeToString("${it.user}:${it.appPassword}".toByteArray(), Base64.NO_WRAP)
                setRequestProperty("Authorization", "Basic $token")
            }
            if (ocs) setRequestProperty("OCS-APIRequest", "true")
            contentType?.let { setRequestProperty("Content-Type", it) }
            if (body != null) {
                doOutput = true
                setFixedLengthStreamingMode(body.size)
            }
        }
        try {
            body?.let { bytes -> connection.outputStream.use { it.write(bytes) } }
            val code = connection.responseCode
            if (code !in 200..299 && code !in allow) throw HttpException(code)
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { input -> input.readBytes().takeIf { it.size <= MAX_BYTES }?.toString(Charsets.UTF_8) ?: throw HttpException(code) }.orEmpty()
            return code to text
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 30_000
        const val MAX_BYTES = 50 * 1024 * 1024
    }
}
