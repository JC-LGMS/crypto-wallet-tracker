package com.jclg.cryptowallettracker.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Stockage local : un simple fichier JSON dans filesDir, écrit de façon atomique. */
class WalletStore(context: Context) {
    private val file = File(context.filesDir, "wallets.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun load(): List<Wallet> = runCatching {
        json.decodeFromString<List<Wallet>>(file.readText())
            .mapNotNull(::migrate)
    }.getOrElse {
        // Ne jamais écraser un fichier qu'on n'a pas su lire : on le garde de côté.
        runCatching { file.renameTo(File(file.parentFile, "wallets.json.bad")) }
        emptyList()
    }

    /** Les versions 1.0 stockaient un enum `chain` : on le convertit en identifiant de crypto. */
    private fun migrate(wallet: Wallet): Wallet? {
        if (wallet.coinId.isNotBlank()) return wallet
        val coin = Coins.fromLegacy(wallet.chain) ?: return null
        return wallet.copy(coinId = coin.id, source = coin.sources.firstOrNull())
    }

    fun save(wallets: List<Wallet>) {
        val tmp = File(file.parentFile, "wallets.json.tmp")
        tmp.writeText(json.encodeToString(wallets))
        if (tmp.renameTo(file)) return
        file.writeText(tmp.readText())
        tmp.delete()
    }

    fun exportJson(wallets: List<Wallet>): String =
        json.encodeToString(wallets.map { WalletPayload.from(it) })

    /** Accepte un export fichier (liste) ou un QR code (objet unique) ; rend les entrées brutes. */
    fun parseImport(text: String): List<WalletPayload> {
        val trimmed = text.trim()
        return runCatching { json.decodeFromString<List<WalletPayload>>(trimmed) }.getOrNull()
            ?: listOf(json.decodeFromString<WalletPayload>(trimmed))
    }
}
