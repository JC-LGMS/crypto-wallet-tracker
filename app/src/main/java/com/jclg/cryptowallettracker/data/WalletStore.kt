package com.jclg.cryptowallettracker.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import java.io.File

/** Stockage local : un simple fichier JSON dans filesDir, écrit de façon atomique. */
class WalletStore(context: Context, fileName: String = "wallets.json") {
    private val file = File(context.filesDir, fileName)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun exists(): Boolean = file.exists()

    fun load(): List<Wallet> {
        val text = runCatching { file.readText() }.getOrElse { return emptyList() }
        val elements = runCatching { json.parseToJsonElement(text).jsonArray }.getOrElse {
            // Fichier entièrement illisible : on le garde de côté, on n'écrase rien.
            runCatching { file.renameTo(File(file.parentFile, "${file.name}.bad")) }
            return emptyList()
        }
        // Une seule entrée abîmée ne doit pas effacer les autres.
        return elements
            .mapNotNull { runCatching { json.decodeFromJsonElement<Wallet>(it) }.getOrNull() }
            .mapNotNull(::migrate)
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
    fun parseImport(text: String): List<WalletPayload> = WalletCodec.decodeAll(text)
}
