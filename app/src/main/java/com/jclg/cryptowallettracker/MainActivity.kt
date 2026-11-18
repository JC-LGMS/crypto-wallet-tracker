package com.jclg.cryptowallettracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import com.jclg.cryptowallettracker.data.Coins
import com.jclg.cryptowallettracker.data.LockCode
import com.jclg.cryptowallettracker.data.Wallet
import com.jclg.cryptowallettracker.ui.AboutScreen
import com.jclg.cryptowallettracker.ui.AppTheme
import com.jclg.cryptowallettracker.ui.AppViewModel
import com.jclg.cryptowallettracker.ui.ChangelogDialog
import com.jclg.cryptowallettracker.ui.CurrencyDialog
import com.jclg.cryptowallettracker.ui.DetailScreen
import com.jclg.cryptowallettracker.ui.DialogTitle
import com.jclg.cryptowallettracker.ui.EditScreen
import com.jclg.cryptowallettracker.ui.HomeScreen
import com.jclg.cryptowallettracker.ui.ImportCodeDialog
import com.jclg.cryptowallettracker.ui.LockScreen
import com.jclg.cryptowallettracker.ui.PriceScreen
import com.jclg.cryptowallettracker.ui.SettingsScreen
import com.jclg.cryptowallettracker.ui.UiState
import com.jclg.cryptowallettracker.ui.WalletQrDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : FragmentActivity() {

    private val vm: AppViewModel by lazy { ViewModelProvider(this)[AppViewModel::class.java] }
    private var pendingExportCode = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingExportCode = savedInstanceState?.getString(KEY_EXPORT_CODE).orEmpty()
        setContent {
            val state by vm.state.collectAsState()
            AppTheme(state.settings.theme) {
                App(vm, state)
            }
        }
    }

    /** Le code d'export survit à une rotation ou à une mort du processus pendant le sélecteur. */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_EXPORT_CODE, pendingExportCode)
    }

    /** Export JSON : requestCode fixe (les ActivityResultLauncher de Compose provoquaient un crash). */
    fun launchExport(fileName: String, code: String) {
        pendingExportCode = code
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, fileName)
        }
        startActivityForResult(intent, RC_EXPORT)
    }

    fun launchImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain"))
        }
        startActivityForResult(intent, RC_IMPORT)
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                RC_NOTIFICATIONS,
            )
        } else {
            vm.setNotifyBalanceChange(true)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) {
            if (requestCode == RC_EXPORT) pendingExportCode = ""
            return
        }
        val uri = data?.data ?: return
        when (requestCode) {
            RC_EXPORT -> {
                vm.exportTo(uri, pendingExportCode)
                pendingExportCode = ""
            }
            RC_IMPORT -> vm.importFrom(uri)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_NOTIFICATIONS) {
            vm.setNotifyBalanceChange(grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
        }
    }

    /** Vrai si le téléphone a une empreinte, un visage ou un code de déverrouillage. */
    fun canUseDeviceLock(): Boolean =
        BiometricManager.from(this).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /** Déverrouillage par empreinte/visage ou code du téléphone, en plus du PIN ou du schéma. */
    fun promptDeviceUnlock(onSuccess: () -> Unit) {
        if (!canUseDeviceLock()) return
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
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        runCatching { prompt.authenticate(info) }
    }

    private companion object {
        const val RC_EXPORT = 1001
        const val RC_IMPORT = 1002
        const val RC_NOTIFICATIONS = 1003
        const val KEY_EXPORT_CODE = "pendingExportCode"
        val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
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
    val lockEnabled by rememberUpdatedState(state.settings.lockEnabled)
    val canUseDeviceLock = remember(activity) { activity?.canUseDeviceLock() == true }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> vm.sync(force = false)
                Lifecycle.Event.ON_STOP -> if (lockEnabled) unlocked = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = route != "home") {
        route = when {
            route.startsWith("wallet/") -> "home"
            route.startsWith("edit/") && route != "edit/new" -> "wallet/${route.removePrefix("edit/")}"
            else -> "home"
        }
    }

    if (state.settings.lockEnabled && !unlocked) {
        LockScreen(
            type = state.settings.lockType,
            verify = { code ->
                LockCode.verify(code, state.settings.lockSalt, state.settings.lockCodeHash)
            },
            onBiometric = if (canUseDeviceLock) {
                { activity?.promptDeviceUnlock { unlocked = true } }
            } else {
                null
            },
            onUnlocked = { unlocked = true },
        )
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
                onExportFile = { code ->
                    val name = "cryptowallet-v${BuildConfig.VERSION_NAME}-" +
                        SimpleDateFormat("yyyyMMdd", Locale.FRANCE).format(Date()) + ".json"
                    activity?.launchExport(name, code)
                },
                onImportFile = { activity?.launchImport() },
                onVersionClick = { changelogOpen = true },
                onPickCurrency = { currencyOpen = true },
                onRefreshLogos = vm::refreshLogos,
                onToggleHidden = { vm.setHideBalances(!state.settings.hideBalances) },
                onToggleChart = vm::toggleHomeChart,
                onMoveChart = vm::moveChart,
                onMoveWallet = vm::moveWallet,
                onDeleteWallet = vm::delete,
            )

            route == "settings" -> SettingsScreen(
                state = state,
                onBack = { route = "home" },
                onTheme = vm::setTheme,
                onCurrency = vm::setCurrency,
                onPriceSource = vm::setPriceSource,
                onAbout = { route = "about" },
                onVersionClick = { changelogOpen = true },
                onBackgroundSync = vm::setBackgroundSyncHours,
                onNotify = { enabled ->
                    if (enabled) activity?.requestNotificationPermission()
                    else vm.setNotifyBalanceChange(false)
                },
                onLock = { type, code ->
                    vm.setLock(type, code)
                    unlocked = true
                },
                onEtherscanKey = vm::setEtherscanKey,
                onBeaconchainKey = vm::setBeaconchainKey,
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

    if (state.importLocked) {
        ImportCodeDialog(
            error = state.importError,
            onConfirm = vm::importEncrypted,
            onDismiss = vm::cancelImportCode,
        )
    }

    state.importReport?.let { report ->
        AlertDialog(
            onDismissRequest = vm::clearImportReport,
            title = { DialogTitle("Import terminé", vm::clearImportReport) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "${report.added} portefeuille(s) importé(s).",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (report.rejected.isNotEmpty()) {
                        Text(
                            "${report.rejected.size} écarté(s) :",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        report.rejected.forEach { reason ->
                            Text("• $reason", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }

    if (state.syncErrors.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = vm::clearSyncErrors,
            title = { DialogTitle("Synchronisation incomplète", vm::clearSyncErrors) },
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
            confirmButton = {},
        )
    }
}
