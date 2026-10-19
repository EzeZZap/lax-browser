package com.javzzap.laxbrowser.domain

import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Saved logins inside a backup file, encrypted with a password the user chooses: PBKDF2-SHA256
 * derives an AES-256-GCM key, so the backup can be restored on another device (the logins file
 * itself is tied to this device's keystore). Only the number of logins is readable without it.
 */
object LoginBackup {
    const val ITERATIONS = 310_000
    const val MIN_PASSWORD_LENGTH = 8
    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val MIN_ITERATIONS = 100_000
    private const val MAX_ITERATIONS = 5_000_000
    private val AAD = "lax-logins-1".toByteArray()

    class WrongPasswordException : Exception("Wrong password")

    fun encrypt(data: LoginData, password: CharArray, random: SecureRandom = SecureRandom(), iterations: Int = ITERATIONS): JSONObject {
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(128, iv))
            updateAAD(AAD)
        }
        val encrypted = cipher.doFinal(Logins.encode(data).toByteArray(Charsets.UTF_8))
        val base64 = Base64.getEncoder()
        return JSONObject()
            .put("kdf", KDF)
            .put("iterations", iterations)
            .put("salt", base64.encodeToString(salt))
            .put("iv", base64.encodeToString(iv))
            .put("data", base64.encodeToString(encrypted))
            .put("count", data.logins.size)
    }

    /** The number of logins stored in [json], shown before asking for the password. */
    fun count(json: JSONObject): Int = json.optInt("count", -1)

    /**
     * Decrypts logins from a backup. Throws [WrongPasswordException] for a wrong password and
     * [Backup.InvalidBackupException] for a damaged or unsupported section.
     */
    fun decrypt(json: JSONObject, password: CharArray): LoginData {
        val iterations = json.optInt("iterations")
        if (json.optString("kdf") != KDF || iterations !in MIN_ITERATIONS..MAX_ITERATIONS) {
            throw Backup.InvalidBackupException("Unsupported login encryption")
        }
        val decoder = Base64.getDecoder()
        val (salt, iv, encrypted) = runCatching {
            Triple(decoder.decode(json.getString("salt")), decoder.decode(json.getString("iv")), decoder.decode(json.getString("data")))
        }.getOrElse { throw Backup.InvalidBackupException("Damaged login data") }
        val plain = try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(128, iv))
                updateAAD(AAD)
                doFinal(encrypted)
            }
        } catch (_: AEADBadTagException) {
            throw WrongPasswordException()
        } catch (e: Exception) {
            throw Backup.InvalidBackupException("Damaged login data: ${e.javaClass.simpleName}")
        }
        return runCatching { Logins.decode(String(plain, Charsets.UTF_8)) }.getOrElse { throw Backup.InvalidBackupException("Damaged login data") }
    }

    private fun key(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
