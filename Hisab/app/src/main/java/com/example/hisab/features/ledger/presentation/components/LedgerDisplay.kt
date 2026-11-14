package com.example.hisab.features.ledger.presentation.components

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

internal fun formatLedgerAmount(amount: BigDecimal, locale: Locale = Locale.getDefault()): String {
    val numbers = NumberFormat.getNumberInstance(locale).apply {
        isGroupingUsed = true
        minimumFractionDigits = 3
        maximumFractionDigits = 3
        roundingMode = RoundingMode.HALF_UP
    }
    return "PKR ${numbers.format(amount)}"
}

internal fun formatLedgerDate(timestamp: Long, locale: Locale = Locale.getDefault()): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(timestamp))
