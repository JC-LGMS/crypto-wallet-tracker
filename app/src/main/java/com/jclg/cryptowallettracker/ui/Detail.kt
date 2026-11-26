package com.jclg.cryptowallettracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jclg.cryptowallettracker.R
import com.jclg.cryptowallettracker.data.Balance
import com.jclg.cryptowallettracker.data.Coin
import com.jclg.cryptowallettracker.data.Currency
import com.jclg.cryptowallettracker.data.HistoryState
import com.jclg.cryptowallettracker.data.SourceId
import com.jclg.cryptowallettracker.data.Tx
import com.jclg.cryptowallettracker.data.Wallet
import com.jclg.cryptowallettracker.data.coinAmount
import com.jclg.cryptowallettracker.data.dateFr
import com.jclg.cryptowallettracker.data.dateTimeShort
import com.jclg.cryptowallettracker.data.money
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    wallet: Wallet,
    coin: Coin,
    balance: Balance?,
    fiat: Double?,
    currency: Currency,
    image: String?,
    history: HistoryState?,
    hidden: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShowQr: () -> Unit,
    onRefresh: () -> Unit,
    onLoadHistory: () -> Unit,
    onSourceChange: (SourceId) -> Unit,
    onPickCurrency: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmQr by remember { mutableStateOf(false) }
    var sourceDialog by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState()
    val explorerUrl = coin.explorerUrl(wallet.address)

    LaunchedEffect(wallet.id, wallet.coinId, wallet.address) { onLoadHistory() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(wallet.name.ifBlank { coin.name }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = balance?.loading != true) {
                        RefreshIcon(balance?.loading == true, "Actualiser")
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Modifier")
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error)
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            PullToRefreshBox(
                isRefreshing = balance?.loading == true,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
                state = pullState,
                indicator = { PullIndicator(pullState, balance?.loading == true) },
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Surface(
                            onClick = onPickCurrency,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                CoinLogo(coin, image, 44.dp)
                                Spacer(Modifier.width(14.dp))
                                Column {
                                    Text(
                                        "${coin.name} · ${currency.code}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                    val amount = balance?.amount
                                    if (amount != null) {
                                        Text(
                                            if (hidden) "••••" else "${amount.coinAmount()} ${coin.symbol}",
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                        if (fiat != null) {
                                            Text(
                                                if (hidden) "••••" else fiat.money(currency),
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            )
                                        }
                                    } else if (balance?.error != null) {
                                        Text(balance.error, color = MaterialTheme.colorScheme.error)
                                    } else {
                                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                InfoBlock("Adresse", wallet.address, monospace = true)
                            }
                            IconButton(onClick = { confirmQr = true }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_qr),
                                    contentDescription = "QR code du portefeuille",
                                )
                            }
                        }
                        if (explorerUrl != null) {
                            TextButton(onClick = { uriHandler.openUri(explorerUrl) }) {
                                Text("Voir sur l'explorateur")
                            }
                        }
                    }

                    if (wallet.viewKey.isNotBlank()) {
                        item {
                            InfoBlock(
                                "Clé d'affichage",
                                "renseignée (transmise au serveur LWS pour lire le solde)",
                            )
                        }
                    }
                    if (wallet.comment.isNotBlank()) {
                        item { InfoBlock("Commentaire", wallet.comment) }
                    }

                    item {
                        Surface(
                            onClick = { sourceDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(
                                    "Source des données",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    wallet.sourceOrFirst?.label ?: "Aucune",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    wallet.sourceOrFirst?.let { if (it.history) "Solde + transactions" else "Solde uniquement" }
                                        ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "Dernière synchro : " +
                                        (balance?.updatedAt?.let { it.dateTimeShort() } ?: "jamais"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (coin.sources.size > 1) {
                                    Text(
                                        "Touchez pour changer de source",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            "Historique",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Text(
                            history?.updatedAt?.let { "Dernière synchro : ${it.dateTimeShort()}" } ?: "Pas encore synchronisé",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    when {
                        history == null || history.loading -> item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Chargement…", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        history.error != null -> item {
                            Text(history.error, color = MaterialTheme.colorScheme.error)
                        }
                        history.note != null && history.txs.isEmpty() -> item {
                            Text(
                                history.note,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        history.txs.isEmpty() -> item {
                            Text("Aucune transaction.", style = MaterialTheme.typography.bodyMedium)
                        }
                        else -> {
                            items(history.txs, key = { it.id }) { tx ->
                                TxRow(tx) { uriHandler.openUri(tx.explorerUrl) }
                            }
                            if (history.hasMore) {
                                item {
                                    Text(
                                        "Seules les ${history.txs.size} transactions les plus récentes sont affichées.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            LazyScrollbar(
                state = listState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp),
            )
        }
    }

    if (sourceDialog) {
        AlertDialog(
            onDismissRequest = { sourceDialog = false },
            title = { DialogTitle("Source de données") { sourceDialog = false } },
            text = {
                Column {
                    coin.sources.forEach { source ->
                        Surface(
                            onClick = {
                                sourceDialog = false
                                onSourceChange(source)
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (source == wallet.sourceOrFirst) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(source.label, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    if (source.history) "Solde + transactions" else "Solde uniquement",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }

    if (confirmQr) {
        AlertDialog(
            onDismissRequest = { confirmQr = false },
            title = { DialogTitle("Afficher le QR code ?") { confirmQr = false } },
            text = {
                Text(
                    if (wallet.viewKey.isNotBlank()) {
                        "Ce QR contient l'adresse publique et la clé d'affichage Monero : " +
                            "qui le photographie peut lire toutes les transactions et le solde de ce " +
                            "portefeuille, sans pouvoir les dépenser."
                    } else {
                        "Ce QR contient l'adresse publique de ce portefeuille."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmQr = false; onShowQr() }) { Text("Afficher") }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { DialogTitle("Supprimer ce portefeuille ?") { confirmDelete = false } },
            text = { Text("Le nom et le commentaire seront perdus.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
}

@Composable
private fun InfoBlock(label: String, value: String, monospace: Boolean = false) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectionContainer {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            )
        }
    }
}

@Composable
private fun TxRow(tx: Tx, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tx.timeMillis.dateFr(), style = MaterialTheme.typography.bodySmall)
                Text(
                    "${tx.symbol} · ${tx.id.take(12)}…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val amount = tx.amount
            if (amount != null) {
                Text(
                    (if (amount >= 0) "+" else "−") + abs(amount).coinAmount(),
                    color = if (amount >= 0) Color(0xFF1B7F4C) else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleSmall,
                )
            } else {
                Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
