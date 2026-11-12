package com.example.hisab.features.ledger.domain

import com.example.hisab.data.db.HisabEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun splitExpenseDividesEquallyAmongAllMembersIncludingPayer() {
        val members = listOf("alice", "bob", "charlie")
        val shares = LedgerAmounts.splitExpense(300.0, "alice", members)

        assertEquals(
            listOf(
                LedgerAmounts.SplitShare("bob", 100.0),
                LedgerAmounts.SplitShare("charlie", 100.0)
            ),
            shares
        )

        val entries = shares!!.map { share ->
            entry(share.debtorId, "alice", share.amount)
        }
        val balances = LedgerAmounts.netByMember(entries)

        assertEquals(BigDecimal("-100.000"), balances.getValue("alice").getValue("bob"))
        assertEquals(BigDecimal("-100.000"), balances.getValue("alice").getValue("charlie"))
        assertEquals(BigDecimal("100.000"), balances.getValue("bob").getValue("alice"))
        assertEquals(BigDecimal("100.000"), balances.getValue("charlie").getValue("alice"))
    }

    @Test
    fun splitExpenseInTwoPersonGroupSplitsFiftyFifty() {
        val members = listOf("alice", "bob")
        val shares = LedgerAmounts.splitExpense(100.0, "alice", members)

        assertEquals(
            listOf(LedgerAmounts.SplitShare("bob", 50.0)),
            shares
        )
    }

    @Test
    fun splitExpensePreservesRemainderAcrossAllMembers() {
        val members = listOf("alice", "bob", "charlie")
        val shares = LedgerAmounts.splitExpense(1.0, "alice", members)

        assertEquals(
            listOf(
                LedgerAmounts.SplitShare("bob", 0.333),
                LedgerAmounts.SplitShare("charlie", 0.333)
            ),
            shares
        )

        val payerShare = BigDecimal("1.000") - shares!!.sumOf { BigDecimal.valueOf(it.amount) }
        assertEquals(BigDecimal("0.334"), payerShare)
    }

    @Test
    fun splitExpenseReturnsNullWhenAmountCannotBeSplitWithoutZeroShare() {
        val members = listOf("alice", "bob", "charlie")
        val shares = LedgerAmounts.splitExpense(0.002, "alice", members)
        assertNull(shares)
    }

    private fun entry(debtor: String, creditor: String, amount: Double) =
        HisabEntry(id = "$debtor-$creditor-$amount", groupId = "group", debtorId = debtor, creditorId = creditor, amount = amount)
}
