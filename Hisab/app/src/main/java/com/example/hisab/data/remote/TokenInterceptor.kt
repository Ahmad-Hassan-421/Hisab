package com.example.hisab.data.remote

import com.example.hisab.util.TokenManager
import com.example.hisab.util.SessionManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class TokenInterceptor @Inject constructor(
    private val tokenManager: TokenManager,
    private val sessionManager: SessionManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.encodedPath in setOf("/auth/login", "/auth/register")) return chain.proceed(request)

        val token = tokenManager.getToken() ?: return chain.proceed(request)
        val response = chain.proceed(request.newBuilder().header("Authorization", "Bearer $token").build())
        if (response.code == 401 && tokenManager.clearIfMatching(token)) {
            sessionManager.expire()
        }
        return response
    }
}
