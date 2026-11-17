package com.jclg.cryptowallettracker.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.math.BigInteger

/** Soldes et historiques, une implémentation par SourceId. */
object Balances {
    private val json = Json { ignoreUnknownKeys = true }
    private const val ETH_RPC = "https://ethereum-rpc.publicnode.com"

    suspend fun balance(
        wallet: Wallet,
        etherscanKey: String = "",
        beaconchainKey: String = "",
    ): Double = withContext(Dispatchers.IO) {
        val coin = wallet.coin ?: error("crypto inconnue")
        when (val source = wallet.sourceOrFirst ?: error("aucune source de solde pour ${coin.symbol}")) {
            SourceId.EVM_RPC, SourceId.BSC_RPC, SourceId.AVAX_RPC ->
                evmBalance(coin.rpc ?: ETH_RPC, wallet.address, null, coin.decimals)

            SourceId.ERC20_RPC ->
                evmBalance(ETH_RPC, wallet.address, coin.contract, coin.decimals)

            SourceId.BLOCKSCOUT -> blockscoutBalance(wallet.address)
            SourceId.ETHERSCAN -> etherscanBalance(wallet.address, etherscanKey)
            SourceId.BEACONCHAIN -> beaconchainBalance(wallet.address, beaconchainKey)
            SourceId.MEMPOOL -> mempoolBalance("https://mempool.space", wallet.address)
            SourceId.BLOCKSTREAM -> mempoolBalance("https://blockstream.info", wallet.address)
            SourceId.RIPPLE -> rippleBalance(wallet.address)
            SourceId.SOLANA -> solanaBalance(wallet.address)
            SourceId.DOGECHAIN -> dogeBalance(wallet.address)
            SourceId.KOIOS -> koiosBalance(wallet.address)
            SourceId.TRONGRID -> tronBalance(wallet.address)
            SourceId.HORIZON -> stellarBalance(wallet.address)
            SourceId.LITECOINSPACE -> mempoolBalance("https://litecoinspace.org", wallet.address)
            SourceId.TONCENTER -> tonBalance(wallet.address)
            SourceId.LWS -> {
                require(wallet.viewKey.isNotBlank()) { "clé d'affichage requise pour Monero" }
                moneroBalance(wallet.address, wallet.viewKey)
            }
            SourceId.DCRDATA -> dcrBalance(wallet.address)
        }
    }

    suspend fun history(wallet: Wallet): HistoryPage = withContext(Dispatchers.IO) {
        when (wallet.sourceOrFirst) {
            SourceId.BLOCKSCOUT -> blockscoutTxs(wallet.address)
            SourceId.MEMPOOL -> btcTxs("https://mempool.space", wallet.address)
            SourceId.BLOCKSTREAM -> btcTxs("https://blockstream.info", wallet.address)
            SourceId.DCRDATA -> dcrTxs(wallet.address)
            else -> HistoryPage(emptyList())
        }
    }

    // ---------- EVM ----------

    private suspend fun evmBalance(rpc: String, address: String, contract: String?, decimals: Int): Double {
        val result = if (contract == null) {
            rpcCall(rpc, "eth_getBalance", """["$address","latest"]""")["result"]?.jsonPrimitive?.contentOrNull
        } else {
            val data = "0x70a08231" + address.removePrefix("0x").lowercase().padStart(64, '0')
            rpcCall(rpc, "eth_call", """[{"to":"$contract","data":"$data"},"latest"]""")["result"]?.jsonPrimitive?.contentOrNull
        } ?: error("réponse RPC invalide")
        return BigDecimal(BigInteger(result.removePrefix("0x"), 16), 0).movePointLeft(decimals).toDouble()
    }

    private suspend fun rpcCall(url: String, method: String, params: String): JsonObject {
        val body = """{"jsonrpc":"2.0","id":1,"method":"$method","params":$params}"""
        val root = json.parseToJsonElement(Net.postJson(url, body)).jsonObject
        root["error"]?.jsonObject?.let { error(it["message"]?.jsonPrimitive?.contentOrNull ?: "erreur RPC") }
        return root
    }

    private suspend fun blockscoutBalance(address: String): Double {
        val root = json.parseToJsonElement(
            Net.get("https://eth.blockscout.com/api/v2/addresses/$address"),
        ).jsonObject
        val wei = root["coin_balance"]?.jsonPrimitive?.contentOrNull?.toBigIntegerOrNull()
            ?: error("adresse refusée par Blockscout")
        return BigDecimal(wei).movePointLeft(18).toDouble()
    }

    private suspend fun etherscanBalance(address: String, key: String): Double {
        require(key.isNotBlank()) { "clé API Etherscan requise (Réglages › Sources de données)" }
        val root = json.parseToJsonElement(
            Net.get(
                "https://api.etherscan.io/v2/api?chainid=1&module=account&action=balance" +
                    "&address=$address&tag=latest&apikey=$key",
            ),
        ).jsonObject
        val result = root["result"]?.jsonPrimitive?.contentOrNull
        if (root["status"]?.jsonPrimitive?.contentOrNull != "1") {
            error(result ?: "réponse Etherscan invalide")
        }
        val wei = result?.toBigIntegerOrNull() ?: error("solde Etherscan illisible")
        return BigDecimal(wei).movePointLeft(18).toDouble()
    }

    private suspend fun beaconchainBalance(address: String, key: String): Double {
        require(key.isNotBlank()) { "clé API beaconcha.in requise (Réglages › Sources de données)" }
        val text = try {
            Net.get("https://beaconcha.in/api/v1/execution/address/$address?apikey=$key")
        } catch (e: ApiException) {
            if (e.code == 401) error("clé API beaconcha.in invalide") else throw e
        }
        val root = json.parseToJsonElement(text).jsonObject
        val ether = root["data"]?.jsonObject?.get("ether")?.jsonPrimitive?.contentOrNull
            ?: error(root["status"]?.jsonPrimitive?.contentOrNull ?: "réponse beaconcha.in invalide")
        return ether.toDoubleOrNull() ?: error("solde beaconcha.in illisible")
    }

    private suspend fun blockscoutTxs(address: String): HistoryPage {
        val root = json.parseToJsonElement(
            Net.get("https://eth.blockscout.com/api/v2/addresses/$address/transactions"),
        ).jsonObject
        val txs = root["items"]?.jsonArray.orEmpty().take(20).mapNotNull { element ->
            val o = element.jsonObject
            val hash = o["hash"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val from = o["from"]?.jsonObject?.get("hash")?.jsonPrimitive?.contentOrNull
            val sent = from.equals(address, ignoreCase = true)
            val wei = o["value"]?.jsonPrimitive?.content?.toBigIntegerOrNull() ?: BigInteger.ZERO
            val amount = BigDecimal(wei).movePointLeft(18).toDouble()
            Tx(
                id = hash,
                timeMillis = parseIso(o["timestamp"]?.jsonPrimitive?.contentOrNull),
                amount = if (sent) -amount else amount,
                symbol = "ETH",
                explorerUrl = "https://eth.blockscout.com/tx/$hash",
            )
        }
        val next = root["next_page_params"]
        return HistoryPage(txs, hasMore = next != null && next !is JsonNull)
    }

    // ---------- Bitcoin / Litecoin ----------

    private suspend fun mempoolBalance(base: String, address: String): Double {
        val root = json.parseToJsonElement(Net.get("$base/api/address/$address")).jsonObject
        val stats = root["chain_stats"]?.jsonObject ?: error("adresse refusée par l'explorateur")
        val funded = stats["funded_txo_sum"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val spent = stats["spent_txo_sum"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        return (funded - spent) / 100_000_000.0
    }

    private suspend fun btcTxs(base: String, address: String): HistoryPage {
        val list = json.parseToJsonElement(Net.get("$base/api/address/$address/txs")).jsonArray
        val total = runCatching {
            json.parseToJsonElement(Net.get("$base/api/address/$address")).jsonObject["chain_stats"]
                ?.jsonObject?.get("tx_count")?.jsonPrimitive?.intOrNull ?: 0
        }.getOrDefault(0)
        val txs = list.take(20).mapNotNull { element ->
            val tx = element.jsonObject
            val txid = tx["txid"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val received = tx["vout"]?.jsonArray.orEmpty().sumOf { out ->
                val o = out.jsonObject
                if (o["scriptpubkey_address"]?.jsonPrimitive?.contentOrNull == address) {
                    o["value"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                } else 0.0
            }
            val spent = tx["vin"]?.jsonArray.orEmpty().sumOf { input ->
                val prev = input.jsonObject["prevout"]?.jsonObject
                if (prev?.get("scriptpubkey_address")?.jsonPrimitive?.contentOrNull == address) {
                    prev["value"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                } else 0.0
            }
            val net = (received - spent) / 100_000_000.0
            val time = tx["status"]?.jsonObject?.get("block_time")?.jsonPrimitive?.contentOrNull
                ?.toLongOrNull() ?: 0L
            Tx(txid, time * 1000, net, "BTC", "$base/tx/$txid")
        }
        return HistoryPage(txs, hasMore = total > txs.size)
    }

    // ---------- Autres chaînes ----------

    private suspend fun rippleBalance(address: String): Double {
        val root = rpcCall(
            "https://s1.ripple.com:51234/",
            "account_info",
            """[{"account":"$address","ledger_index":"validated"}]""",
        )
        val drops = root["result"]?.jsonObject?.get("account_data")?.jsonObject
            ?.get("Balance")?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            ?: error("compte XRP introuvable ou vide")
        return drops / 1_000_000.0
    }

    private suspend fun solanaBalance(address: String): Double {
        val root = rpcCall("https://api.mainnet-beta.solana.com", "getBalance", """["$address"]""")
        val lamports = root["result"]?.jsonObject?.get("value")?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            ?: error("réponse Solana invalide")
        return lamports / 1_000_000_000.0
    }

    private suspend fun dogeBalance(address: String): Double {
        val root = json.parseToJsonElement(
            Net.get("https://dogechain.info/api/v1/address/balance/$address"),
        ).jsonObject
        return root["balance"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            ?: error(root["error"]?.jsonPrimitive?.contentOrNull ?: "adresse Dogecoin invalide")
    }

    private suspend fun koiosBalance(address: String): Double {
        val root = json.parseToJsonElement(
            Net.postJson("https://api.koios.rest/api/v1/address_info", """{"_addresses":["$address"]}"""),
        ).jsonArray.firstOrNull()?.jsonObject ?: error("adresse Cardano inconnue")
        val lovelace = root["balance"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
        return lovelace / 1_000_000.0
    }

    private suspend fun tronBalance(address: String): Double {
        val root = json.parseToJsonElement(Net.get("https://api.trongrid.io/v1/accounts/$address")).jsonObject
        val sun = root["data"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("balance")?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
        return sun / 1_000_000.0
    }

    private suspend fun stellarBalance(address: String): Double {
        val root = json.parseToJsonElement(Net.get("https://horizon.stellar.org/accounts/$address")).jsonObject
        val native = root["balances"]?.jsonArray.orEmpty().firstOrNull {
            it.jsonObject["asset_type"]?.jsonPrimitive?.contentOrNull == "native"
        }?.jsonObject ?: return 0.0
        return native["balance"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 0.0
    }

    private suspend fun tonBalance(address: String): Double {
        val root = json.parseToJsonElement(
            Net.get("https://toncenter.com/api/v2/getAddressInformation?address=$address"),
        ).jsonObject
        val nano = root["result"]?.jsonObject?.get("balance")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            ?: error("adresse TON invalide")
        return nano / 1_000_000_000.0
    }

    private suspend fun moneroBalance(address: String, viewKey: String): Double {
        require(viewKey.length == 64) { "clé d'affichage invalide (64 caractères hexadécimaux)" }
        val payload = """{"address":"$address","view_key":"${viewKey.lowercase()}","create_account":false}"""
        val text = try {
            Net.postJson("https://lws.monero.fail/get_address_info", payload)
        } catch (e: ApiException) {
            if (e.code == 500) {
                error("portefeuille inconnu du serveur LWS : enregistrez-le sur https://lightwallet.monero.fail/register")
            }
            throw e
        }
        val root = json.parseToJsonElement(text).jsonObject
        val received = root["total_received"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            ?: error("réponse LWS invalide")
        val sent = root["total_sent"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
        return BigDecimal(received - sent).movePointLeft(12).toDouble()
    }

    private suspend fun dcrBalance(address: String): Double {
        val root = json.parseToJsonElement(
            Net.get("https://dcrdata.decred.org/api/address/$address/totals"),
        ).jsonObject
        return root["dcr_unspent"]?.jsonPrimitive?.doubleOrNull ?: error("réponse inattendue de dcrdata")
    }

    private suspend fun dcrTxs(address: String): HistoryPage {
        val root = json.parseToJsonElement(
            Net.get("https://dcrdata.decred.org/api/address/$address"),
        ).jsonObject
        val allIds = root["address_transactions"]?.jsonArray.orEmpty()
            .mapNotNull { it.jsonObject["txid"]?.jsonPrimitive?.contentOrNull }
            .distinct()
        val ids = allIds.take(5)
        val fetcher = TxFetcher()
        val txs = ids.mapNotNull { id -> runCatching { fetcher.toTx(id, address) }.getOrNull() }
        return HistoryPage(txs, hasMore = allIds.size > ids.size)
    }

    private fun owns(output: JsonObject, address: String): Boolean =
        output["scriptPubKey"]?.jsonObject?.get("addresses")?.jsonArray.orEmpty()
            .any { it.jsonPrimitive.contentOrNull.equals(address, ignoreCase = true) }

    /** Récupère les transactions sources (entrées) une seule fois pour le lot affiché. */
    private class TxFetcher {
        private val cache = HashMap<String, JsonObject>()

        fun fetch(txid: String): JsonObject =
            cache.getOrPut(txid) {
                json.parseToJsonElement(Net.get("https://dcrdata.decred.org/api/tx/$txid")).jsonObject
            }

        fun toTx(txid: String, address: String): Tx {
            val tx = fetch(txid)
            val vouts = tx["vout"]?.jsonArray ?: JsonArray(emptyList())
            val received = vouts.sumOf { out ->
                val o = out.jsonObject
                if (owns(o, address)) o["value"]?.jsonPrimitive?.doubleOrNull ?: 0.0 else 0.0
            }

            // Montant envoyé = entrées possédées - rendu. On saute le calcul au-delà de 20 entrées.
            val vins = tx["vin"]?.jsonArray
            val ownIn = if (vins == null || vins.size > 20) null else vins.sumOf { vin ->
                val o = vin.jsonObject
                val prevId = o["txid"]?.jsonPrimitive?.contentOrNull
                val prevIndex = o["vout"]?.jsonPrimitive?.intOrNull
                if (prevId == null || prevIndex == null) return@sumOf 0.0
                val prevOut = fetch(prevId)["vout"]?.jsonArray?.getOrNull(prevIndex)?.jsonObject
                if (prevOut != null && owns(prevOut, address)) {
                    o["amountin"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                } else 0.0
            }

            val amount = when {
                ownIn == null -> null
                ownIn > 0 -> -(ownIn - received)
                received > 0 -> received
                else -> null
            }
            val time = tx["block"]?.jsonObject?.get("time")?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
            return Tx(txid, time * 1000, amount, "DCR", "https://dcrdata.decred.org/tx/$txid")
        }
    }
}
