package com.example.hisab.data.remote.api

data class CursorPage<T>(val items: List<T>, val nextCursor: String? = null, val revision: Long? = null, val serverTime: Long? = null)
