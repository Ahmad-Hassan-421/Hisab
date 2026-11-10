package com.example.hisab.features.ledger.domain

import com.example.hisab.data.db.HisabEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class LedgerAmountsTest {
    @Test
    fun settlesDecimalAmountsExactly() {
        val entries = listOf(
            entry("a", "b", 0.1),
            entry("a", "b", 0.2),
            entry("b", "a", 0.3)
        )

        val balances = LedgerAmounts.netByMember(entries)

        assertEquals(BigDecimal("0.000"), balances.getValue("a").getValue("b"))
        assertEquals(BigDecimal("0.000"), balances.getValue("b").getValue("a"))
    }

    @Test
    fun splitDistributesTheRemainderAndPreservesTheTotal() {
        val shares = LedgerAmounts.split(1.0, 3)

        assertEquals(listOf(0.334, 0.333, 0.333), shares)
        assertEquals(0, shares.sumOf(BigDecimal::valueOf).compareTo(BigDecimal("1.000")))
    }

    @Test
    fun legacyFractionRemainsReadableAfterRoundedRepayment() {
        val entries = listOf(entry("a", "b", 1.0 / 3), entry("b", "a", 0.333))

        assertEquals(BigDecimal("0.000"), LedgerAmounts.netByMember(entries).getValue("a").getValue("b"))
    }

    @Test
    fun validatesPrecisionRangeAndNonFiniteAmounts() {
        listOf(0.001, 2.345, 1_000_000_000.0).forEach {
            assertTrue("Expected $it to be valid", LedgerAmounts.isValid(it))
        }
        listOf(0.0, -0.1, 0.0009, 3.14159, 1_000_000_000.001, Double.NaN, Double.POSITIVE_INFINITY).forEach {
            assertFalse("Expected $it to be invalid", LedgerAmounts.isValid(it))
        }
    }

    @Test
    fun retainsCounterpartyBalancesWhenMemberNetIsZero() {
        val entries = listOf(entry("a", "b", 4.0), entry("c", "a", 4.0))

        val balances = LedgerAmounts.netByMember(entries).getValue("a")

        assertEquals(BigDecimal("4.000"), balances.getValue("b"))
        assertEquals(BigDecimal("-4.000"), balances.getValue("c"))
    }

    private fun entry(debtor: String, creditor: String, amount: Double) =
        HisabEntry(id = "$debtor-$creditor-$amount", groupId = "group", debtorId = debtor, creditorId = creditor, amount = amount)
}
