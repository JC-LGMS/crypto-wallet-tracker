package com.jclg.cryptowallettracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jclg.cryptowallettracker.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("À propos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppLogo(72.dp)
            Text("Crypto Wallet Tracker", style = MaterialTheme.typography.headlineSmall)
            Text(
                "v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Développé sur OpenCode via DeepSeek 4.1 Flash 1017 par Jean-Cyril Poiraud",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            Spacer(Modifier.height(8.dp))

            Text("Les principes", style = MaterialTheme.typography.titleMedium)
            Bullet("Aucune clé privée, aucun mot de passe : uniquement des adresses publiques.")
            Bullet("Les données restent sur le téléphone (fichier JSON local) ; l'application ne crée aucun compte.")
            Bullet("Les soldes sont lus dans des API publiques, sans clé, et chaque portefeuille indique sa source.")
            Bullet("Les montants sont convertis à la devise choisie à partir des cours USD et d'un taux de change.")
            Bullet("Monero est en lecture seule via une clé d'affichage : elle ne permet pas de dépenser.")

            Spacer(Modifier.height(8.dp))

            Text("Sources de données", style = MaterialTheme.typography.titleMedium)
            Bullet("Cours et graphiques : CoinGecko (Binance et Kraken pour les longues périodes).")
            Bullet("Soldes : RPC Ethereum/BSC/Avalanche, Blockscout, mempool.space, Blockstream, Ripple, Solana, Dogechain, Koios, TronGrid, Horizon, litecoinspace, toncenter, dcrdata, LWS Monero.")
            Bullet("Aucune garantie de disponibilité : ces services sont gratuits et publics.")
        }
    }
}

@Composable
private fun Bullet(text: String) {
    RowText("•  $text")
}

@Composable
private fun RowText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth(),
    )
}
