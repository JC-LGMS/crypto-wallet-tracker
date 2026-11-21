package com.jclg.cryptowallettracker.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Petit client HTTP partagé ; toujours appelé depuis Dispatchers.IO. */
object Net {
    private val JSON = "application/json".toMediaType()
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/131.0.0.0 Mobile Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun get(url: String): String = execute(Request.Builder().url(url).get())

    fun postJson(url: String, body: String): String =
        execute(Request.Builder().url(url).post(body.toRequestBody(JSON)))

    private fun execute(builder: Request.Builder): String {
        val request = builder.header("User-Agent", USER_AGENT).build()
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
