package com.example.hisab.features.ledger.data

data class HisabEntryDto(
    val id: String,
    val groupId: String,
    val debtorId: String,
    val creditorId: String,
    val amount: Double,
    val description: String?,
    val timestamp: Long
)

data class CreateEntryRequest(
    val groupId: String,
    val debtorId: String,
    val creditorId: String,
    val amount: Double,
    val description: String,
    val clientEntryId: String
)

data class CreateBatchEntryRequest(
    val entries: List<CreateEntryRequest>
)
