package com.jclg.cryptowallettracker.data

import android.content.Context
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

/**
 * Freine le bourrage du code : 5 essais libres, puis un délai qui double à chaque échec.
 * Persisté : redémarrer l'application ne remet pas le compteur à zéro.
 */
object LockAttempts {
    private const val PREFS = "lock_attempts"
    private const val KEY_FAILURES = "failures"
    private const val KEY_UNTIL = "until"
    private const val FREE_ATTEMPTS = 5
    private const val BASE_DELAY_MS = 30_000L
    private const val MAX_DELAY_MS = 15 * 60_000L

    /** Millisecondes restantes avant le prochain essai autorisé (0 = autorisé). */
    fun remainingMs(context: Context): Long =
        (prefs(context).getLong(KEY_UNTIL, 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    fun fail(context: Context) {
        val prefs = prefs(context)
        val failures = prefs.getInt(KEY_FAILURES, 0) + 1
        prefs.edit()
            .putInt(KEY_FAILURES, failures)
            .putLong(KEY_UNTIL, System.currentTimeMillis() + delayAfter(failures))
            .apply()
    }

    fun reset(context: Context) {
        prefs(context).edit().clear().apply()
    }

    /** Délai après [failures] échecs consécutifs (0 tant qu'on reste sous le seuil). */
    internal fun delayAfter(failures: Int): Long =
        if (failures <= FREE_ATTEMPTS) {
            0L
        } else {
            (BASE_DELAY_MS shl (failures - FREE_ATTEMPTS - 1).coerceAtMost(5)).coerceAtMost(MAX_DELAY_MS)
        }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
