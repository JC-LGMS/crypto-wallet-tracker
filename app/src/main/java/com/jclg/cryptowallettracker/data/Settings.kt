package com.jclg.cryptowallettracker.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
    val lockEnabled: Boolean = false,
    val hideBalances: Boolean = false,
)

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
