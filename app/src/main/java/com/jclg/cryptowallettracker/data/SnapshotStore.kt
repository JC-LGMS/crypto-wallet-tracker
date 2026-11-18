package com.jclg.cryptowallettracker.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class BalanceEntry(val amount: Double, val updatedAt: Long)

/** Dernier état connu, conservé sur disque pour l'affichage hors ligne et la synchro en arrière-plan. */
@Serializable
data class BalanceSnapshot(
    val balances: Map<String, BalanceEntry> = emptyMap(),
    /** Derniers cours connus, pour afficher un total hors ligne. */
    val markets: Map<String, Market> = emptyMap(),
    val rates: Rates = Rates(emptyMap()),
    val quotesUpdatedAt: Long? = null,
)

class SnapshotStore(context: Context) {
    private val file = File(context.filesDir, "snapshot.json")
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): BalanceSnapshot = runCatching {
        json.decodeFromString<BalanceSnapshot>(file.readText())
    }.getOrDefault(BalanceSnapshot())

    fun save(snapshot: BalanceSnapshot) {
        runCatching { file.writeText(json.encodeToString(snapshot)) }
    }
}

/** Décodage d'un QR code ou d'un fichier d'import. */
object WalletCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(text: String): WalletPayload? {
        val trimmed = text.trim()
        return runCatching { json.decodeFromString<WalletPayload>(trimmed) }.getOrNull()
            ?: runCatching { json.decodeFromString<List<WalletPayload>>(trimmed) }.getOrNull()?.firstOrNull()
    }
}
