package com.jclg.cryptowallettracker.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Petit client HTTP partagé ; toujours appelé depuis Dispatchers.IO. */
object Net {
    private val JSON = "application/json".toMediaType()
    // CoinGecko refuse les clients non navigateur ; les RPC publics refusent un agent de
    // navigateur (Solana répond 403). L'agent dépend donc de l'hôte.
    private const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/131.0.0.0 Mobile Safari/537.36"
    private const val APP_UA = "CryptoWalletTracker (Android)"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun get(url: String): String = execute(Request.Builder().url(url).get(), url)

    fun postJson(url: String, body: String): String =
        execute(Request.Builder().url(url).post(body.toRequestBody(JSON)), url)

    private fun execute(builder: Request.Builder, url: String): String {
        val agent = if (url.contains("coingecko.com")) BROWSER_UA else APP_UA
        val request = builder.header("User-Agent", agent).build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException(response.code, body)
            return body
        }
    }
}

class ApiException(val code: Int, body: String) : Exception("HTTP $code ${body.take(160)}")

/** Message court et lisible pour l'utilisateur. */
fun Throwable.friendly(): String = when (this) {
    is ApiException -> when (code) {
        429 -> "trop de requêtes, réessayez plus tard"
        422 -> "adresse invalide"
        else -> "erreur HTTP $code"
    }
    is java.net.UnknownHostException -> "pas de connexion"
    is java.net.SocketTimeoutException -> "délai dépassé"
    else -> message ?: "erreur inconnue"
}
