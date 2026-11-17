package com.jclg.cryptowallettracker.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class LockType(val label: String) {
    NONE("Aucun"),
    PIN("PIN"),
    PATTERN("Schéma"),
}

@Serializable
data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val currency: Currency = Currency.EUR,
    val priceSource: PriceSource = PriceSource.COINGECKO,
    /** Cryptos affichées sur l'accueil (avec leur mini-courbe 7 j). */
    val homeCharts: Set<String> = emptySet(),
    /** Période de graphique mémorisée par crypto (nom de ChartRange). */
    val chartRanges: Map<String, String> = emptyMap(),
    /** Synchro de fond toutes les N heures (0 = désactivée). */
    val backgroundSyncHours: Int = 0,
    val notifyBalanceChange: Boolean = false,
    /** Code propre à l'application (PIN ou schéma), stocké salé et haché. */
    val lockType: LockType = LockType.NONE,
    val lockSalt: String = "",
    val lockCodeHash: String = "",
    val etherscanKey: String = "",
    val beaconchainKey: String = "",
    val hideBalances: Boolean = false,
) {
    val lockEnabled: Boolean get() = lockType != LockType.NONE && lockCodeHash.isNotBlank()
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): Settings = runCatching {
        json.decodeFromString<Settings>(prefs.getString(KEY, "")!!)
    }.getOrDefault(Settings())

    fun save(settings: Settings) {
        prefs.edit().putString(KEY, json.encodeToString(settings)).apply()
    }

    private companion object {
        const val KEY = "settings"
    }
}
