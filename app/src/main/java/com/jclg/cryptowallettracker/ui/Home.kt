package com.jclg.cryptowallettracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jclg.cryptowallettracker.BuildConfig
import com.jclg.cryptowallettracker.R
import com.jclg.cryptowallettracker.data.Balance
import com.jclg.cryptowallettracker.data.Coin
import com.jclg.cryptowallettracker.data.Currency
import com.jclg.cryptowallettracker.data.Market
import com.jclg.cryptowallettracker.data.Point
import com.jclg.cryptowallettracker.data.Wallet
import com.jclg.cryptowallettracker.data.coinAmount
import com.jclg.cryptowallettracker.data.dateTimeShort
import com.jclg.cryptowallettracker.data.money
import com.jclg.cryptowallettracker.data.percent

private val HeroTop = Color(0xFFE9C88E)
private val HeroBottom = Color(0xFFC48F3D)
private val HeroInk = Color(0xFF2E1D05)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: UiState,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
    onOpenWallet: (String) -> Unit,
    onOpenPrice: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onExportFile: () -> Unit,
    onImportFile: () -> Unit,
    onScanQr: () -> Unit,
    onVersionClick: () -> Unit,
    onPickCurrency: () -> Unit,
    onRefreshLogos: () -> Unit,
    onToggleHidden: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val hidden = state.settings.hideBalances
    val visibleCoins = state.catalog.filter { it.id in state.settings.homeCharts }

    Scaffold { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppLogo(38.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Crypto Wallet Tracker", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Suivi de portefeuilles",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = onRefresh, enabled = !state.refreshing) {
                                Icon(Icons.Default.Refresh, contentDescription = "Actualiser")
                            }
                            Box {
                                IconButton(onClick = { menuOpen = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Plus")
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Importer un fichier") },
                                        onClick = { menuOpen = false; onImportFile() },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Scanner un QR code") },
                                        onClick = { menuOpen = false; onScanQr() },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Mettre à jour les logos") },
                                        onClick = { menuOpen = false; onRefreshLogos() },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Exporter en fichier") },
                                        onClick = { menuOpen = false; onExportFile() },
                                        enabled = state.wallets.isNotEmpty(),
                                    )
                                }
                            }
                            IconButton(onClick = onOpenSettings) {
                                Icon(Icons.Default.Settings, contentDescription = "Réglages")
                            }
                        }
                        if (state.refreshing) {
                            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
                        }
                    }

                    item {
                        val total = state.total()
                        TotalCard(
                            total = total.value,
                            incomplete = total.incomplete,
                            currency = state.settings.currency,
                            hidden = hidden,
                            onToggleHidden = onToggleHidden,
                            onClick = onPickCurrency,
                        )
                    }

                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Mes portefeuilles", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = onAdd) {
                                Icon(Icons.Default.Add, contentDescription = "Ajouter un portefeuille")
                            }
                        }
                    }

                    if (state.wallets.isEmpty()) {
                        item {
                            Text(
                                "Aucun portefeuille pour l'instant. Touchez + pour ajouter une adresse.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    items(state.wallets, key = { it.id }) { wallet ->
                        val coin = wallet.coin
                        if (coin != null) {
                            WalletRow(
                                wallet = wallet,
                                coin = coin,
                                balance = state.balances[wallet.id],
                                image = state.markets[coin.id]?.image,
                                fiat = state.balances[wallet.id]?.amount?.let { amount ->
                                    state.price(coin.id)?.let { amount * it }
                                },
                                currency = state.settings.currency,
                                hidden = hidden,
                                onClick = { onOpenWallet(wallet.id) },
                            )
                        }
                    }

                    item {
                        Text(
                            "Marchés",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                        Text(
                            if (visibleCoins.isEmpty()) {
                                "Choisissez les cryptos à suivre dans les réglages."
                            } else {
                                "Touchez une crypto pour voir son graphique."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    items(visibleCoins, key = { "market-${it.id}" }) { coin ->
                        MarketRow(
                            coin = coin,
                            market = state.markets[coin.id],
                            price = state.price(coin.id),
                            currency = state.settings.currency,
                            sparkline = state.markets[coin.id]?.sparkline,
                            onClick = { onOpenPrice(coin.id) },
                        )
                    }

                    item {
                        Text(
                            "v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 18.dp)
                                .clickable(onClick = onVersionClick),
                        )
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
}

@Composable
private fun TotalCard(
    total: Double,
    incomplete: Boolean,
    currency: Currency,
    hidden: Boolean,
    onToggleHidden: () -> Unit,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(HeroTop, HeroBottom)))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Solde total",
                    style = MaterialTheme.typography.labelLarge,
                    color = HeroInk.copy(alpha = 0.75f),
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onToggleHidden, modifier = Modifier.size(30.dp)) {
                    Icon(
                        painter = painterResource(if (hidden) R.drawable.ic_eye_off else R.drawable.ic_eye),
                        contentDescription = if (hidden) "Afficher les soldes" else "Masquer les soldes",
                        tint = HeroInk,
                    )
                }
                Text(
                    currency.code,
                    style = MaterialTheme.typography.labelLarge,
                    color = HeroInk,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (hidden) "••••" else total.money(currency),
                    style = MaterialTheme.typography.headlineMedium,
                    color = HeroInk,
                    fontWeight = FontWeight.SemiBold,
                )
                if (incomplete) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Total partiel : un portefeuille n'a pas pu être actualisé",
                        tint = HeroInk.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Text(
                if (incomplete) {
                    "Total partiel — un portefeuille est en attente"
                } else {
                    "Touchez pour changer de devise"
                },
                style = MaterialTheme.typography.labelSmall,
                color = HeroInk.copy(alpha = 0.65f),
            )
        }
    }
}

@Composable
private fun WalletRow(
    wallet: Wallet,
    coin: Coin,
    balance: Balance?,
    image: String?,
    fiat: Double?,
    currency: Currency,
    hidden: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            CoinLogo(coin, image, 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    wallet.name.ifBlank { coin.name },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(wallet.sourceOrFirst?.label ?: coin.symbol)
                        balance?.updatedAt?.let { append(" · ").append(it.dateTimeShort()) }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (wallet.comment.isNotBlank()) {
                    Text(
                        wallet.comment,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                when {
                    balance?.amount != null -> {
                        Text(
                            if (hidden) "••••" else "${balance.amount.coinAmount()} ${coin.symbol}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (fiat != null) {
                            Text(
                                if (hidden) "••••" else fiat.money(currency),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    balance?.error != null -> Text(
                        balance.error,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        textAlign = TextAlign.End,
                    )
                    else -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

@Composable
private fun MarketRow(
    coin: Coin,
    market: Market?,
    price: Double?,
    currency: Currency,
    sparkline: List<Double>?,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CoinLogo(coin, market?.image, 30.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(coin.symbol, style = MaterialTheme.typography.bodyLarge)
                Text(
                    coin.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (sparkline != null && sparkline.size > 1) {
                val points = remember(sparkline) {
                    sparkline.mapIndexed { index, value -> Point(index.toLong(), value) }
                }
                PriceChart(
                    points = points,
                    selectedIndex = null,
                    onSelectIndex = {},
                    modifier = Modifier
                        .size(width = 58.dp, height = 24.dp)
                        .padding(end = 8.dp),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(price?.money(currency) ?: "…", style = MaterialTheme.typography.bodyLarge)
                market?.change24h?.let { change ->
                    Text(
                        change.percent(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (change >= 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
