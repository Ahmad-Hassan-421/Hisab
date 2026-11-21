package com.hisab.server.features.accounts

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.Payload
import com.hisab.server.models.UserDocument
import java.util.Date

object JwtUtil {
    private val algorithm by lazy {
        val secret = System.getenv("JWT_SECRET").orEmpty()
        require(secret.toByteArray(Charsets.UTF_8).size >= 32) {
            "JWT_SECRET must contain at least 32 bytes"
        }
        Algorithm.HMAC256(secret)
    }
    const val ISSUER = "hisab-server"
    const val AUDIENCE = "hisab-app"
    private const val EXPIRY_MS = 7L * 24 * 60 * 60 * 1000 // 7 days

    fun generateToken(user: UserDocument): String {
        return JWT.create()
            .withAudience(AUDIENCE)
            .withIssuer(ISSUER)
            .withClaim("userId", user.id)
            .withClaim("role", user.role)
            .withClaim("sessionVersion", user.sessionVersion)
            .withExpiresAt(Date(System.currentTimeMillis() + EXPIRY_MS))
            .sign(algorithm)
    }

    fun verifier() = JWT.require(algorithm)
        .withAudience(AUDIENCE)
        .withIssuer(ISSUER)
        .withClaimPresence("exp")
        .build()
}

internal fun Payload.hisabSessionVersion(): Long = getClaim("sessionVersion").asLong() ?: 0L
