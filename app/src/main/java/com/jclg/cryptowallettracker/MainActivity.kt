package com.jclg.cryptowallettracker

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.jclg.cryptowallettracker.data.Coins
import com.jclg.cryptowallettracker.data.Wallet
import com.jclg.cryptowallettracker.ui.AboutScreen
import com.jclg.cryptowallettracker.ui.AppLogo
import com.jclg.cryptowallettracker.ui.AppTheme
import com.jclg.cryptowallettracker.ui.AppViewModel
import com.jclg.cryptowallettracker.ui.ChangelogDialog
import com.jclg.cryptowallettracker.ui.CurrencyDialog
import com.jclg.cryptowallettracker.ui.DetailScreen
import com.jclg.cryptowallettracker.ui.EditScreen
import com.jclg.cryptowallettracker.ui.HomeScreen
import com.jclg.cryptowallettracker.ui.PriceScreen
import com.jclg.cryptowallettracker.ui.SettingsScreen
import com.jclg.cryptowallettracker.ui.UiState
import com.jclg.cryptowallettracker.ui.WalletQrDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val vm: AppViewModel = viewModel()
            val state by vm.state.collectAsState()
            AppTheme(state.settings.theme) {
                App(vm, state)
            }
        }
    }

    fun promptUnlock(onSuccess: () -> Unit) {
        val allowed = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(allowed) != BiometricManager.BIOMETRIC_SUCCESS) {
            // Aucun verrou configuré sur le téléphone : ne pas bloquer l'accès.
            onSuccess()
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Déverrouiller Crypto Wallet Tracker")
            .setAllowedAuthenticators(allowed)
            .build()
        runCatching { prompt.authenticate(info) }
    }
}

@Composable
private fun App(vm: AppViewModel, state: UiState) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val snackbar = remember { SnackbarHostState() }

    var route by rememberSaveable { mutableStateOf("home") }
    var changelogOpen by rememberSaveable { mutableStateOf(false) }
    var currencyOpen by rememberSaveable { mutableStateOf(false) }
    var qrWallet by remember { mutableStateOf<Wallet?>(null) }

    var unlocked by rememberSaveable { mutableStateOf(!state.settings.lockEnabled) }
    var lockTick by remember { mutableIntStateOf(0) }
    val lockEnabled by rememberUpdatedState(state.settings.lockEnabled)

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(vm::exportTo) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(vm::importFrom) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    vm.sync(force = false)
                    lockTick++
                }
                Lifecycle.Event.ON_STOP -> if (lockEnabled) unlocked = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(lockTick, state.settings.lockEnabled) {
        if (state.settings.lockEnabled && !unlocked) {
            activity?.promptUnlock { unlocked = true }
        }
    }

    fun scanQr() {
        runCatching {
            GmsBarcodeScanning.getClient(
                context,
                GmsBarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build(),
            ).startScan()
                .addOnSuccessListener { barcode -> barcode.rawValue?.let(vm::importText) }
                .addOnFailureListener { vm.showMessage("Scan annulé ou indisponible") }
        }.onFailure { vm.showMessage("Scanner indisponible sur cet appareil") }
    }

    BackHandler(enabled = route != "home") {
        route = when {
            route.startsWith("wallet/") -> "home"
            route.startsWith("edit/") && route != "edit/new" -> "wallet/${route.removePrefix("edit/")}"
            else -> "home"
        }
    }

    if (state.settings.lockEnabled && !unlocked) {
        LockScreen(onUnlock = { activity?.promptUnlock { unlocked = true } })
        return
    }

    Box(Modifier.fillMaxSize()) {
        when {
            route == "home" -> HomeScreen(
                state = state,
                onRefresh = { vm.sync(force = true) },
                onAdd = { route = "edit/new" },
                onOpenWallet = { route = "wallet/$it" },
                onOpenPrice = { route = "price/$it" },
                onOpenSettings = { route = "settings" },
                onExportFile = {
                    val name = "cryptowallet-v${BuildConfig.VERSION_NAME}-" +
                        SimpleDateFormat("yyyyMMdd", Locale.FRANCE).format(Date()) + ".json"
                    exportLauncher.launch(name)
                },
                onImportFile = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                onScanQr = ::scanQr,
                onVersionClick = { changelogOpen = true },
                onPickCurrency = { currencyOpen = true },
                onRefreshLogos = vm::refreshLogos,
                onToggleHidden = { vm.setHideBalances(!state.settings.hideBalances) },
            )

            route == "settings" -> SettingsScreen(
                state = state,
                onBack = { route = "home" },
                onTheme = vm::setTheme,
                onCurrency = vm::setCurrency,
                onPriceSource = vm::setPriceSource,
                onToggleChart = vm::toggleHomeChart,
                onAbout = { route = "about" },
                onVersionClick = { changelogOpen = true },
                onBackgroundSync = vm::setBackgroundSyncHours,
                onNotify = vm::setNotifyBalanceChange,
                onLock = { enabled ->
                    vm.setLockEnabled(enabled)
                    if (enabled) unlocked = false
                },
            )

            route == "about" -> AboutScreen(onBack = { route = "settings" })

            route.startsWith("wallet/") -> {
                val wallet = state.wallets.firstOrNull { it.id == route.removePrefix("wallet/") }
                val coin = wallet?.coin
                if (wallet == null || coin == null) {
                    route = "home"
                } else {
                    DetailScreen(
                        wallet = wallet,
                        coin = coin,
                        balance = state.balances[wallet.id],
                        fiat = state.balances[wallet.id]?.amount?.let { amount ->
                            state.price(coin.id)?.let { amount * it }
                        },
                        currency = state.settings.currency,
                        image = state.markets[coin.id]?.image,
                        history = state.history[wallet.id],
                        hidden = state.settings.hideBalances,
                        onBack = { route = "home" },
                        onEdit = { route = "edit/${wallet.id}" },
                        onDelete = { vm.delete(wallet); route = "home" },
                        onShowQr = { qrWallet = wallet },
                        onRefresh = { vm.refreshWallet(wallet, force = true) },
                        onLoadHistory = { vm.loadHistory(wallet) },
                        onSourceChange = { source -> vm.setWalletSource(wallet, source) },
                        onPickCurrency = { currencyOpen = true },
                    )
                }
            }

            route.startsWith("edit/") -> {
                val id = route.removePrefix("edit/")
                val initial = state.wallets.firstOrNull { it.id == id }
                EditScreen(
                    initial = initial,
                    catalog = state.catalog,
                    marketImages = state.markets.mapValues { it.value.image },
                    onBack = {
                        route = if (initial == null || id == "new") "home" else "wallet/${initial.id}"
                    },
                    onSave = { wallet ->
                        vm.upsert(wallet)
                        route = "wallet/${wallet.id}"
                    },
                )
            }

            route.startsWith("price/") -> {
                val coinId = route.removePrefix("price/")
                val coin = state.catalog.firstOrNull { it.id == coinId } ?: Coins.byId(coinId)
                if (coin == null) {
                    route = "home"
                } else {
                    PriceScreen(
                        coin = coin,
                        state = state,
                        onBack = { route = "home" },
                        onRange = { vm.setChartRange(coin.id, it) },
                    )
                }
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    qrWallet?.let { wallet ->
        WalletQrDialog(wallet = wallet, onDismiss = { qrWallet = null })
    }

    if (changelogOpen) {
        ChangelogDialog(onDismiss = { changelogOpen = false })
    }

    if (currencyOpen) {
        CurrencyDialog(
            current = state.settings.currency,
            onPick = {
                currencyOpen = false
                vm.setCurrency(it)
            },
            onDismiss = { currencyOpen = false },
        )
    }

    if (state.syncErrors.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = vm::clearSyncErrors,
            title = { Text("Synchronisation incomplète") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Les données précédentes sont conservées.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.syncErrors.forEach { failure ->
                        Text("• $failure", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = vm::clearSyncErrors) { Text("Fermer") }
            },
        )
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppLogo(72.dp)
            Text("Application verrouillée", style = MaterialTheme.typography.titleLarge)
            Text(
                "Déverrouillez avec votre empreinte ou le code du téléphone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onUnlock) { Text("Déverrouiller") }
        }
    }
}
