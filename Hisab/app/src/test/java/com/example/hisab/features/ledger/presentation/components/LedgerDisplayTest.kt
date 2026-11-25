package com.example.hisab.features.ledger.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class LedgerDisplayTest {
    @Test fun ledgerAmountsKeepThousandthsAndReadableGrouping() {
        assertEquals("PKR 1,234.567", formatLedgerAmount(BigDecimal("1234.567"), Locale.US))
        assertEquals("PKR 0.001", formatLedgerAmount(BigDecimal("0.001"), Locale.US))
    }
    @Test fun missingLegacyDatesNeverAppearAsJanuary1970() {
        assertEquals("Date unavailable", formatLedgerDate(0, Locale.US))
        assertEquals("Date unavailable", formatLedgerDate(-1, Locale.US))
    }
}
