package com.example.hisab.features.ledger.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class LedgerDisplayTest {
    @Test
    fun displaysExactThousandthsWithLocaleGroupingAndTrailingZeroes() {
        val amount = BigDecimal("1234567.120")
        assertEquals("PKR 1,234,567.120", formatLedgerAmount(amount, Locale.US))
        assertEquals("PKR 1.234.567,120", formatLedgerAmount(amount, Locale.GERMANY))
    }

    @Test
    fun roundsLegacyPrecisionForDisplayWithoutChangingTheStoredAmount() {
        val legacy = BigDecimal("1.0025")
        assertEquals("PKR 1.003", formatLedgerAmount(legacy, Locale.US))
        assertEquals(BigDecimal("1.0025"), legacy)
    }
}
