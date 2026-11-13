package com.example.hisab.features.ledger.data

data class HisabEntryDto(
    val id: String,
    val groupId: String,
    val debtorId: String,
    val creditorId: String,
    val amount: Double,
    val description: String?,
    val timestamp: Long,
    val type: String = "EXPENSE"
)

data class CreateEntryRequest(
    val groupId: String,
    val debtorId: String,
    val creditorId: String,
    val amount: Double,
    val description: String,
    val clientEntryId: String,
    val type: String = "EXPENSE"
)

data class CreateBatchEntryRequest(
    val entries: List<CreateEntryRequest>
)
