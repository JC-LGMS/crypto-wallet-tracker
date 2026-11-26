package com.jclg.cryptowallettracker.data

/**
 * Données du mode leurre : 4 adresses publiques réelles, dont les soldes sont lus comme
 * ceux des autres portefeuilles. Aucune clé n'est détenue : rien ne peut être dépensé.
 * Un solde peut évidemment changer : c'est le vrai solde de l'adresse qui est affiché.
 */
object Decoy {
    val wallets = listOf(
        Wallet(
            id = "decoy-bitcoin", coinId = "bitcoin", source = SourceId.MEMPOOL,
            address = "bc1qq4xt5s3rjxmt8kvd8d3qxtra92nagj88922taw", name = "Épargne",
        ),
        Wallet(
            id = "decoy-ethereum", coinId = "ethereum", source = SourceId.BLOCKSCOUT,
            address = "0x71ee6a81718fe1240cd24f43752145d4cce44e2c", name = "Principal",
        ),
        Wallet(
            id = "decoy-solana", coinId = "solana", source = SourceId.SOLANA,
            address = "83TSSS7qojPowqrrvH23mCrhEJjnJG2ygaf7FEq9ZgKC", name = "Courant",
        ),
        Wallet(
            id = "decoy-ripple", coinId = "ripple", source = SourceId.RIPPLE,
            address = "rUjd8QPJZdhrNWw1cnJkGjoSuxFvQC6dwe", name = "Réserve",
        ),
    )

    /** Adresses factices des versions 1.11 et antérieures, remplacées au premier lancement. */
    private val legacyAddresses = setOf(
        "bc1q" + "0".repeat(38),
        "0x" + "0".repeat(39) + "1",
        "1".repeat(32),
        "r" + "1".repeat(32),
    )

    fun isLegacy(wallet: Wallet): Boolean = wallet.address in legacyAddresses
}
