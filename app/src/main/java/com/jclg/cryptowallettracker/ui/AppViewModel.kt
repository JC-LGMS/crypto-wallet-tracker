package com.jclg.cryptowallettracker.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jclg.cryptowallettracker.data.Balance
import com.jclg.cryptowallettracker.data.BalanceEntry
import com.jclg.cryptowallettracker.data.BalanceSnapshot
import com.jclg.cryptowallettracker.data.Balances
import com.jclg.cryptowallettracker.data.ChartRange
import com.jclg.cryptowallettracker.data.Coin
import com.jclg.cryptowallettracker.data.Coins
import com.jclg.cryptowallettracker.data.Currency
import com.jclg.cryptowallettracker.data.ExportCodec
import com.jclg.cryptowallettracker.data.HistoryState
import com.jclg.cryptowallettracker.data.LockCode
import com.jclg.cryptowallettracker.data.LockType
import com.jclg.cryptowallettracker.data.Market
import com.jclg.cryptowallettracker.data.Markets
import com.jclg.cryptowallettracker.data.PriceSource
import com.jclg.cryptowallettracker.data.Rates
import com.jclg.cryptowallettracker.data.Settings
import com.jclg.cryptowallettracker.data.SettingsStore
import com.jclg.cryptowallettracker.data.SnapshotStore
import com.jclg.cryptowallettracker.data.SourceId
import com.jclg.cryptowallettracker.data.ThemeMode
import com.jclg.cryptowallettracker.data.Wallet
import com.jclg.cryptowallettracker.data.WalletStore
import com.jclg.cryptowallettracker.sync.SyncScheduler
import com.jclg.cryptowallettracker.data.friendly
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.util.Collections

data class UiState(
    val wallets: List<Wallet> = emptyList(),
    val settings: Settings = Settings(),
    val markets: Map<String, Market> = emptyMap(),
    val rates: Rates = Rates(mapOf(Currency.USD to 1.0)),
    val balances: Map<String, Balance> = emptyMap(),
    val history: Map<String, HistoryState> = emptyMap(),
    /** Top 100 des cryptos (catalogue des marchés et des réglages). */
    val catalog: List<Coin> = Coins.all,
    val quotesUpdatedAt: Long? = null,
    val refreshing: Boolean = false,
    val message: String? = null,
    /** Échecs de la dernière synchronisation : donnée + source. */
    val syncErrors: List<String> = emptyList(),
    /** Un import chiffré attend son code. */
    val importLocked: Boolean = false,
    val importError: String? = null,
) {
    fun price(coinId: String): Double? =
        markets[coinId]?.priceUsd?.let { it * rates.rate(settings.currency) }

    /** Somme des portefeuilles disponibles ; `incomplete` si un solde ou un cours manque. */
    fun total(): TotalSummary {
        var sum = 0.0
        var incomplete = false
        for (wallet in wallets) {
            val amount = balances[wallet.id]?.amount
            val price = price(wallet.coinId)
            if (amount == null || price == null) {
                incomplete = true
            } else {
                sum += amount * price
            }
        }
        return TotalSummary(sum, incomplete)
    }

    val loading: Boolean get() = refreshing
}

data class TotalSummary(val value: Double, val incomplete: Boolean)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = WalletStore(app)
    private val settingsStore = SettingsStore(app)
    private val snapshotStore = SnapshotStore(app)
    private val _state = MutableStateFlow(
        UiState(wallets = store.load(), settings = settingsStore.load()),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var quotesLoading = false
    private var pendingImport: String? = null

    init {
        val saved = snapshotStore.load()
        if (saved.balances.isNotEmpty()) {
            _state.update { s ->
                s.copy(
                    balances = s.balances + saved.balances.mapValues { (_, entry) ->
                        Balance(amount = entry.amount, updatedAt = entry.updatedAt)
                    },
                )
            }
        }
        SyncScheduler.apply(app, _state.value.settings.backgroundSyncHours)
        sync()
    }

    fun coin(id: String): Coin? = _state.value.catalog.firstOrNull { it.id == id } ?: Coins.byId(id)

    /**
     * Synchronise les données périmées (24 h) ou tout si [force].
     * Les anciennes valeurs restent affichées tant qu'une synchro n'a pas réussi.
     */
    fun sync(force: Boolean = false) {
        val now = System.currentTimeMillis()
        val snapshot = _state.value
        val wallets = snapshot.wallets

        val quotesStale = force || snapshot.quotesUpdatedAt.let { it == null || now - it > QUOTES_TTL }
        val toRefresh = wallets.filter { wallet ->
            val balance = snapshot.balances[wallet.id]
            balance?.loading != true &&
                (force || balance?.updatedAt.let { it == null || now - it > WALLET_TTL })
        }
        if (!quotesStale && toRefresh.isEmpty()) return

        _state.update { s ->
            s.copy(
                refreshing = true,
                syncErrors = if (force) emptyList() else s.syncErrors,
                balances = s.balances + toRefresh.associate { wallet ->
                    wallet.id to (s.balances[wallet.id] ?: Balance()).copy(loading = true, error = null)
                },
            )
        }

        val failures = Collections.synchronizedList(mutableListOf<String>())

        viewModelScope.launch {
            supervisorScope {
                if (quotesStale) launch { fetchQuotes(force, failures) }
                toRefresh.forEach { wallet ->
                    launch { fetchBalance(wallet, failures) }
                }
            }
            _state.update { s ->
                s.copy(
                    refreshing = false,
                    syncErrors = (s.syncErrors + failures).distinct(),
                )
            }
            persistSnapshot()
        }
    }

    /** Pull-to-refresh d'un portefeuille : solde + historique. */
    fun refreshWallet(wallet: Wallet, force: Boolean = true) {
        val failures = Collections.synchronizedList(mutableListOf<String>())
        _state.update { s ->
            s.copy(
                refreshing = true,
                balances = s.balances + (
                    wallet.id to (s.balances[wallet.id] ?: Balance()).copy(loading = true, error = null)
                    ),
            )
        }
        viewModelScope.launch {
            fetchBalance(wallet, failures)
            loadHistory(wallet, force)
            _state.update { s ->
                s.copy(
                    refreshing = false,
                    syncErrors = (s.syncErrors + failures).distinct(),
                )
            }
            persistSnapshot()
        }
    }

    private suspend fun fetchQuotes(force: Boolean, failures: MutableList<String>) {
        if (quotesLoading) return
        quotesLoading = true
        val source = _state.value.settings.priceSource
        runCatching { Markets.snapshot(source, force) }
            .onSuccess { snapshot ->
                _state.update {
                    it.copy(
                        markets = snapshot.markets,
                        rates = snapshot.rates,
                        quotesUpdatedAt = snapshot.fetchedAt,
                        catalog = snapshot.catalog,
                    )
                }
            }
            .onFailure { e ->
                failures.add("Cours, variations et mini-courbes — ${source.label} (${e.friendly()})")
            }
        quotesLoading = false
    }

    private suspend fun fetchBalance(wallet: Wallet, failures: MutableList<String>) {
        val settings = _state.value.settings
        val result = runCatching {
            Balances.balance(wallet, settings.etherscanKey, settings.beaconchainKey)
        }
        _state.update { s ->
            val previous = s.balances[wallet.id] ?: Balance()
            val balance = result.fold(
                onSuccess = {
                    previous.copy(
                        amount = it,
                        loading = false,
                        error = null,
                        updatedAt = System.currentTimeMillis(),
                    )
                },
                onFailure = { previous.copy(loading = false, error = it.friendly()) },
            )
            s.copy(balances = s.balances + (wallet.id to balance))
        }
        result.exceptionOrNull()?.let { e ->
            val coin = wallet.coin?.symbol ?: wallet.coinId
            val source = wallet.sourceOrFirst?.label ?: "source inconnue"
            failures.add("Solde $coin — $source (${e.friendly()})")
        }
    }

    /** Rafraîchit les logos (et les cours) puis recharge les images. */
    fun refreshLogos() {
        viewModelScope.launch {
            runCatching { Markets.snapshot(_state.value.settings.priceSource, force = true) }
                .onSuccess { snapshot ->
                    _state.update {
                        it.copy(
                            markets = snapshot.markets,
                            rates = snapshot.rates,
                            quotesUpdatedAt = snapshot.fetchedAt,
                            catalog = snapshot.catalog,
                        )
                    }
                    LogoLoader.clear()
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(syncErrors = (it.syncErrors + "Logos — CoinGecko (${e.friendly()})").distinct())
                    }
                }
        }
    }

    fun upsert(wallet: Wallet) {
        val list = _state.value.wallets
        val index = list.indexOfFirst { it.id == wallet.id }
        persist(if (index >= 0) list.toMutableList().also { it[index] = wallet } else list + wallet)
        sync(force = true)
    }

    fun delete(wallet: Wallet) {
        persist(_state.value.wallets.filterNot { it.id == wallet.id })
        _state.update {
            it.copy(
                balances = it.balances - wallet.id,
                history = it.history - wallet.id,
            )
        }
        sync()
    }

    fun setWalletSource(wallet: Wallet, source: SourceId) {
        val updated = wallet.copy(source = source)
        persist(_state.value.wallets.map { if (it.id == updated.id) updated else it })
        refreshWallet(updated, force = true)
    }

    fun loadHistory(wallet: Wallet, force: Boolean = false) {
        val coin = wallet.coin ?: return
        val source = wallet.sourceOrFirst
        val current = _state.value.history[wallet.id]

        if (source == null) {
            _state.update {
                it.copy(history = it.history + (wallet.id to HistoryState(note = "Aucune source de solde pour ${coin.symbol}.")))
            }
            return
        }
        if (!source.history) {
            val hint = coin.sources.filter { it.history }.joinToString { it.label }
            val note = if (hint.isBlank()) {
                "Historique non disponible pour ${coin.symbol}."
            } else {
                "Historique non disponible via ${source.label}. Sources avec historique : $hint."
            }
            _state.update { it.copy(history = it.history + (wallet.id to HistoryState(note = note))) }
            return
        }

        val fresh = current?.updatedAt?.let { System.currentTimeMillis() - it < WALLET_TTL } == true
        if (!force && fresh) return
        if (current?.loading == true) return

        _state.update {
            it.copy(history = it.history + (wallet.id to (current ?: HistoryState()).copy(loading = true, error = null)))
        }
        viewModelScope.launch {
            val result = runCatching { Balances.history(wallet) }
            _state.update { s ->
                val previous = s.history[wallet.id] ?: HistoryState()
                val history = result.fold(
                    onSuccess = { page ->
                        previous.copy(
                            txs = page.txs,
                            hasMore = page.hasMore,
                            loading = false,
                            error = null,
                            updatedAt = System.currentTimeMillis(),
                        )
                    },
                    onFailure = { previous.copy(loading = false, error = it.friendly()) },
                )
                val errors = result.exceptionOrNull()?.let { e ->
                    (s.syncErrors + "Historique ${coin.symbol} — ${source.label} (${e.friendly()})").distinct()
                } ?: s.syncErrors
                s.copy(history = s.history + (wallet.id to history), syncErrors = errors)
            }
        }
    }

    fun setTheme(mode: ThemeMode) = updateSettings { it.copy(theme = mode) }

    fun setCurrency(currency: Currency) = updateSettings { it.copy(currency = currency) }

    fun setHideBalances(hidden: Boolean) = updateSettings { it.copy(hideBalances = hidden) }

    /** Enregistre un PIN ou un schéma (haché) ; [LockType.NONE] efface le verrou. */
    fun setLock(type: LockType, code: String = "") {
        if (type == LockType.NONE) {
            updateSettings { it.copy(lockType = type, lockSalt = "", lockCodeHash = "") }
            return
        }
        val (salt, hash) = LockCode.hash(code)
        updateSettings { it.copy(lockType = type, lockSalt = salt, lockCodeHash = hash) }
    }

    fun setEtherscanKey(key: String) = updateSettings { it.copy(etherscanKey = key.trim()) }

    fun setBeaconchainKey(key: String) = updateSettings { it.copy(beaconchainKey = key.trim()) }

    fun setNotifyBalanceChange(enabled: Boolean) =
        updateSettings { it.copy(notifyBalanceChange = enabled) }

    fun setBackgroundSyncHours(hours: Int) {
        updateSettings { it.copy(backgroundSyncHours = hours) }
        SyncScheduler.apply(getApplication(), hours)
    }

    fun setPriceSource(source: PriceSource) {
        updateSettings { it.copy(priceSource = source) }
        sync(force = true)
    }

    fun toggleHomeChart(coinId: String) {
        val enabled = coinId !in _state.value.settings.homeCharts
        updateSettings { settings ->
            settings.copy(
                homeCharts = if (enabled) settings.homeCharts + coinId else settings.homeCharts - coinId,
            )
        }
    }

    /** Mémorise la période du graphique pour chaque crypto. */
    fun setChartRange(coinId: String, range: ChartRange) {
        updateSettings { settings ->
            settings.copy(chartRanges = settings.chartRanges + (coinId to range.name))
        }
    }

    fun importText(text: String) {
        runCatching { store.parseImport(text) }
            .onSuccess { imported ->
                val existing = _state.value.wallets
                val added = imported
                    .filter { it.address.isNotBlank() && Coins.byId(it.coinId)?.sources?.isNotEmpty() == true }
                    .filterNot { new -> existing.any { it.coinId == new.coinId && it.address.equals(new.address, true) } }
                if (added.isEmpty()) {
                    showMessage("Aucun nouveau portefeuille à importer")
                    return
                }
                persist(existing + added)
                sync(force = true)
                showMessage("${added.size} portefeuille(s) importé(s)")
            }
            .onFailure { e -> showMessage("Import impossible : ${e.friendly()}") }
    }

    fun exportTo(uri: Uri, code: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val plain = store.exportJson(_state.value.wallets)
                val text = if (code.isBlank()) plain else ExportCodec.encrypt(plain, code)
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(text.toByteArray())
                } ?: error("fichier inaccessible")
            }.onSuccess { showMessage("Export effectué") }
                .onFailure { e -> showMessage("Export impossible : ${e.friendly()}") }
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val text = runCatching {
                getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()
                    ?.use { it.readText() } ?: error("fichier inaccessible")
            }.getOrElse { e ->
                showMessage("Lecture impossible : ${e.friendly()}")
                return@launch
            }
            withContext(Dispatchers.Main) {
                if (ExportCodec.isEncrypted(text)) {
                    pendingImport = text
                    _state.update { it.copy(importLocked = true, importError = null) }
                } else {
                    importText(text)
                }
            }
        }
    }

    fun importEncrypted(code: String) {
        val text = pendingImport ?: return
        runCatching { ExportCodec.decrypt(text, code) }
            .onSuccess { plain ->
                pendingImport = null
                _state.update { it.copy(importLocked = false, importError = null) }
                importText(plain)
            }
            .onFailure { e ->
                _state.update { it.copy(importError = e.message ?: "code incorrect") }
            }
    }

    fun cancelImportCode() {
        pendingImport = null
        _state.update { it.copy(importLocked = false, importError = null) }
    }

    fun showMessage(message: String) {
        _state.update { it.copy(message = message) }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    fun clearSyncErrors() {
        _state.update { it.copy(syncErrors = emptyList()) }
    }

    private fun updateSettings(block: (Settings) -> Settings) {
        val settings = block(_state.value.settings)
        settingsStore.save(settings)
        _state.update { it.copy(settings = settings) }
    }

    private fun persist(wallets: List<Wallet>) {
        store.save(wallets)
        _state.update { it.copy(wallets = wallets) }
    }

    private fun persistSnapshot() {
        val balances = _state.value.balances.mapNotNull { (id, balance) ->
            balance.amount?.let { id to BalanceEntry(it, balance.updatedAt ?: System.currentTimeMillis()) }
        }.toMap()
        snapshotStore.save(BalanceSnapshot(balances, System.currentTimeMillis()))
    }

    private companion object {
        const val QUOTES_TTL = 24 * 3_600_000L
        const val WALLET_TTL = 24 * 3_600_000L
    }
}
