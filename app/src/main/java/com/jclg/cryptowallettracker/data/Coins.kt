package com.jclg.cryptowallettracker.data

import kotlinx.serialization.Serializable

@Serializable
enum class Currency(val code: String, val label: String, val symbol: String, val crypto: Boolean = false) {
    EUR("EUR", "Euro", "€"),
    USD("USD", "Dollar US", "$"),
    GBP("GBP", "Livre sterling", "£"),
    JPY("JPY", "Yen", "¥"),
    CNY("CNY", "Yuan", "CNY"),
    BTC("BTC", "Bitcoin", "BTC", crypto = true),
    ETH("ETH", "Ether", "ETH", crypto = true),
    ;

    companion object {
        fun fromCode(code: String?): Currency = entries.firstOrNull { it.code == code } ?: EUR
    }
}

@Serializable
enum class ThemeMode(val label: String) {
    SYSTEM("Automatique (système)"),
    LIGHT("Clair"),
    DARK("Sombre"),
}

@Serializable
enum class PriceSource(val label: String, val detail: String) {
    COINGECKO("CoinGecko", "Cours, logos et historique des graphiques"),
    BINANCE("Binance", "Paires USDT, complétées par CoinGecko pour les cryptos absentes"),
}

/** Une source de données de solde. `history` indique si les transactions sont disponibles. */
@Serializable
enum class SourceId(val label: String, val history: Boolean) {
    EVM_RPC("RPC public Ethereum", false),
    BLOCKSCOUT("Blockscout", true),
    ETHERSCAN("etherscan.io", false),
    BEACONCHAIN("beaconcha.in", false),
    MEMPOOL("mempool.space", true),
    BLOCKSTREAM("Blockstream", true),
    ERC20_RPC("RPC Ethereum (jeton ERC-20)", false),
    RIPPLE("Ripple s1.ripple.com", false),
    BSC_RPC("BSC RPC public", false),
    SOLANA("Solana RPC public", false),
    DOGECHAIN("dogechain.info", false),
    KOIOS("Koios", false),
    TRONGRID("TronGrid", false),
    AVAX_RPC("Avalanche C-Chain RPC", false),
    HORIZON("Horizon (Stellar)", false),
    LITECOINSPACE("litecoinspace.org", false),
    TONCENTER("toncenter.com", false),
    LWS("Serveur LWS Monero", false),
    DCRDATA("dcrdata", true),
}

data class Coin(
    val id: String,
    val symbol: String,
    val name: String,
    val decimals: Int,
    val color: Long,
    val binance: String? = null,
    val sources: List<SourceId> = emptyList(),
    val pattern: Regex,
    val hint: String,
    val contract: String? = null,
    val rpc: String? = null,
    /** Modèle d'URL d'explorateur, `{addr}` remplacé par l'adresse. */
    val explorerAddress: String? = null,
) {
    val capabilities: String
        get() = when {
            sources.isEmpty() -> "Cours seulement"
            sources.any { it.history } -> "Solde + transactions"
            else -> "Solde"
        }

    fun matchesAddress(address: String): Boolean = pattern.matches(address.trim())

    fun explorerUrl(address: String): String? = explorerAddress?.replace("{addr}", address)
}

private val evm = Regex("^0x[0-9a-fA-F]{40}$")

object Coins {
    val all: List<Coin> = listOf(
        Coin(
            "bitcoin", "BTC", "Bitcoin", 8, 0xFFF7931A, "BTCUSDT",
            listOf(SourceId.MEMPOOL, SourceId.BLOCKSTREAM),
            Regex("^(bc1|[13])[a-zA-HJ-NP-Z0-9]{25,62}$"), "bc1… ou 1… / 3…",
            explorerAddress = "https://mempool.space/address/{addr}",
        ),
        Coin(
            "ethereum", "ETH", "Ethereum", 18, 0xFF627EEA, "ETHUSDT",
            listOf(SourceId.EVM_RPC, SourceId.BLOCKSCOUT, SourceId.ETHERSCAN, SourceId.BEACONCHAIN),
            evm, "0x… (40 caractères hex)",
            rpc = "https://ethereum-rpc.publicnode.com",
            explorerAddress = "https://eth.blockscout.com/address/{addr}",
        ),
        Coin(
            "tether", "USDT", "Tether", 6, 0xFF26A17B, null,
            listOf(SourceId.ERC20_RPC),
            evm, "0x… (40 caractères hex)",
            contract = "0xdAC17F958D2ee523a2206206994597C13D831ec7",
            explorerAddress = "https://eth.blockscout.com/address/{addr}",
        ),
        Coin(
            "ripple", "XRP", "XRP", 6, 0xFF23292F, "XRPUSDT",
            listOf(SourceId.RIPPLE),
            Regex("^r[1-9A-HJ-NP-Za-km-z]{24,34}$"), "r…",
            explorerAddress = "https://xrpscan.com/account/{addr}",
        ),
        Coin(
            "binancecoin", "BNB", "BNB", 18, 0xFFF3BA2F, "BNBUSDT",
            listOf(SourceId.BSC_RPC),
            evm, "0x… (40 caractères hex)",
            rpc = "https://bsc-rpc.publicnode.com",
            explorerAddress = "https://bscscan.com/address/{addr}",
        ),
        Coin(
            "solana", "SOL", "Solana", 9, 0xFF14F195, "SOLUSDT",
            listOf(SourceId.SOLANA),
            Regex("^[1-9A-HJ-NP-Za-km-z]{32,44}$"), "base58, 32 à 44 caractères",
            explorerAddress = "https://solscan.io/account/{addr}",
        ),
        Coin(
            "usd-coin", "USDC", "USD Coin", 6, 0xFF2775CA, null,
            listOf(SourceId.ERC20_RPC),
            evm, "0x… (40 caractères hex)",
            contract = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
            explorerAddress = "https://eth.blockscout.com/address/{addr}",
        ),
        Coin(
            "dogecoin", "DOGE", "Dogecoin", 8, 0xFFC2A633, "DOGEUSDT",
            listOf(SourceId.DOGECHAIN),
            Regex("^D[5-9A-HJ-NP-U][1-9A-HJ-NP-Za-km-z]{32}$"), "D… (34 caractères)",
            explorerAddress = "https://dogechain.info/address/{addr}",
        ),
        Coin(
            "cardano", "ADA", "Cardano", 6, 0xFF0033AD, "ADAUSDT",
            listOf(SourceId.KOIOS),
            Regex("^addr1[a-z0-9]{20,}$"), "addr1…",
            explorerAddress = "https://cardanoscan.io/address/{addr}",
        ),
        Coin(
            "tron", "TRX", "TRON", 6, 0xFFFF060A, "TRXUSDT",
            listOf(SourceId.TRONGRID),
            Regex("^T[1-9A-HJ-NP-Za-km-z]{33}$"), "T… (34 caractères)",
            explorerAddress = "https://tronscan.org/#/address/{addr}",
        ),
        Coin(
            "chainlink", "LINK", "Chainlink", 18, 0xFF2A5ADA, "LINKUSDT",
            listOf(SourceId.ERC20_RPC),
            evm, "0x… (40 caractères hex)",
            contract = "0x514910771AF9Ca656af840dff83E8264EcF986CA",
            explorerAddress = "https://eth.blockscout.com/address/{addr}",
        ),
        Coin(
            "avalanche-2", "AVAX", "Avalanche", 18, 0xFFE84142, "AVAXUSDT",
            listOf(SourceId.AVAX_RPC),
            evm, "0x… (40 caractères hex)",
            rpc = "https://avalanche-c-chain-rpc.publicnode.com",
            explorerAddress = "https://snowtrace.io/address/{addr}",
        ),
        Coin(
            "stellar", "XLM", "Stellar", 7, 0xFF7D00FF, "XLMUSDT",
            listOf(SourceId.HORIZON),
            Regex("^G[A-Z2-7]{55}$"), "G… (56 caractères)",
            explorerAddress = "https://stellar.expert/explorer/public/account/{addr}",
        ),
        Coin("sui", "SUI", "Sui", 9, 0xFF4DA2FF, "SUIUSDT", emptyList(), evm, "Cours seulement pour l'instant"),
        Coin(
            "litecoin", "LTC", "Litecoin", 8, 0xFFA6A9AA, "LTCUSDT",
            listOf(SourceId.LITECOINSPACE),
            Regex("^(ltc1|[LM3])[a-zA-HJ-NP-Z0-9]{25,62}$"), "ltc1… ou L… / M… / 3…",
            explorerAddress = "https://litecoinspace.org/address/{addr}",
        ),
        Coin(
            "the-open-network", "TON", "Toncoin", 9, 0xFF0098EA, "TONUSDT",
            listOf(SourceId.TONCENTER),
            Regex("^(EQ|UQ|0:)[A-Za-z0-9_\\-]{20,}$"), "EQ… / UQ…",
            explorerAddress = "https://tonviewer.com/{addr}",
        ),
        Coin("polkadot", "DOT", "Polkadot", 10, 0xFFE6007A, "DOTUSDT", emptyList(), Regex("^1[1-9A-HJ-NP-Za-km-z]{46,48}$"), "Cours seulement pour l'instant"),
        Coin("bitcoin-cash", "BCH", "Bitcoin Cash", 8, 0xFF8DC351, "BCHUSDT", emptyList(), Regex("^((bitcoincash:)?[qp])[a-z0-9]{41}$"), "Cours seulement pour l'instant"),
        Coin(
            "shiba-inu", "SHIB", "Shiba Inu", 18, 0xFFFFA409, "SHIBUSDT",
            listOf(SourceId.ERC20_RPC),
            evm, "0x… (40 caractères hex)",
            contract = "0x95aD61b0a150d79219dCF64E1E6Cc01f0B64C4cE",
            explorerAddress = "https://eth.blockscout.com/address/{addr}",
        ),
        Coin(
            "dai", "DAI", "Dai", 18, 0xFFF5AC37, "DAIUSDT",
            listOf(SourceId.ERC20_RPC),
            evm, "0x… (40 caractères hex)",
            contract = "0x6B175474E89094C44Da98b954EedeAC495271d0F",
            explorerAddress = "https://eth.blockscout.com/address/{addr}",
        ),
        Coin(
            "monero", "XMR", "Monero", 12, 0xFFFF6600, null,
            listOf(SourceId.LWS),
            Regex("^[48][0-9AB][1-9A-HJ-NP-Za-km-z]{93}$"), "4… ou 8… (95 caractères)",
            explorerAddress = "https://xmrchain.net/search?value={addr}",
        ),
        Coin(
            "decred", "DCR", "Decred", 8, 0xFF2ED6A1, null,
            listOf(SourceId.DCRDATA),
            Regex("^D[a-zA-Z0-9]{33,35}$"), "D… (34 à 36 caractères)",
            explorerAddress = "https://dcrdata.decred.org/address/{addr}",
        ),
    )

    private val byId = all.associateBy { it.id }

    fun byId(id: String): Coin? = byId[id]

    /** Anciens noms d'enum stockés dans les sauvegardes 1.0. */
    fun fromLegacy(chain: String): Coin? = when (chain) {
        "ETHEREUM" -> byId("ethereum")
        "DECRED" -> byId("decred")
        "MONERO" -> byId("monero")
        else -> byId(chain)
    }

    /** Crypto du top 100 sans source de solde : cours et graphique uniquement. */
    fun priceOnly(id: String, symbol: String, name: String): Coin = Coin(
        id = id,
        symbol = symbol.uppercase(),
        name = name,
        decimals = 8,
        color = colorFor(id),
        binance = null,
        sources = emptyList(),
        pattern = Regex("(?!)"),
        hint = "Cours seulement",
    )

    private fun colorFor(id: String): Long {
        val palette = longArrayOf(
            0xFFF7931A, 0xFF627EEA, 0xFF26A17B, 0xFF23292F, 0xFFF3BA2F, 0xFF14F195,
            0xFF2775CA, 0xFFC2A633, 0xFF0033AD, 0xFFFF060A, 0xFF2A5ADA, 0xFFE84142,
            0xFF7D00FF, 0xFF4DA2FF, 0xFFA6A9AA, 0xFF0098EA, 0xFFE6007A, 0xFF8DC351,
            0xFFFFA409, 0xFFF5AC37, 0xFFFF6600, 0xFF2ED6A1, 0xFF8E44AD, 0xFF16A085,
        )
        return palette[(id.hashCode() and Int.MAX_VALUE) % palette.size]
    }
}
