package com.example.hisab.features.ledger.domain

import com.example.hisab.data.db.HisabEntry
import java.math.BigDecimal
import java.math.RoundingMode

internal object LedgerAmounts {
    private const val SCALE = 3
    private const val MAX_AMOUNT = 1_000_000_000.0

    fun isValid(amount: Double): Boolean =
        amount.isFinite() && amount > 0 && amount <= MAX_AMOUNT &&
            BigDecimal.valueOf(amount).stripTrailingZeros().scale() <= SCALE

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

    fun netByMember(entries: List<HisabEntry>): Map<String, Map<String, BigDecimal>> {
        val pairs = mutableMapOf<String, MutableMap<String, BigDecimal>>()
        fun add(from: String, to: String, amount: BigDecimal) {
            val balances = pairs.getOrPut(from) { mutableMapOf() }
            balances[to] = balances.getOrDefault(to, BigDecimal.ZERO) + amount
        }
        for (entry in entries) {
            val amount = BigDecimal.valueOf(entry.amount)
            add(entry.debtorId, entry.creditorId, amount)
            add(entry.creditorId, entry.debtorId, amount.negate())
        }
        return pairs.mapValues { (_, balances) ->
            balances.mapValues { (_, amount) -> amount.setScale(SCALE, RoundingMode.HALF_UP) }
        }
    }
}
