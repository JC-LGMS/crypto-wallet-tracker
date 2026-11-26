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
    /** Cryptos affichées sur l'accueil (avec leur mini-courbe 7 j), dans l'ordre choisi. */
    val homeCharts: List<String> = emptyList(),
    /** Liste de marchés propre au mode leurre ; null = jamais modifiée. */
    val decoyHomeCharts: List<String>? = null,
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
    /** Code saisi au verrouillage pour afficher des portefeuilles factices. */
    val decoyCodeSalt: String = "",
    val decoyCodeHash: String = "",
) {
    val lockEnabled: Boolean get() = lockType != LockType.NONE && lockCodeHash.isNotBlank()
    val decoyEnabled: Boolean get() = decoyCodeHash.isNotBlank()
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * La sauvegarde précédente sert de secours : sans elle, un JSON abîmé rendrait
     * les réglages par défaut et le verrou disparaîtrait sans prévenir.
     */
    fun load(): Settings = runCatching {
        decode(prefs.getString(KEY, "").orEmpty())
    }.recoverCatching {
        decode(prefs.getString(KEY_BACKUP, "").orEmpty())
    }.getOrDefault(Settings())

    /** Vrai pendant une session « leurre » : le worker de fond ne doit rien notifier de visible. */
    fun decoySession(): Boolean = prefs.getBoolean(KEY_DECOY_SESSION, false)

    fun setDecoySession(active: Boolean) {
        prefs.edit().putBoolean(KEY_DECOY_SESSION, active).apply()
    }

    fun save(settings: Settings) {
        val previous = prefs.getString(KEY, null)
        val editor = prefs.edit()
        if (previous != null) editor.putString(KEY_BACKUP, previous)
        editor.putString(KEY, json.encodeToString(settings)).apply()
    }

    private fun decode(text: String): Settings = json.decodeFromString(text)

    private companion object {
        const val KEY = "settings"
        const val KEY_BACKUP = "settings.backup"
        const val KEY_DECOY_SESSION = "decoy.session"
    }
}
