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
import com.jclg.cryptowallettracker.data.Decoy
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
    /** Progression de la synchro globale : éléments terminés / à faire. */
    val syncDone: Int = 0,
    val syncTotal: Int = 0,
    val message: String? = null,
    /** Échecs de la dernière synchronisation : donnée + source. */
    val syncErrors: List<String> = emptyList(),
    /** Un import chiffré attend son code. */
    val importLocked: Boolean = false,
    val importError: String? = null,
    /** Les cours affichés viennent du disque : la dernière synchro des cours a échoué. */
    val quotesFromCache: Boolean = false,
    /** Résultat détaillé du dernier import. */
    val importReport: ImportReport? = null,
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

    /** Avancement réel, utilisable même quand les animations système sont désactivées. */
    val syncProgress: Float?
        get() = if (syncTotal > 0) (syncDone.toFloat() / syncTotal).coerceIn(0f, 1f) else null
}

data class TotalSummary(val value: Double, val incomplete: Boolean)

/** Portefeuilles importés, et détail de ceux qui ont été écartés (avec la raison). */
data class ImportReport(val added: Int, val rejected: List<String>)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = WalletStore(app)
    private val settingsStore = SettingsStore(app)
    private val snapshotStore = SnapshotStore(app)
    private val decoyStore = WalletStore(app, "decoy-wallets.json")
    private val decoySnapshotStore = SnapshotStore(app, "decoy-snapshot.json")
    private var activeStore = store
    private var activeSnapshotStore = snapshotStore
    private var decoyMode = false
    private val _state = MutableStateFlow(
        UiState(wallets = store.load(), settings = settingsStore.load()),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var quotesLoading = false
    private var activeRefreshes = 0
    private var pendingImport: String? = null

    init {
        val saved = snapshotStore.load()
        if (saved.balances.isNotEmpty() || saved.markets.isNotEmpty()) {
            _state.update { s ->
                s.copy(
                    balances = s.balances + saved.balances.mapValues { (_, entry) ->
                        Balance(amount = entry.amount, updatedAt = entry.updatedAt)
                    },
                    markets = saved.markets.ifEmpty { s.markets },
                    rates = Rates(saved.rates.byCurrency.ifEmpty { s.rates.byCurrency }),
                    quotesUpdatedAt = saved.quotesUpdatedAt ?: s.quotesUpdatedAt,
                )
            }
        }
        SyncScheduler.apply(app, _state.value.settings.backgroundSyncHours)
        sync()
    }

    /**
     * Bascule entre les vraies données et celles du mode leurre.
     * Les fichiers sont séparés : rien des vrais portefeuilles n'est modifié.
     */
    fun setDecoyMode(enabled: Boolean) {
        if (decoyMode == enabled) return
        decoyMode = enabled
        val now = System.currentTimeMillis()
        if (enabled) {
            activeStore = decoyStore
            activeSnapshotStore = decoySnapshotStore
            val wallets = if (decoyStore.exists()) {
                decoyStore.load()
            } else {
                Decoy.wallets.also { decoyStore.save(it) }
            }
            val saved = decoySnapshotStore.load()
            val seeded = wallets.associate { wallet ->
                val entry = saved.balances[wallet.id]
                wallet.id to Balance(
                    amount = entry?.amount ?: Decoy.amounts[wallet.id],
                    updatedAt = entry?.updatedAt ?: now,
                )
            }
            val real = _state.value
            val markets = wallets.fold(real.markets) { acc, wallet ->
                if (wallet.coinId in acc) acc
                else acc + (wallet.coinId to Market(priceUsd = Decoy.prices[wallet.coinId]))
            }
            val stored = settingsStore.load()
            _state.update { s ->
                s.copy(
                    wallets = wallets,
                    settings = stored.copy(
                        homeCharts = stored.decoyHomeCharts ?: Decoy.wallets.map { it.coinId },
                    ),
                    balances = s.balances + seeded,
                    history = s.history + wallets.associate { it.id to HistoryState(updatedAt = now) },
                    markets = markets,
                    refreshing = false,
                    message = null,
                    syncErrors = emptyList(),
                    importLocked = false,
                    importError = null,
                    importReport = null,
                )
            }
        } else {
            activeStore = store
            activeSnapshotStore = snapshotStore
            _state.update { s ->
                s.copy(
                    wallets = store.load(),
                    settings = settingsStore.load(),
                    refreshing = false,
                    message = null,
                    syncErrors = emptyList(),
                    importLocked = false,
                    importError = null,
                    importReport = null,
                )
            }
        }
        viewModelScope.launch { persistSnapshot() }
        sync(force = false)
    }

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
            wallet.id !in Decoy.ids &&
                snapshot.balances[wallet.id]?.loading != true &&
                (force || snapshot.balances[wallet.id]?.updatedAt.let { it == null || now - it > WALLET_TTL })
        }
        if (!quotesStale && toRefresh.isEmpty()) return

        _state.update { s ->
            s.copy(
                syncErrors = if (force) emptyList() else s.syncErrors,
                balances = s.balances + toRefresh.associate { wallet ->
                    wallet.id to (s.balances[wallet.id] ?: Balance()).copy(loading = true, error = null)
                },
                syncDone = 0,
                syncTotal = toRefresh.size + if (quotesStale) 1 else 0,
            )
        }
        beginRefresh()

        val failures = Collections.synchronizedList(mutableListOf<String>())

        viewModelScope.launch {
            try {
                supervisorScope {
                    if (quotesStale) launch {
                        fetchQuotes(force, failures)
                        markProgress()
                    }
                    toRefresh.forEach { wallet ->
                        launch {
                            fetchBalance(wallet, failures)
                            markProgress()
                        }
                    }
                }
                _state.update { s ->
                    s.copy(syncErrors = (s.syncErrors + failures).distinct())
                }
            } finally {
                endRefresh()
            }
            persistSnapshot()
        }
    }

    /** Pull-to-refresh d'un portefeuille : solde + historique. */
    fun refreshWallet(wallet: Wallet, force: Boolean = true) {
        if (wallet.id in Decoy.ids) return
        val failures = Collections.synchronizedList(mutableListOf<String>())
        _state.update { s ->
            s.copy(
                balances = s.balances + (
                    wallet.id to (s.balances[wallet.id] ?: Balance()).copy(loading = true, error = null)
                    ),
                syncDone = 0,
                syncTotal = 1,
            )
        }
        beginRefresh()
        viewModelScope.launch {
            try {
                fetchBalance(wallet, failures)
                markProgress()
                loadHistory(wallet, force)
                _state.update { s ->
                    s.copy(syncErrors = (s.syncErrors + failures).distinct())
                }
            } finally {
                endRefresh()
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
                        quotesFromCache = false,
                    )
                }
            }
            .onFailure { e ->
                failures.add("Cours, variations et mini-courbes — ${source.label} (${e.friendly()})")
                // Cours en cache : on les garde et on le signale sur l'accueil.
                _state.update { if (it.markets.isEmpty()) it else it.copy(quotesFromCache = true) }
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
                            quotesFromCache = false,
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

    /** Déplace un portefeuille de [delta] rangs dans l'ordre de l'accueil. */
    fun moveWallet(wallet: Wallet, delta: Int) {
        val list = _state.value.wallets.toMutableList()
        val from = list.indexOfFirst { it.id == wallet.id }
        val to = from + delta
        if (from < 0 || to !in list.indices) return
        list.add(to, list.removeAt(from))
        persist(list)
    }

    fun setWalletSource(wallet: Wallet, source: SourceId) {
        val updated = wallet.copy(source = source)
        persist(_state.value.wallets.map { if (it.id == updated.id) updated else it })
        refreshWallet(updated, force = true)
    }

    fun loadHistory(wallet: Wallet, force: Boolean = false) {
        if (wallet.id in Decoy.ids) return
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
            updateSettings {
                it.copy(
                    lockType = type,
                    lockSalt = "",
                    lockCodeHash = "",
                    decoyCodeSalt = "",
                    decoyCodeHash = "",
                )
            }
            return
        }
        val previousType = _state.value.settings.lockType
        viewModelScope.launch {
            // PBKDF2 : hors du fil principal, sinon l'interface se fige.
            val (salt, hash) = withContext(Dispatchers.Default) { LockCode.hash(code) }
            updateSettings {
                it.copy(
                    lockType = type,
                    lockSalt = salt,
                    lockCodeHash = hash,
                    // Le code de leurre suit le même type d'entrée que le vrai code.
                    decoyCodeSalt = if (type == previousType) it.decoyCodeSalt else "",
                    decoyCodeHash = if (type == previousType) it.decoyCodeHash else "",
                )
            }
        }
    }

    /** Enregistre (ou efface) le code qui ouvre les portefeuilles factices. */
    fun setDecoyCode(code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) {
            updateSettings { it.copy(decoyCodeSalt = "", decoyCodeHash = "") }
            return
        }
        val settings = _state.value.settings
        if (!settings.lockEnabled) {
            showMessage("Activez d'abord un PIN ou un schéma.")
            return
        }
        viewModelScope.launch {
            val same = withContext(Dispatchers.Default) {
                LockCode.verify(trimmed, settings.lockSalt, settings.lockCodeHash)
            }
            if (same) {
                showMessage("Choisissez un autre code.")
                return@launch
            }
            val (salt, hash) = withContext(Dispatchers.Default) { LockCode.hash(trimmed) }
            updateSettings { it.copy(decoyCodeSalt = salt, decoyCodeHash = hash) }
            showMessage("Code enregistré.")
        }
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
        val updated = if (enabled) {
            _state.value.settings.homeCharts + coinId
        } else {
            _state.value.settings.homeCharts - coinId
        }
        updateSettings {
            if (decoyMode) it.copy(homeCharts = updated, decoyHomeCharts = updated)
            else it.copy(homeCharts = updated)
        }
    }

    /** Déplace une crypto de [delta] rangs parmi les graphiques de l'accueil. */
    fun moveChart(coinId: String, delta: Int) {
        val current = _state.value.settings.homeCharts
        val from = current.indexOf(coinId)
        val to = from + delta
        if (from < 0 || to !in current.indices) return
        val list = current.toMutableList()
        list.add(to, list.removeAt(from))
        updateSettings {
            if (decoyMode) it.copy(homeCharts = list, decoyHomeCharts = list)
            else it.copy(homeCharts = list)
        }
    }

    /** Mémorise la période du graphique pour chaque crypto. */
    fun setChartRange(coinId: String, range: ChartRange) {
        updateSettings { settings ->
            settings.copy(chartRanges = settings.chartRanges + (coinId to range.name))
        }
    }

    fun importText(text: String) {
        runCatching { activeStore.parseImport(text) }
            .onSuccess { payloads ->
                val existing = _state.value.wallets
                val added = mutableListOf<Wallet>()
                val rejected = mutableListOf<String>()
                payloads.forEach { payload ->
                    val wallet = payload.toWallet()
                    val coin = wallet?.coin
                    val reason = when {
                        wallet == null || coin == null -> "crypto inconnue"
                        wallet.address.isBlank() -> "adresse vide"
                        coin.sources.isEmpty() -> "crypto sans source de solde"
                        !coin.matchesAddress(wallet.address) -> "adresse ${coin.symbol} invalide"
                        (existing + added).any {
                            it.coinId == wallet.coinId && it.address.equals(wallet.address, true)
                        } -> "déjà présent"
                        else -> null
                    }
                    if (wallet != null && reason == null) {
                        added += wallet
                    } else {
                        val label = "${coin?.symbol ?: payload.c} ${payload.a.trim().take(12)}…"
                        rejected += "$label : $reason"
                    }
                }
                if (added.isNotEmpty()) {
                    persist(existing + added)
                    sync(force = true)
                }
                _state.update { it.copy(importReport = ImportReport(added.size, rejected)) }
            }
            .onFailure { e -> showMessage("Import impossible : ${e.friendly()}") }
    }

    fun clearImportReport() {
        _state.update { it.copy(importReport = null) }
    }

    fun exportTo(uri: Uri, code: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val plain = activeStore.exportJson(_state.value.wallets)
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
        viewModelScope.launch {
            // PBKDF2 : hors du fil principal, sinon l'interface se fige.
            val result = withContext(Dispatchers.Default) { runCatching { ExportCodec.decrypt(text, code) } }
            result.onSuccess { plain ->
                pendingImport = null
                _state.update { it.copy(importLocked = false, importError = null) }
                importText(plain)
            }.onFailure { e ->
                _state.update { it.copy(importError = e.message ?: "code incorrect") }
            }
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

    private fun beginRefresh() {
        activeRefreshes++
        _state.update { it.copy(refreshing = true) }
    }

    private fun endRefresh() {
        activeRefreshes = (activeRefreshes - 1).coerceAtLeast(0)
        _state.update {
            if (activeRefreshes > 0) {
                it.copy(refreshing = true)
            } else {
                it.copy(refreshing = false, syncDone = 0, syncTotal = 0)
            }
        }
    }

    private fun markProgress() {
        _state.update { it.copy(syncDone = (it.syncDone + 1).coerceAtMost(it.syncTotal)) }
    }

    private fun updateSettings(block: (Settings) -> Settings) {
        val settings = block(_state.value.settings)
        // En mode leurre, la liste de marchés réelle ne doit jamais être écrasée.
        val toSave = if (decoyMode) settings.copy(homeCharts = settingsStore.load().homeCharts) else settings
        settingsStore.save(toSave)
        _state.update { it.copy(settings = settings) }
    }

    private fun persist(wallets: List<Wallet>) {
        activeStore.save(wallets)
        _state.update { it.copy(wallets = wallets) }
    }

    private suspend fun persistSnapshot() {
        val s = _state.value
        val ids = s.wallets.mapTo(HashSet()) { it.id }
        val balances = s.balances.mapNotNull { (id, balance) ->
            if (id !in ids) return@mapNotNull null
            balance.amount?.let { id to BalanceEntry(it, balance.updatedAt ?: System.currentTimeMillis()) }
        }.toMap()
        val target = activeSnapshotStore
        // L'écriture (cours inclus) est trop grosse pour le fil principal.
        withContext(Dispatchers.IO) {
            target.save(
                BalanceSnapshot(
                    balances = balances,
                    markets = s.markets,
                    rates = s.rates,
                    quotesUpdatedAt = s.quotesUpdatedAt,
                ),
            )
        }
    }

    private companion object {
        const val QUOTES_TTL = 24 * 3_600_000L
        const val WALLET_TTL = 24 * 3_600_000L
    }
}
