package com.jclg.cryptowallettracker.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap

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
    /** Graphiques gardés en mémoire ; au-delà, le plus ancien est évincé. */
    private const val CHART_CACHE_SIZE = 100

    private var snapshotCache: Pair<Long, MarketSnapshot>? = null
    private var snapshotSource: PriceSource? = null
    private var ratesCache: Pair<Long, Rates>? = null
    private val chartCache = ConcurrentHashMap<String, Pair<Long, ChartData>>()

    /** Devises fiat à taux de change (les cryptos viennent des cours). */
    private val FIAT = Currency.entries.filterNot { it.crypto }

    /** Un seul appel réseau à la fois : l'interface et le worker partagent ce cache. */
    private val fetchMutex = Mutex()

    suspend fun snapshot(source: PriceSource, force: Boolean = false): MarketSnapshot =
        withContext(Dispatchers.IO) {
            fetchMutex.withLock {
                val now = System.currentTimeMillis()
                val cached = snapshotCache
                if (!force && cached != null && snapshotSource == source && now - cached.first < SNAPSHOT_TTL) {
                    return@withLock cached.second
                }

                val (markets, ranked) = marketsFromCoinGecko()
                if (source == PriceSource.BINANCE) {
                    runCatching { binancePrices(ranked) }.getOrNull()?.forEach { (id, price) ->
                        markets[id] = markets[id]?.copy(priceUsd = price) ?: Market(priceUsd = price)
                    }
                }

                // Les cryptos suivies mais hors top 100 (Monero, Decred…) ont besoin de leur cours.
                val missing = Coins.all.filter { it.id !in markets }
                if (missing.isNotEmpty()) {
                    runCatching { fetchMissing(missing.map { it.id }, markets) }
                }

                val snapshot = MarketSnapshot(markets, rates(markets), now, buildCatalog(ranked))
                snapshotCache = now to snapshot
                snapshotSource = source
                snapshot
            }
        }

    /** Cours des seules cryptos utiles (portefeuilles + accueil), pour la synchro de fond. */
    suspend fun quotes(ids: Collection<String>): MarketSnapshot = withContext(Dispatchers.IO) {
        fetchMutex.withLock {
            val markets = HashMap<String, Market>()
            fetchMissing(ids, markets)
            MarketSnapshot(markets, rates(markets), System.currentTimeMillis(), emptyList())
        }
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

    private suspend fun fetchMissing(ids: Collection<String>, markets: HashMap<String, Market>) {
        if (ids.isEmpty()) return
        val text = Net.get(
            "https://api.coingecko.com/api/v3/coins/markets" +
                "?vs_currency=usd&ids=${ids.joinToString(",")}&per_page=250&page=1&sparkline=true",
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
    private suspend fun binancePrices(ranked: List<Coin>): Map<String, Double> {
        val pairs = ranked.mapNotNull { coin -> coin.binance?.let { coin.id to it } }
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

    /**
     * Taux complets pour toutes les devises, ou exception : jamais une devise convertie à 1,0
     * sans le dire. CoinGecko d'abord, la BCE comble les devises manquantes, BTC/ETH viennent
     * des cours connus.
     */
    private suspend fun rates(markets: Map<String, Market>): Rates {
        val now = System.currentTimeMillis()
        ratesCache?.let { (at, cached) -> if (now - at < RATES_TTL) return cached }

        return runCatching {
            val byCurrency = HashMap<Currency, Double>()
            byCurrency[Currency.USD] = 1.0
            byCurrency.putAll(runCatching { coinGeckoFiatRates() }.getOrDefault(emptyMap()))
            completeFiatFromBce(byCurrency)
            listOf("bitcoin" to Currency.BTC, "ethereum" to Currency.ETH).forEach { (id, currency) ->
                val price = markets[id]?.priceUsd
                if (price != null && price > 0) byCurrency[currency] = 1.0 / price
                else ratesCache?.second?.byCurrency?.get(currency)?.let { byCurrency[currency] = it }
            }
            val missing = Currency.entries.filter { it !in byCurrency }
            if (missing.isNotEmpty()) error("taux indisponibles : ${missing.joinToString { it.code }}")
            Rates(byCurrency)
        }.getOrElse { error -> ratesCache?.second ?: throw error }
            .also { ratesCache = now to it }
    }

    /** Fiats déduits des cotations BTC de CoinGecko ; les devises absentes sont simplement omises. */
    private suspend fun coinGeckoFiatRates(): Map<Currency, Double> {
        val text = Net.get(
            "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd,eur,gbp,jpy,cny",
        )
        val btc = json.parseToJsonElement(text).jsonObject["bitcoin"]?.jsonObject
            ?: error("taux indisponibles")
        // Un prix USD nul rendrait tous les taux infinis.
        val usd = btc["usd"]?.jsonPrimitive?.doubleOrNull?.takeIf { it > 0 }
            ?: error("taux indisponibles")
        return FIAT.mapNotNull { currency ->
            btc[currency.code.lowercase()]?.jsonPrimitive?.doubleOrNull
                ?.takeIf { it > 0 }
                ?.let { currency to it / usd }
        }.toMap()
    }

    /** Repli : taux de référence de la BCE (Frankfurter, sans clé) pour les devises manquantes. */
    private suspend fun completeFiatFromBce(byCurrency: MutableMap<Currency, Double>) {
        val missing = FIAT.filter { it !in byCurrency }
        if (missing.isEmpty()) return
        val text = Net.get(
            "https://api.frankfurter.app/latest?from=USD&to=" + missing.joinToString(",") { it.code },
        )
        val fetched = json.parseToJsonElement(text).jsonObject["rates"]?.jsonObject
            ?: error("taux indisponibles")
        missing.forEach { currency ->
            fetched[currency.code]?.jsonPrimitive?.doubleOrNull
                ?.takeIf { it > 0 }
                ?.let { byCurrency[currency] = it }
        }
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
            if (!chartCache.containsKey(key) && chartCache.size >= CHART_CACHE_SIZE) {
                chartCache.entries.minByOrNull { it.value.first }?.let { chartCache.remove(it.key) }
            }
            chartCache[key] = now to fetched
            fetched
        }

    private suspend fun fetchChart(coin: Coin, range: ChartRange): ChartData =
        if (!range.long) {
            ChartData(coinGeckoChart(coin.id, range), "CoinGecko", System.currentTimeMillis())
        } else {
            longChart(coin, range)
        }

    private suspend fun coinGeckoChart(coinId: String, range: ChartRange): List<Point> {
        val days = range.days ?: 1
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
        // CoinGecko rend des points de 5 min sur 24 h : « 1 h » n'en garde que les 13 derniers.
        return if (range == ChartRange.H1) points.takeLast(13) else points
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
