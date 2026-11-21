package com.jclg.cryptowallettracker.data

/** Données factices du mode leurre : 4 portefeuilles et leurs marchés. */
object Decoy {
    val wallets = listOf(
        Wallet(
            id = "decoy-bitcoin", coinId = "bitcoin", source = SourceId.MEMPOOL,
            address = "bc1q" + "0".repeat(38), name = "Épargne",
        ),
        Wallet(
            id = "decoy-ethereum", coinId = "ethereum", source = SourceId.EVM_RPC,
            address = "0x" + "0".repeat(39) + "1", name = "Principal",
        ),
        Wallet(
            id = "decoy-solana", coinId = "solana", source = SourceId.SOLANA,
            address = "1".repeat(32), name = "Courant",
        ),
        Wallet(
            id = "decoy-ripple", coinId = "ripple", source = SourceId.RIPPLE,
            address = "r" + "1".repeat(32), name = "Réserve",
        ),
    )

    val amounts = mapOf(
        "decoy-bitcoin" to 0.0421,
        "decoy-ethereum" to 1.87,
        "decoy-solana" to 24.5,
        "decoy-ripple" to 3_100.0,
    )

    val prices = mapOf(
        "bitcoin" to 56_000.0,
        "ethereum" to 3_000.0,
        "solana" to 140.0,
        "ripple" to 0.55,
    )

    val ids: Set<String> = wallets.mapTo(HashSet()) { it.id }
}
