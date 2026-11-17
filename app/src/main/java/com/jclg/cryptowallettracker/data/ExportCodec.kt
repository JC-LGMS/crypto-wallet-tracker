package com.jclg.cryptowallettracker.data

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Chiffrement AES-GCM des exports protégés par un code (PBKDF2 pour la clé). */
object ExportCodec {
    private const val PREFIX = "CWT1:"
    private const val ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12

    fun isEncrypted(text: String): Boolean = text.trimStart().startsWith(PREFIX)

    fun encrypt(plain: String, code: String): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(code, salt), GCMParameterSpec(128, iv))
        val body = salt + iv + cipher.doFinal(plain.toByteArray())
        return PREFIX + Base64.encodeToString(body, Base64.NO_WRAP)
    }

    /** @throws IllegalArgumentException si le code est faux ou le fichier abîmé. */
    fun decrypt(text: String, code: String): String {
        val body = Base64.decode(text.trim().removePrefix(PREFIX), Base64.NO_WRAP)
        require(body.size > SALT_BYTES + IV_BYTES) { "fichier protégé illisible" }
        val salt = body.copyOfRange(0, SALT_BYTES)
        val iv = body.copyOfRange(SALT_BYTES, SALT_BYTES + IV_BYTES)
        val data = body.copyOfRange(SALT_BYTES + IV_BYTES, body.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(code, salt), GCMParameterSpec(128, iv))
        return try {
            String(cipher.doFinal(data))
        } catch (e: Exception) {
            throw IllegalArgumentException("code incorrect")
        }
    }

    private fun key(code: String, salt: ByteArray): SecretKeySpec {
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(code.toCharArray(), salt, ITERATIONS, 256))
            .encoded
        return SecretKeySpec(bytes, "AES")
    }
}
