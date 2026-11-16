package com.jclg.cryptowallettracker.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class ChartRange(val label: String, val days: Int?, val long: Boolean) {
    H1("1 h", 1, false),
    D1("24 h", 1, false),
    D7("7 j", 7, false),
    D30("30 j", 30, false),
    Y1("1 an", 365, false),
    Y2("2 ans", 730, true),
    Y5("5 ans", 1825, true),
    MAX("Max", null, true),
}

data class MarketSnapshot(
    val markets: Map<String, Market>,
    val rates: Rates,
    val fetchedAt: Long,
    /** Top 100 des cryptos + cryptos suivies hors classement (XMR, DCR…). */
    val catalog: List<Coin>,
)

data class ChartData(val points: List<Point>, val source: String, val fetchedAt: Long)

/**
 * Cours et historique, avec cache mémoire.
 * CoinGecko couvre les 365 derniers jours ; au-delà (2 ans, 5 ans, max) on passe
 * par Binance puis Kraken, car l'API publique CoinGecko refuse > 365 jours (HTTP 401).
 */
object Markets {
    private val json = Json { ignoreUnknownKeys = true }

    private const val SNAPSHOT_TTL = 24 * 3_600_000L
    private const val RATES_TTL = 3_600_000L
    private const val SHORT_CHART_TTL = 3_600_000L
    private const val LONG_CHART_TTL = 24 * 3_600_000L

    private var snapshotCache: Pair<Long, MarketSnapshot>? = null
    private var snapshotSource: PriceSource? = null
    private var ratesCache: Pair<Long, Rates>? = null
    private val chartCache = HashMap<String, Pair<Long, ChartData>>()

    suspend fun snapshot(source: PriceSource, force: Boolean = false): MarketSnapshot =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val cached = snapshotCache
            if (!force && cached != null && snapshotSource == source && now - cached.first < SNAPSHOT_TTL) {
                return@withContext cached.second
            }

            val (markets, ranked) = marketsFromCoinGecko()
            if (source == PriceSource.BINANCE) {
                runCatching { binancePrices() }.getOrNull()?.forEach { (id, price) ->
                    markets[id] = markets[id]?.copy(priceUsd = price) ?: Market(priceUsd = price)
                }
            }

            // Les cryptos suivies mais hors top 100 (Monero, Decred…) ont besoin de leur cours.
            val missing = Coins.all.filter { it.id !in markets }
            if (missing.isNotEmpty()) {
                runCatching { fetchMissing(missing, markets) }
            }

            val snapshot = MarketSnapshot(markets, rates(markets), now, buildCatalog(ranked))
            snapshotCache = now to snapshot
            snapshotSource = source
            snapshot
        }

    /** Top 100 par capitalisation : cours, logos, mini-courbes et catalogue. */
    private suspend fun marketsFromCoinGecko(): Pair<HashMap<String, Market>, List<Coin>> {
        val text = Net.get(
            "https://api.coingecko.com/api/v3/coins/markets" +
                "?vs_currency=usd&order=market_cap_desc&per_page=100&page=1&sparkline=true",
        )
        val markets = HashMap<String, Market>()
        val ranked = ArrayList<Coin>()
        json.parseToJsonElement(text).jsonArray.forEach { element ->
            val o = element.jsonObject
            val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            markets[id] = marketOf(o)
            val symbol = o["symbol"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val name = o["name"]?.jsonPrimitive?.contentOrNull ?: symbol
            ranked.add(Coins.byId(id) ?: Coins.priceOnly(id, symbol, name))
        }
        return markets to ranked
    }

    private suspend fun fetchMissing(missing: List<Coin>, markets: HashMap<String, Market>) {
        val ids = missing.joinToString(",") { it.id }
        val text = Net.get(
            "https://api.coingecko.com/api/v3/coins/markets" +
                "?vs_currency=usd&ids=$ids&per_page=250&page=1&sparkline=true",
        )
        json.parseToJsonElement(text).jsonArray.forEach { element ->
            val o = element.jsonObject
            val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            markets[id] = marketOf(o)
        }
    }

    private fun marketOf(o: kotlinx.serialization.json.JsonObject): Market {
        val sparkline = o["sparkline_in_7d"]?.jsonObject?.get("price")?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.doubleOrNull }
            .orEmpty()
        return Market(
            priceUsd = o["current_price"]?.jsonPrimitive?.doubleOrNull,
            change24h = o["price_change_percentage_24h"]?.jsonPrimitive?.doubleOrNull,
            image = o["image"]?.jsonPrimitive?.contentOrNull,
            sparkline = sparkline,
        )
    }

    private fun buildCatalog(ranked: List<Coin>): List<Coin> {
        val rankedIds = ranked.mapTo(HashSet()) { it.id }
        return ranked + Coins.all.filter { it.id !in rankedIds }
    }

    /** Prix USDT ≈ USD ; CoinGecko complète les cryptos absentes de Binance. */
    private suspend fun binancePrices(): Map<String, Double> {
        val pairs = Coins.all.mapNotNull { coin -> coin.binance?.let { coin.id to it } }
        val text = Net.get("https://api.binance.com/api/v3/ticker/price")
        val byPair = HashMap<String, Double>()
        json.parseToJsonElement(text).jsonArray.forEach { element ->
            val o = element.jsonObject
            val symbol = o["symbol"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val price = o["price"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: return@forEach
            byPair[symbol] = price
        }
        return pairs.mapNotNull { (id, pair) -> byPair[pair]?.let { id to it } }.toMap()
    }

    /** Taux dérivés du prix du bitcoin par devise (1 requête, mise en cache 1 h). */
    private suspend fun rates(markets: Map<String, Market>): Rates {
        val now = System.currentTimeMillis()
        ratesCache?.let { (at, cached) -> if (now - at < RATES_TTL) return cached }

        return runCatching {
            val text = Net.get(
                "https://api.coingecko.com/api/v3/simple/price" +
                    "?ids=bitcoin,ethereum&vs_currencies=usd,eur,gbp,jpy,cny",
            )
            val root = json.parseToJsonElement(text).jsonObject
            val btc = root["bitcoin"]?.jsonObject ?: error("taux indisponibles")
            val usd = btc["usd"]?.jsonPrimitive?.doubleOrNull ?: error("taux indisponibles")
            val ethUsd = markets["ethereum"]?.priceUsd
                ?: root["ethereum"]?.jsonObject?.get("usd")?.jsonPrimitive?.doubleOrNull

            val byCurrency = HashMap<Currency, Double>()
            byCurrency[Currency.USD] = 1.0
            btc["eur"]?.jsonPrimitive?.doubleOrNull?.let { byCurrency[Currency.EUR] = it / usd }
            btc["gbp"]?.jsonPrimitive?.doubleOrNull?.let { byCurrency[Currency.GBP] = it / usd }
            btc["jpy"]?.jsonPrimitive?.doubleOrNull?.let { byCurrency[Currency.JPY] = it / usd }
            btc["cny"]?.jsonPrimitive?.doubleOrNull?.let { byCurrency[Currency.CNY] = it / usd }
            if (usd > 0) byCurrency[Currency.BTC] = 1.0 / usd
            if (ethUsd != null && ethUsd > 0) byCurrency[Currency.ETH] = 1.0 / ethUsd
            Rates(byCurrency)
        }.getOrElse { error ->
            ratesCache?.second ?: throw error
        }.also { ratesCache = now to it }
    }

    /** Historique en USD (converti à l'affichage), avec cache par crypto et période. */
    suspend fun chart(coin: Coin, range: ChartRange, force: Boolean = false): ChartData =
        withContext(Dispatchers.IO) {
            val key = "${coin.id}:${range.name}"
            val now = System.currentTimeMillis()
            val ttl = if (range.long) LONG_CHART_TTL else SHORT_CHART_TTL
            if (!force) {
                chartCache[key]?.let { (at, cached) -> if (now - at < ttl) return@withContext cached }
            }

            val fetched = fetchChart(coin, range)
            chartCache[key] = now to fetched
            fetched
        }

    private suspend fun fetchChart(coin: Coin, range: ChartRange): ChartData =
        if (!range.long) {
            ChartData(coinGeckoChart(coin.id, range.days ?: 1), "CoinGecko", System.currentTimeMillis())
        } else {
            longChart(coin, range)
        }

    private suspend fun coinGeckoChart(coinId: String, days: Int): List<Point> {
        val text = Net.get(
            "https://api.coingecko.com/api/v3/coins/$coinId/market_chart" +
                "?vs_currency=usd&days=$days",
        )
        val points = json.parseToJsonElement(text).jsonObject["prices"]?.jsonArray.orEmpty()
            .mapNotNull { row ->
                val values = row.jsonArray
                val time = values.getOrNull(0)?.jsonPrimitive?.doubleOrNull?.toLong() ?: return@mapNotNull null
                val price = values.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                Point(time, price)
            }
        return if (days == 1) points.takeLast(13) else points
    }

    /** 2 ans, 5 ans, max : Binance d'abord, Kraken en secours (XMR, DCR…). */
    private suspend fun longChart(coin: Coin, range: ChartRange): ChartData {
        var lastError: Throwable? = null

        coin.binance?.let { pair ->
            val result = runCatching { binanceKlines(pair, range) }
            result.exceptionOrNull()?.let { lastError = it }
            result.getOrNull()
                ?.takeIf { it.size > 2 }
                ?.let { return ChartData(it, "Binance", System.currentTimeMillis()) }
        }

        val kraken = runCatching { krakenOhlc(coin.symbol, range) }
        kraken.exceptionOrNull()?.let { lastError = it }
        kraken.getOrNull()
            ?.takeIf { it.size > 2 }
            ?.let { return ChartData(it, "Kraken", System.currentTimeMillis()) }

        throw lastError ?: IllegalStateException("période indisponible pour ${coin.symbol}")
    }

    private suspend fun binanceKlines(pair: String, range: ChartRange): List<Point> {
        val (interval, limit) = when (range) {
            ChartRange.Y2 -> "1d" to 730
            ChartRange.Y5 -> "1w" to 260
            else -> "1M" to 1000
        }
        val text = Net.get(
            "https://api.binance.com/api/v3/klines?symbol=$pair&interval=$interval&limit=$limit",
        )
        return json.parseToJsonElement(text).jsonArray.mapNotNull { row ->
            val values = row.jsonArray
            val time = values.getOrNull(0)?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: return@mapNotNull null
            val close = values.getOrNull(4)?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                ?: return@mapNotNull null
            Point(time, close)
        }
    }

    private suspend fun krakenOhlc(symbol: String, range: ChartRange): List<Point> {
        val interval = if (range == ChartRange.Y2) 1440 else 10_080
        val text = Net.get("https://api.kraken.com/0/public/OHLC?pair=${symbol}USD&interval=$interval")
        val root = json.parseToJsonElement(text).jsonObject
        val result = root["result"]?.jsonObject ?: error("réponse Kraken invalide")
        val series = result.entries
            .firstOrNull { it.key != "last" && it.value is JsonArray }
            ?.value as? JsonArray
            ?: error("paire Kraken indisponible")
        return series.mapNotNull { row ->
            val values = row.jsonArray
            val time = values.getOrNull(0)?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: return@mapNotNull null
            val close = values.getOrNull(4)?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                ?: return@mapNotNull null
            Point(time * 1000, close)
        }
    }
}
