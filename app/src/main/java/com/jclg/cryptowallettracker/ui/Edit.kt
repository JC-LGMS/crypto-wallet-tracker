package com.jclg.cryptowallettracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jclg.cryptowallettracker.data.Coin
import com.jclg.cryptowallettracker.data.Coins
import com.jclg.cryptowallettracker.data.SourceId
import com.jclg.cryptowallettracker.data.Wallet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    initial: Wallet?,
    catalog: List<Coin>,
    marketImages: Map<String, String?>,
    onBack: () -> Unit,
    onSave: (Wallet) -> Unit,
) {
    var coinId by rememberSaveable { mutableStateOf(initial?.coinId ?: Coins.all.first().id) }
    var sourceName by rememberSaveable { mutableStateOf(initial?.source?.name.orEmpty()) }
    var address by rememberSaveable { mutableStateOf(initial?.address.orEmpty()) }
    var viewKey by rememberSaveable { mutableStateOf(initial?.viewKey.orEmpty()) }
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var comment by rememberSaveable { mutableStateOf(initial?.comment.orEmpty()) }
    var coinDialog by remember { mutableStateOf(false) }

    val addable = remember(catalog) {
        catalog.filter { it.sources.isNotEmpty() }
            .ifEmpty { Coins.all.filter { it.sources.isNotEmpty() } }
            .sortedBy { it.name }
    }
    val coin = Coins.byId(coinId) ?: addable.firstOrNull() ?: Coins.all.first()
    val sources = coin.sources
    val source: SourceId? = sources.firstOrNull { it.name == sourceName } ?: sources.firstOrNull()
    val addressValid = coin.matchesAddress(address)
    val viewKeyValid = coin.symbol != "XMR" || viewKey.trim().length == 64

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initial == null) "Nouveau portefeuille" else "Modifier") },
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
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Crypto", style = MaterialTheme.typography.labelLarge)
            Surface(
                onClick = { coinDialog = true },
                shape = RoundedCornerShape(14.dp),
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CoinLogo(coin, marketImages[coin.id], 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${coin.name} (${coin.symbol})", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            coin.capabilities,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Changer de crypto")
                }
            }

            if (sources.isEmpty()) {
                Text(
                    "Cette crypto n'a pas encore de source de solde : ajout impossible, " +
                        "son cours reste visible dans les marchés.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Text("Ce qui sera affiché", style = MaterialTheme.typography.labelLarge)
                Text(
                    capabilityText(source),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (sources.size > 1) {
                    Text("Source de données", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        sources.forEach { option ->
                            FilterChip(
                                selected = option == source,
                                onClick = { sourceName = option.name },
                                label = { Text(option.label) },
                            )
                        }
                    }
                    Text(
                        "Source choisie : ${source?.label} — " +
                            if (source?.history == true) "solde + transactions" else "solde uniquement",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "Source : ${source?.label}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Adresse ${coin.symbol}") },
                    singleLine = true,
                    isError = address.isNotBlank() && !addressValid,
                    supportingText = { Text(if (address.isNotBlank() && !addressValid) "Adresse invalide" else coin.hint) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (coin.symbol == "XMR") {
                    OutlinedTextField(
                        value = viewKey,
                        onValueChange = { viewKey = it },
                        label = { Text("Clé d'affichage privée (64 caractères)") },
                        singleLine = true,
                        isError = viewKey.isNotBlank() && !viewKeyValid,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Ascii,
                        ),
                        supportingText = {
                            Text(
                                "Monero ne permet pas de lire un solde depuis l'adresse seule. " +
                                    "La clé d'affichage est envoyée au serveur public lws.monero.fail : " +
                                    "elle permet de voir les montants, pas de les dépenser. " +
                                    "L'adresse doit d'abord être enregistrée sur lightwallet.monero.fail/register.",
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nom (optionnel)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text("Commentaire (optionnel)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    val base = initial ?: Wallet(coinId = coin.id, address = address.trim())
                    onSave(
                        base.copy(
                            coinId = coin.id,
                            source = source,
                            address = address.trim(),
                            viewKey = if (coin.symbol == "XMR") viewKey.trim() else "",
                            name = name.trim(),
                            comment = comment.trim(),
                        ),
                    )
                },
                enabled = sources.isNotEmpty() && addressValid && viewKeyValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text("Enregistrer")
            }
        }
    }

    if (coinDialog) {
        AlertDialog(
            onDismissRequest = { coinDialog = false },
            title = { Text("Choisir une crypto") },
            text = {
                LazyColumn(Modifier.heightIn(max = 430.dp)) {
                    items(addable, key = { it.id }) { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coinDialog = false
                                    if (option.id != coin.id) {
                                        coinId = option.id
                                        sourceName = ""
                                        address = ""
                                        viewKey = ""
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CoinLogo(option, marketImages[option.id], 32.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${option.name} (${option.symbol})",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    option.capabilities,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { coinDialog = false }) { Text("Fermer") }
            },
        )
    }
}

private fun capabilityText(source: SourceId?): String = when {
    source == null -> "Cours seulement"
    source.history -> "Solde et transactions"
    else -> "Solde uniquement (pas d'historique)"
}
