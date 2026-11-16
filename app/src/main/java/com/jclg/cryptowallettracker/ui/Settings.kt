package com.jclg.cryptowallettracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jclg.cryptowallettracker.BuildConfig
import com.jclg.cryptowallettracker.data.Currency
import com.jclg.cryptowallettracker.data.PriceSource
import com.jclg.cryptowallettracker.data.ThemeMode

private val syncOptions = listOf(0 to "Désactivée", 6 to "6 h", 12 to "12 h", 24 to "24 h")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: UiState,
    onBack: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onCurrency: (Currency) -> Unit,
    onPriceSource: (PriceSource) -> Unit,
    onToggleChart: (String) -> Unit,
    onAbout: () -> Unit,
    onVersionClick: () -> Unit,
    onBackgroundSync: (Int) -> Unit,
    onNotify: (Boolean) -> Unit,
    onLock: (Boolean) -> Unit,
) {
    var currencyDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Réglages") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SectionTitle("Apparence")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.settings.theme == mode,
                            onClick = { onTheme(mode) },
                            label = { Text(mode.label) },
                        )
                    }
                }

                SectionTitle("Devise d'affichage")
                Surface(
                    onClick = { currencyDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(state.settings.currency.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Appliquée à tous les montants et graphiques",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(state.settings.currency.code, style = MaterialTheme.typography.titleMedium)
                    }
                }

                SectionTitle("Source des cours")
                PriceSource.entries.forEach { source ->
                    Surface(
                        onClick = { onPriceSource(source) },
                        shape = RoundedCornerShape(14.dp),
                        tonalElevation = 1.dp,
                        color = if (state.settings.priceSource == source) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(source.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                source.detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                SectionTitle("Sécurité")
                ToggleRow(
                    title = "Verrouillage de l'application",
                    subtitle = "PIN, schéma ou empreinte digitale du téléphone",
                    checked = state.settings.lockEnabled,
                    onChange = onLock,
                )

                SectionTitle("Synchro en arrière-plan")
                Text(
                    "Met à jour les soldes même si l'application est fermée.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    syncOptions.forEach { (hours, label) ->
                        FilterChip(
                            selected = state.settings.backgroundSyncHours == hours,
                            onClick = { onBackgroundSync(hours) },
                            label = { Text(label) },
                        )
                    }
                }
                ToggleRow(
                    title = "Notification de changement de solde",
                    subtitle = "À chaque synchro en arrière-plan",
                    checked = state.settings.notifyBalanceChange,
                    onChange = { enabled -> onNotify(enabled) },
                )

                SectionTitle("Cryptos sur l'accueil")
                Text(
                    "Affiche le cours de la crypto et sa mini-courbe sur 7 jours. Décochez pour la masquer.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.catalog.forEach { coin ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleChart(coin.id) }
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CoinLogo(coin, state.markets[coin.id]?.image, 28.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "${coin.name} (${coin.symbol})",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = coin.id in state.settings.homeCharts,
                            onCheckedChange = { onToggleChart(coin.id) },
                        )
                    }
                }

                SectionTitle("Application")
                Surface(
                    onClick = onAbout,
                    shape = RoundedCornerShape(14.dp),
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("À propos", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                }

                Text(
                    "v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .clickable(onClick = onVersionClick),
                )
            }

            ColumnScrollbar(
                state = scrollState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp),
            )
        }
    }

    if (currencyDialog) {
        CurrencyDialog(
            current = state.settings.currency,
            onPick = {
                currencyDialog = false
                onCurrency(it)
            },
            onDismiss = { currencyDialog = false },
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
