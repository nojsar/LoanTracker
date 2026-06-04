package com.nojus.loantracker.ui

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

fun formatDate(epochMillis: Long): String {
    if (epochMillis <= 0L) return "—"
    val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    return date.format(dateFormatter)
}

fun formatMoney(amount: Double, currency: String = "EUR"): String {
    val nf = NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
        runCatching { this.currency = Currency.getInstance(currency) }
    }
    return nf.format(amount)
}

fun humanizeUntil(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    val now = System.currentTimeMillis()
    val diff = epochMillis - now
    val past = diff < 0
    val absMs = abs(diff)
    val days = absMs / (1000L * 60 * 60 * 24)
    val hours = (absMs / (1000L * 60 * 60)) % 24
    val pretty = when {
        days >= 1 -> "${days}d ${hours}h"
        else -> "${hours}h"
    }
    return if (past) "$pretty overdue" else "in $pretty"
}

fun todayPlusDays(days: Int): Long =
    LocalDate.now().plusDays(days.toLong())
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
