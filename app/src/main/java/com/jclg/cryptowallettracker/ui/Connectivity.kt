package com.jclg.cryptowallettracker.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** Vrai quand le téléphone a une connexion Internet utilisable ; mis à jour en direct. */
@Composable
fun rememberOnline(): Boolean {
    val context = LocalContext.current
    var online by remember { mutableStateOf(isOnline(context)) }
    DisposableEffect(context) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = update()

            override fun onLost(network: Network) = update()

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = update()

            private fun update() {
                val value = isOnline(context)
                context.mainExecutor.execute { online = value }
            }
        }
        runCatching { manager?.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { manager?.unregisterNetworkCallback(callback) } }
    }
    return online
}

private fun isOnline(context: Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return true
    val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
