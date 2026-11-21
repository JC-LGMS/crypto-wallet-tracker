package com.jclg.cryptowallettracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
    onExportFile: (String) -> Unit,
    onImportFile: () -> Unit,
    onVersionClick: () -> Unit,
    onPickCurrency: () -> Unit,
    onRefreshLogos: () -> Unit,
    onToggleHidden: () -> Unit,
    onToggleChart: (String) -> Unit,
    onMoveChart: (String, Int) -> Unit,
    onMoveWallet: (Wallet, Int) -> Unit,
    onDeleteWallet: (Wallet) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var exportDialog by remember { mutableStateOf(false) }
    var chartPicker by remember { mutableStateOf(false) }
    var chartMenu by remember { mutableStateOf<Coin?>(null) }
    var walletMenu by remember { mutableStateOf<Wallet?>(null) }
    var pendingDelete by remember { mutableStateOf<Wallet?>(null) }
    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState()
    val hidden = state.settings.hideBalances
    val online = rememberOnline()
    val visibleCoins = state.settings.homeCharts.mapNotNull { id ->
        state.catalog.firstOrNull { it.id == id }
    }

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
                state = pullState,
                indicator = {
                    // Visible pendant le geste, masqué dès que la synchro démarre.
                    if (!state.refreshing) {
                        PullToRefreshDefaults.Indicator(
                            state = pullState,
                            isRefreshing = false,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                },
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
                                RefreshIcon(state.refreshing, "Actualiser")
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
                                        text = { Text("Mettre à jour les logos") },
                                        onClick = { menuOpen = false; onRefreshLogos() },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Exporter en fichier") },
                                        onClick = { menuOpen = false; exportDialog = true },
                                        enabled = state.wallets.isNotEmpty(),
                                    )
                                }
                            }
                            IconButton(onClick = onOpenSettings) {
                                Icon(Icons.Default.Settings, contentDescription = "Réglages")
                            }
                        }
                        if (state.refreshing) {
                            val progress = state.syncProgress
                            if (progress != null) {
                                // Déterministe : avance même si les animations système sont à 0.
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                )
                            } else {
                                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
                            }
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

                    if (state.markets.isNotEmpty() && (!online || state.quotesFromCache)) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = "Cours hors ligne",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    (if (online) {
                                        "Cours en cache — la mise à jour a échoué"
                                    } else {
                                        "Hors ligne — derniers cours connus"
                                    }) +
                                        (state.quotesUpdatedAt?.let { " du ${it.dateTimeShort()}" } ?: "") +
                                        ". Tirez vers le bas pour actualiser.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
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
                                onLongClick = { walletMenu = wallet },
                            )
                        }
                    }

                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Marchés",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 14.dp),
                            )
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { chartPicker = true }) {
                                Icon(Icons.Default.Add, contentDescription = "Ajouter un graphique")
                            }
                        }
                        if (visibleCoins.isEmpty()) {
                            Text(
                                "Touchez le + à côté de « Marchés » pour ajouter une crypto.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    items(visibleCoins, key = { "market-${it.id}" }) { coin ->
                        MarketRow(
                            coin = coin,
                            market = state.markets[coin.id],
                            price = state.price(coin.id),
                            currency = state.settings.currency,
                            sparkline = state.markets[coin.id]?.sparkline,
                            onClick = { onOpenPrice(coin.id) },
                            onLongClick = { chartMenu = coin },
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

    if (exportDialog) {
        ExportCodeDialog(
            onConfirm = { code ->
                exportDialog = false
                onExportFile(code)
            },
            onDismiss = { exportDialog = false },
        )
    }

    if (chartPicker) {
        ChartPickerDialog(
            coins = state.catalog,
            selected = state.settings.homeCharts,
            onPick = { coinId ->
                chartPicker = false
                onToggleChart(coinId)
            },
            onDismiss = { chartPicker = false },
        )
    }

    chartMenu?.let { coin ->
        val index = state.settings.homeCharts.indexOf(coin.id)
        LongPressMenu("${coin.name} (${coin.symbol})", onClose = { chartMenu = null }) {
            MenuAction("Monter", Icons.Default.KeyboardArrowUp, enabled = index > 0) {
                chartMenu = null; onMoveChart(coin.id, -1)
            }
            MenuAction(
                "Descendre",
                Icons.Default.KeyboardArrowDown,
                enabled = index in 0 until state.settings.homeCharts.lastIndex,
            ) {
                chartMenu = null; onMoveChart(coin.id, 1)
            }
            MenuAction(
                "Supprimer",
                Icons.Default.Delete,
                color = MaterialTheme.colorScheme.error,
            ) {
                chartMenu = null; onToggleChart(coin.id)
            }
        }
    }

    walletMenu?.let { wallet ->
        val index = state.wallets.indexOfFirst { it.id == wallet.id }
        LongPressMenu(
            wallet.name.ifBlank { wallet.coin?.name ?: "Portefeuille" },
            onClose = { walletMenu = null },
        ) {
            MenuAction("Monter", Icons.Default.KeyboardArrowUp, enabled = index > 0) {
                walletMenu = null; onMoveWallet(wallet, -1)
            }
            MenuAction(
                "Descendre",
                Icons.Default.KeyboardArrowDown,
                enabled = index in 0 until state.wallets.lastIndex,
            ) {
                walletMenu = null; onMoveWallet(wallet, 1)
            }
            MenuAction(
                "Supprimer",
                Icons.Default.Delete,
                color = MaterialTheme.colorScheme.error,
            ) {
                walletMenu = null; pendingDelete = wallet
            }
        }
    }

    pendingDelete?.let { wallet ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { DialogTitle("Supprimer ce portefeuille ?") { pendingDelete = null } },
            text = { Text(wallet.name.ifBlank { wallet.coin?.name ?: "" }) },
            confirmButton = {
                TextButton(onClick = { pendingDelete = null; onDeleteWallet(wallet) }) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
}

@Composable
private fun MenuAction(
    label: String,
    icon: ImageVector,
    enabled: Boolean = true,
    color: Color? = null,
    onClick: () -> Unit,
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        color != null -> color
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = tint)
    }
}

/** Menu d'appui long compact : petite boîte, titre + croix, actions serrées. */
@Composable
private fun LongPressMenu(
    title: String,
    onClose: () -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onClose) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 3.dp,
            modifier = Modifier.width(180.dp),
        ) {
            Column(Modifier.padding(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp),
                    )
                    IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Fermer",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                actions()
            }
        }
    }
}

@Composable
private fun ChartPickerDialog(
    coins: List<Coin>,
    selected: Collection<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val available = coins
        .filter { it.id !in selected && it.name.contains(query.trim(), ignoreCase = true) }
        .sortedBy { it.name }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { DialogTitle("Ajouter un graphique", onDismiss) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Rechercher une crypto") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                when {
                    available.isEmpty() -> Text(
                        if (coins.size == selected.size) "Toutes les cryptos sont déjà sur l'accueil."
                        else "Aucune crypto trouvée.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    else -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(available, key = { it.id }) { coin ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(coin.id) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CoinLogo(coin, null, 26.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "${coin.name} (${coin.symbol})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
    )
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
            .padding(horizontal = 18.dp, vertical = 8.dp),
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
    onLongClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
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
    onLongClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
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
