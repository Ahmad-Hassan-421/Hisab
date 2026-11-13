package com.example.hisab.features.ledger.domain

import com.example.hisab.data.db.HisabEntry
import java.math.BigDecimal
import java.math.RoundingMode

internal object LedgerAmounts {
    private const val SCALE = 3
    private val maxAmount = BigDecimal("1000000000")
    const val AMOUNT_ERROR = "Enter a positive amount up to PKR 1 billion with at most three decimal places."

    data class SplitShare(val debtorId: String, val amount: Double)

    fun isValid(amount: Double): Boolean =
        amount.isFinite() && isValidDecimal(BigDecimal.valueOf(amount))

    fun parseAmount(text: String): Double? =
        text.trim().toBigDecimalOrNull()?.takeIf(::isValidDecimal)?.toDouble()

    fun parseRepaymentAmounts(amounts: Map<String, String>): Map<String, Double>? {
        val parsed = amounts.filterValues { it.isNotBlank() }.mapValues { (_, text) ->
            parseAmount(text) ?: return null
        }
        return parsed.takeIf { it.isNotEmpty() }
    }

    private fun isValidDecimal(amount: BigDecimal): Boolean =
        amount.signum() > 0 && amount <= maxAmount && amount.stripTrailingZeros().scale() <= SCALE

    fun split(amount: Double, members: Int): List<Double> {
        require(isValid(amount) && members > 0)
        val units = BigDecimal.valueOf(amount).movePointRight(SCALE).longValueExact()
        val base = units / members
        val extra = units % members
        return List(members) { index ->
            val share = base + if (index < extra) 1 else 0
            BigDecimal.valueOf(share, SCALE).toDouble()
        }
    }

    /**
     * Splits a total shared expense [amount] paid by [creditorId] among all [members] of the group.
     * Every member (including [creditorId]) bears an equal share of the expense.
     * The payer [creditorId] absorbs their own share; each other member owes their share to [creditorId].
     * Returns the list of [SplitShare] entries for all other members, or null if the amount is too small
     * for every member to receive a non-zero share.
     */
    fun splitExpense(amount: Double, creditorId: String, members: List<String>): List<SplitShare>? {
        require(isValid(amount))
        val distinctMembers = members.distinct()
        require(creditorId in distinctMembers && distinctMembers.size > 1)
        val allShares = split(amount, distinctMembers.size)
        if (allShares.any { it == 0.0 }) return null
        val sharesByMember = distinctMembers.zip(allShares).toMap()
        return distinctMembers.filter { it != creditorId }.map { debtorId ->
            SplitShare(debtorId, sharesByMember.getValue(debtorId))
        }
    }

    fun netByMember(entries: List<HisabEntry>): Map<String, Map<String, BigDecimal>> {
        val balancesByMember = mutableMapOf<String, MutableMap<String, BigDecimal>>()
        val legacyByMember = mutableMapOf<String, MutableMap<String, BigDecimal>>()
        fun MutableMap<String, MutableMap<String, BigDecimal>>.add(from: String, to: String, amount: BigDecimal) {
            val balances = getOrPut(from) { mutableMapOf() }
            balances[to] = balances.getOrDefault(to, BigDecimal.ZERO) + amount
        }
        for (entry in entries) {
            val amount = BigDecimal.valueOf(entry.amount)
            val balances = if (amount.stripTrailingZeros().scale() > SCALE) legacyByMember else balancesByMember
            balances.add(entry.debtorId, entry.creditorId, amount)
            balances.add(entry.creditorId, entry.debtorId, -amount)
        }
        // Round each legacy subtotal once so a settled fraction cannot affect later transactions.
        for ((member, balances) in legacyByMember) {
            for ((counterparty, amount) in balances) {
                balancesByMember.add(member, counterparty, amount.setScale(SCALE, RoundingMode.HALF_UP))
            }
        }
        return balancesByMember.mapValues { (_, balances) ->
            balances.mapValues { (_, amount) -> amount.setScale(SCALE, RoundingMode.UNNECESSARY) }
        }
    }
}
