package com.jclg.cryptowallettracker.data

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Code de verrouillage propre à l'application : jamais stocké en clair. */
object LockCode {
    fun hash(code: String): Pair<String, String> {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return encode(salt) to encode(KeyDerivation.derive(code, salt))
    }

    fun verify(code: String, salt: String, hash: String): Boolean = runCatching {
        MessageDigest.isEqual(
            KeyDerivation.derive(code, Base64.getDecoder().decode(salt)),
            Base64.getDecoder().decode(hash),
        )
    }.getOrDefault(false)

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
}
