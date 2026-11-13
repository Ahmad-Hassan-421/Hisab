package com.hisab.server.plugins

import com.hisab.server.features.accounts.JwtUtil
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.http.HttpStatusCode

fun Application.configureSecurity() {
    val tokenVerifier = JwtUtil.verifier()
    install(Authentication) {
        jwt("auth-jwt") {
            realm = "Hisab Server"
            verifier(tokenVerifier)
            challenge { _, _ -> call.respondFailure(HttpStatusCode.Unauthorized, "Sign-in required") }
            validate { credential ->
                if (!credential.payload.getClaim("userId").asString().isNullOrBlank()) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }
}

val ApplicationCall.authenticatedUserId: String
    get() = requireNotNull(principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString())
