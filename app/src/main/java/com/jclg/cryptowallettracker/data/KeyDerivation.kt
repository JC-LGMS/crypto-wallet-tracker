package com.jclg.cryptowallettracker.data

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PBKDF2-SHA256 partagé par le verrou de l'application et le chiffrement des exports. */
object KeyDerivation {
    const val ITERATIONS = 120_000
    private const val BITS = 256

    fun derive(code: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(code.toCharArray(), salt, ITERATIONS, BITS))
            .encoded
}
