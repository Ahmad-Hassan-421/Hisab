package com.example.hisab.features.ledger.data

import com.example.hisab.features.groups.data.HisabGroupDto
import com.example.hisab.features.profile.data.UserDto

data class PairBalanceDto(val firstId: String, val secondId: String, val amount: String)
data class LedgerOverview(
    val group: HisabGroupDto,
    val self: UserDto,
    val pairs: List<PairBalanceDto>,
    val names: Map<String, String>,
    val entryCount: Long,
    val settled: Boolean,
    val revision: Long
)
