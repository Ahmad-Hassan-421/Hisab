package com.hisab.server.features.accounts

internal const val USER_ROLE = "user"
internal const val ADMIN_ROLE = "admin"
internal const val ACTIVE_ACCOUNT = "active"
internal const val SUSPENDED_ACCOUNT = "suspended"

internal fun accountRole(raw: String?): String = if (raw.equals(ADMIN_ROLE, ignoreCase = true)) ADMIN_ROLE else USER_ROLE

internal fun isActiveAccount(status: String?): Boolean = status == null || status == ACTIVE_ACCOUNT
