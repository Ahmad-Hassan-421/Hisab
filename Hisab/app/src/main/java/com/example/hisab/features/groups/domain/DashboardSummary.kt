package com.example.hisab.features.groups.domain

import java.math.BigDecimal

data class DashboardSummary(val totalDebt: BigDecimal, val totalReceive: BigDecimal, val groupCount: Long, val generatedAt: Long)
