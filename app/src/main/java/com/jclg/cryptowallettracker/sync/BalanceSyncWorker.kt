package com.jclg.cryptowallettracker.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jclg.cryptowallettracker.MainActivity
import com.jclg.cryptowallettracker.data.BalanceEntry
import com.jclg.cryptowallettracker.data.BalanceSnapshot
import com.jclg.cryptowallettracker.data.Balances
import com.jclg.cryptowallettracker.data.Markets
import com.jclg.cryptowallettracker.data.SettingsStore
import com.jclg.cryptowallettracker.data.SnapshotStore
import com.jclg.cryptowallettracker.data.WalletStore
import com.jclg.cryptowallettracker.data.coinAmount
import java.util.concurrent.TimeUnit

/** Synchro périodique des soldes, même application fermée. */
class BalanceSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val settings = SettingsStore(context).load()
        val wallets = WalletStore(context).load()
        if (wallets.isEmpty()) return Result.success()

        val store = SnapshotStore(context)
        val previous = store.load()
        val balances = HashMap<String, BalanceEntry>()
        val changes = mutableListOf<String>()

        runCatching { Markets.snapshot(settings.priceSource, force = true) }

        wallets.forEach { wallet ->
            val amount = runCatching {
                Balances.balance(wallet, settings.etherscanKey, settings.beaconchainKey)
            }.getOrNull()
            if (amount == null) return@forEach
            balances[wallet.id] = BalanceEntry(amount, System.currentTimeMillis())
            val old = previous.balances[wallet.id]
            if (old != null && old.amount != amount) {
                val coin = wallet.coin
                val label = wallet.name.ifBlank { coin?.name ?: wallet.coinId }
                changes.add(
                    "$label : ${old.amount.coinAmount()} → ${amount.coinAmount()} ${coin?.symbol.orEmpty()}",
                )
            }
        }

        previous.balances.forEach { (id, entry) -> balances.putIfAbsent(id, entry) }
        store.save(BalanceSnapshot(balances, System.currentTimeMillis()))

        if (changes.isNotEmpty()) {
            if (settings.notifyBalanceChange) notifyChanges(context, changes)
        }
        return Result.success()
    }

    private fun notifyChanges(context: Context, changes: List<String>) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Variations de solde", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Solde modifié")
            .setContentText(changes.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(changes.joinToString("\n")))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    companion object {
        private const val CHANNEL = "balance_changes"
        private const val NOTIFICATION_ID = 42
    }
}

object SyncScheduler {
    private const val WORK_NAME = "balance-sync"

    fun apply(context: Context, hours: Int) {
        val manager = WorkManager.getInstance(context)
        if (hours <= 0) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<BalanceSyncWorker>(hours.toLong(), TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .setInitialDelay(hours.toLong(), TimeUnit.HOURS)
            .build()
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
