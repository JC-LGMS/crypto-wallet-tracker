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

class SnapshotStore(private val file: File) {
    constructor(context: Context, fileName: String = "snapshot.json") : this(File(context.filesDir, fileName))

    private val json = Json { ignoreUnknownKeys = true }

    fun load(): BalanceSnapshot = synchronized(LOCK) { read() }

    /** Fusionne avec l'état du disque : l'interface et le worker écrivent tous les deux. */
    fun save(snapshot: BalanceSnapshot) {
        synchronized(LOCK) {
            runCatching { file.writeText(json.encodeToString(merge(read(), snapshot))) }
        }
    }

    private fun read(): BalanceSnapshot = runCatching {
        json.decodeFromString<BalanceSnapshot>(file.readText())
    }.getOrDefault(BalanceSnapshot())

    private fun merge(old: BalanceSnapshot, new: BalanceSnapshot): BalanceSnapshot {
        val balances = HashMap(old.balances)
        new.balances.forEach { (id, entry) ->
            val current = balances[id]
            if (current == null || entry.updatedAt >= current.updatedAt) balances[id] = entry
        }
        val newer = if ((new.quotesUpdatedAt ?: 0) >= (old.quotesUpdatedAt ?: 0)) new else old
        val older = if (newer === new) old else new
        return BalanceSnapshot(
            balances = balances,
            markets = newer.markets.ifEmpty { older.markets },
            rates = if (newer.rates.byCurrency.isEmpty()) older.rates else newer.rates,
            quotesUpdatedAt = newer.quotesUpdatedAt ?: older.quotesUpdatedAt,
        )
    }

    private companion object {
        val LOCK = Any()
    }
}

/** Décodage d'un QR code ou d'un fichier d'import. */
object WalletCodec {
    private val json = Json { ignoreUnknownKeys = true }

    /** Accepte un export fichier (liste) ou un QR code (objet unique). */
    fun decodeAll(text: String): List<WalletPayload> {
        val trimmed = text.trim()
        return runCatching { json.decodeFromString<List<WalletPayload>>(trimmed) }.getOrNull()
            ?: listOf(json.decodeFromString<WalletPayload>(trimmed))
    }

    fun decode(text: String): WalletPayload? =
        runCatching { decodeAll(text) }.getOrNull()?.firstOrNull()
}
