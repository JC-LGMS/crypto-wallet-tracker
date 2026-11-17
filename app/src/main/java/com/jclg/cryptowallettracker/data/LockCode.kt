package com.jclg.cryptowallettracker.data

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Code de verrouillage propre à l'application : jamais stocké en clair. */
object LockCode {
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256

    fun hash(code: String): Pair<String, String> {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(salt, Base64.NO_WRAP) to
            Base64.encodeToString(derive(code, salt), Base64.NO_WRAP)
    }

    fun verify(code: String, salt: String, hash: String): Boolean = runCatching {
        MessageDigest.isEqual(
            derive(code, Base64.decode(salt, Base64.NO_WRAP)),
            Base64.decode(hash, Base64.NO_WRAP),
        )
    }.getOrDefault(false)

    private fun derive(code: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(code.toCharArray(), salt, ITERATIONS, KEY_BITS))
            .encoded
}
