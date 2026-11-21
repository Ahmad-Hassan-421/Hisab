package com.hisab.server.plugins

import com.hisab.server.database.MongoClientProvider
import com.hisab.server.features.accounts.JwtUtil
import com.hisab.server.features.accounts.hisabSessionVersion
import com.hisab.server.features.accounts.accountRole
import com.hisab.server.features.accounts.isActiveAccount
import com.hisab.server.features.accounts.hasAcknowledgedPrivacy
import com.hisab.server.features.accounts.privacyAcknowledgmentRequiredStatus
import com.hisab.server.models.UserDocument
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.http.HttpStatusCode
import org.litote.kmongo.eq
import io.ktor.util.AttributeKey

private val AccountRole = AttributeKey<String>("HisabAccountRole")
private val PrivacyAcknowledged = AttributeKey<Boolean>("HisabPrivacyAcknowledged")

internal val PrivacyAccess = createRouteScopedPlugin("HisabPrivacyAccess") {
    on(AuthenticationChecked) { call ->
        if (call.principal<JWTPrincipal>() != null && !call.privacyAcknowledged) {
            throw RequestFailure(privacyAcknowledgmentRequiredStatus, "Acknowledge the current Privacy Policy to continue")
        }
    }
}

fun Application.configureSecurity() {
    val tokenVerifier = JwtUtil.verifier()
    install(Authentication) {
        jwt("auth-jwt") {
            realm = "Hisab Server"
            verifier(tokenVerifier)
            challenge { _, _ -> call.respondFailure(HttpStatusCode.Unauthorized, "Sign-in required") }
            validate { credential ->
                val userId = credential.payload.getClaim("userId").asString()?.takeIf(String::isNotBlank)
                    ?: return@validate null
                val user = MongoClientProvider.users.findOne(UserDocument::id eq userId) ?: return@validate null
                if (credential.payload.hisabSessionVersion() != user.sessionVersion || !isActiveAccount(user.accountStatus)) {
                    return@validate null
                }
                attributes.put(AccountRole, accountRole(user.role))
                attributes.put(PrivacyAcknowledged, user.hasAcknowledgedPrivacy())
                JWTPrincipal(credential.payload)
            }
        }
    }
}

val ApplicationCall.authenticatedUserId: String
    get() = requireNotNull(principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString())

internal val ApplicationCall.authenticatedAccountRole: String
    get() = attributes[AccountRole]

internal val ApplicationCall.privacyAcknowledged: Boolean
    get() = attributes.getOrNull(PrivacyAcknowledged) == true
