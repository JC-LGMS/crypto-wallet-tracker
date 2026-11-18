package com.jclg.cryptowallettracker.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Wallet(
    val id: String = UUID.randomUUID().toString(),
    val coinId: String = "",
    /** null = première source disponible pour la crypto. */
    val source: SourceId? = null,
    val address: String,
    /** Clé d'affichage privée, uniquement pour Monero. */
    val viewKey: String = "",
    val name: String = "",
    val comment: String = "",
    /** Ancien champ (versions 1.0) : sert uniquement à la migration. */
    val chain: String = "",
) {
    val coin: Coin? get() = Coins.byId(coinId)

    val sourceOrFirst: SourceId? get() = source ?: coin?.sources?.firstOrNull()
}

/** Format d'échange (export fichier et QR code). */
@Serializable
data class WalletPayload(
    /** Identifiant CoinGecko ; accepte aussi les anciens noms ETHEREUM/DECRED/MONERO. */
    val c: String,
    val a: String,
    val v: String = "",
    val n: String = "",
    val m: String = "",
    val s: String = "",
) {
    fun toWallet(): Wallet? {
        val coin = Coins.fromLegacy(c) ?: return null
        val source = SourceId.entries.firstOrNull { it.name == s }
            ?.takeIf { it in coin.sources }
        return Wallet(
            coinId = coin.id,
            source = source,
            address = a.trim(),
            viewKey = v.trim(),
            name = n.trim(),
            comment = m.trim(),
        )
    }

    companion object {
        fun from(w: Wallet) = WalletPayload(
            c = w.coinId,
            a = w.address,
            v = w.viewKey,
            n = w.name,
            m = w.comment,
            s = w.source?.name.orEmpty(),
        )
    }
}

data class Balance(
    val amount: Double? = null,
    val loading: Boolean = false,
    val error: String? = null,
    /** Dernière synchronisation réussie (millis). */
    val updatedAt: Long? = null,
)

data class Tx(
    val id: String,
    val timeMillis: Long,
    /** Montant signé en unités de la crypto : positif reçu, négatif envoyé. */
    val amount: Double?,
    val symbol: String,
    val explorerUrl: String,
)

data class HistoryState(
    val loading: Boolean = false,
    val txs: List<Tx> = emptyList(),
    val note: String? = null,
    val error: String? = null,
    val updatedAt: Long? = null,
    /** Vrai si la source contient plus de transactions que celles récupérées. */
    val hasMore: Boolean = false,
)

/** Transactions récupérées + indication de pagination. */
data class HistoryPage(
    val txs: List<Tx>,
    val hasMore: Boolean = false,
)

data class Point(val time: Long, val value: Double)

/** Cours en USD + variation 24 h + logo (URL CoinGecko) + mini-courbe 7 j. */
@Serializable
data class Market(
    val priceUsd: Double? = null,
    val change24h: Double? = null,
    val image: String? = null,
    val sparkline: List<Double> = emptyList(),
)

/** Taux de conversion depuis 1 USD (BTC/ETH = quantité de crypto). */
@Serializable
data class Rates(val byCurrency: Map<Currency, Double>) {
    fun rate(currency: Currency): Double = byCurrency[currency] ?: 1.0
}
