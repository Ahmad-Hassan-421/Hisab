package com.hisab.server.database

import com.hisab.server.plugins.RequestFailure
import org.bson.Document
import org.bson.types.Decimal128
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class LedgerAggregateTest {
    @Test fun exactAmountsAndLegacySubtotalsKeepTheSameHalfUpPolicy() {
        fun row(exact: String, legacy: String) = Document("exact", Decimal128(BigDecimal(exact)))
            .append("legacy", Decimal128(BigDecimal(legacy)))
        assertEquals(BigDecimal("1.233"), ledgerNetAmount(row("1.234", "-0.0005")))
        assertEquals(BigDecimal("1.235"), ledgerNetAmount(row("1.234", "0.0005")))
        assertEquals(BigDecimal("-1.235"), ledgerNetAmount(row("-1.234", "-0.0005")))
        assertEquals(BigDecimal("0.000"), ledgerNetAmount(row("0", "0.000499999")))
    }
    @Test(expected = RequestFailure::class)
    fun nonFiniteStoredAmountsNeverBecomeAVisibleBalance() {
        ledgerNetAmount(Document("exact", Decimal128.POSITIVE_INFINITY).append("legacy", Decimal128(0)))
    }
}
