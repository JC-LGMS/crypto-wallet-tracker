package com.jclg.cryptowallettracker.data

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val symbols = DecimalFormatSymbols(Locale.FRANCE)

/** Montant en unités de la crypto (6 décimales max, sans zéros inutiles). */
fun Double.coinAmount(): String {
    val pattern = if (abs(this) >= 1) "#,##0.######" else "0.######"
    return DecimalFormat(pattern, symbols).format(this)
}

fun Double.money(currency: Currency): String = when (currency) {
    Currency.BTC, Currency.ETH ->
        DecimalFormat("#,##0.######", symbols).format(this) + " " + currency.symbol

    Currency.JPY -> DecimalFormat("#,##0", symbols).format(this) + " " + currency.symbol
    else -> DecimalFormat("#,##0.00", symbols).format(this) + " " + currency.symbol
}

fun Double.percent(): String = (if (this >= 0) "+" else "") + DecimalFormat("0.00", symbols).format(this) + " %"

/** Format court pour les axes : 65,1 k€, 1,2 M$, 0,00042 BTC. */
fun Double.compactMoney(currency: Currency): String {
    if (currency.crypto) return DecimalFormat("0.####", symbols).format(this)
    val value = abs(this)
    val number = when {
        value >= 1_000_000 -> DecimalFormat("0.#", symbols).format(this / 1_000_000) + " M"
        value >= 1_000 -> DecimalFormat("0.#", symbols).format(this / 1_000) + " k"
        else -> DecimalFormat("0.##", symbols).format(this)
    }
    return number + currency.symbol
}

fun Long.dateFr(): String =
    if (this <= 0) "—" else SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(this))

fun Long.dateShort(): String =
    if (this <= 0) "—" else SimpleDateFormat("dd/MM/yy", Locale.FRANCE).format(Date(this))

fun Long.dateTimeShort(): String =
    if (this <= 0) "—" else SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE).format(Date(this))

fun parseIso(value: String?): Long =
    if (value == null) 0L else runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L)
