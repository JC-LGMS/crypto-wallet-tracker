package com.jclg.cryptowallettracker.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jclg.cryptowallettracker.data.ChartData
import com.jclg.cryptowallettracker.data.ChartRange
import com.jclg.cryptowallettracker.data.Coin
import com.jclg.cryptowallettracker.data.Markets
import com.jclg.cryptowallettracker.data.Point
import com.jclg.cryptowallettracker.data.compactMoney
import com.jclg.cryptowallettracker.data.dateFr
import com.jclg.cryptowallettracker.data.dateShort
import com.jclg.cryptowallettracker.data.dateTimeShort
import com.jclg.cryptowallettracker.data.friendly
import com.jclg.cryptowallettracker.data.money
import com.jclg.cryptowallettracker.data.percent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceScreen(
    coin: Coin,
    state: UiState,
    onBack: () -> Unit,
    onRange: (ChartRange) -> Unit,
) {
    val range = ChartRange.entries
        .firstOrNull { it.name == state.settings.chartRanges[coin.id] }
        ?: ChartRange.D7

    var data by remember(coin.id) { mutableStateOf<ChartData?>(null) }
    var loading by remember(coin.id) { mutableStateOf(true) }
    var refreshing by remember(coin.id) { mutableStateOf(false) }
    var error by remember(coin.id) { mutableStateOf<String?>(null) }
    var selected by remember(coin.id, range) { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val currency = state.settings.currency
    val rate = state.rates.rate(currency)

    fun load(force: Boolean) {
        scope.launch {
            if (data == null) loading = true else refreshing = true
            runCatching { Markets.chart(coin, range, force) }
                .onSuccess {
                    data = it
                    error = null
                }
                .onFailure { e ->
                    // On garde la courbe précédente ; le message précise donnée et source.
                    error = "Graphique ${coin.symbol} ${range.label} — " +
                        "${data?.source ?: "CoinGecko"} : ${e.friendly()}"
                }
            loading = false
            refreshing = false
        }
    }

    LaunchedEffect(coin.id, range) {
        selected = null
        error = null
        load(force = false)
    }

    val display = remember(data, rate) { data?.points?.map { Point(it.time, it.value * rate) }.orEmpty() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CoinLogo(coin, state.markets[coin.id]?.image, 30.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("${coin.name} (${coin.symbol})")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = { load(force = true) }, enabled = !refreshing) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualiser le graphique")
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
            isRefreshing = refreshing,
            onRefresh = { load(force = true) },
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    state.price(coin.id)?.money(currency) ?: "…",
                    style = MaterialTheme.typography.headlineMedium,
                )

                val first = display.firstOrNull()
                val last = display.lastOrNull()
                val change = if (first != null && last != null && first.value > 0) {
                    (last.value - first.value) / first.value * 100
                } else null
                Text(
                    change?.let { "Variation ${it.percent()} sur la période" } ?: "Variation indisponible",
                    style = MaterialTheme.typography.titleSmall,
                    color = when {
                        change == null -> MaterialTheme.colorScheme.onSurfaceVariant
                        change >= 0 -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.error
                    },
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChartRange.entries.forEach { option ->
                        FilterChip(
                            selected = range == option,
                            onClick = { onRange(option) },
                            label = { Text(option.label) },
                        )
                    }
                }

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                when {
                    loading && data == null ->
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)

                    display.isEmpty() -> Text(
                        "Cours indisponible pour cette période.",
                        color = MaterialTheme.colorScheme.error,
                    )

                    else -> {
                        val index = selected?.takeIf { it in display.indices }
                        val shown = index?.let { display[it] }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                shown?.time?.dateFr() ?: "Appuyez sur la courbe",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                (shown?.value ?: last?.value)?.money(currency) ?: "",
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }

                        val max = display.maxOf { it.value }
                        val min = display.minOf { it.value }
                        val mid = (max + min) / 2

                        Row(Modifier.fillMaxWidth().height(230.dp)) {
                            PriceChart(
                                points = display,
                                selectedIndex = index,
                                onSelectIndex = { selected = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(top = 16.dp, bottom = 16.dp, start = 8.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                                horizontalAlignment = Alignment.End,
                            ) {
                                Text(max.compactMoney(currency), style = MaterialTheme.typography.labelSmall)
                                Text(mid.compactMoney(currency), style = MaterialTheme.typography.labelSmall)
                                Text(min.compactMoney(currency), style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(display.first().time.dateShort(), style = MaterialTheme.typography.labelSmall)
                            Text(display[display.size / 2].time.dateShort(), style = MaterialTheme.typography.labelSmall)
                            Text(display.last().time.dateShort(), style = MaterialTheme.typography.labelSmall)
                        }

                        Text(
                            "Source : ${data?.source ?: "CoinGecko"} · ${range.label} · " +
                                "Synchro : ${data?.fetchedAt?.dateTimeShort() ?: "—"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            }
            ColumnScrollbar(
                state = scrollState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp),
            )
        }
    }
}
