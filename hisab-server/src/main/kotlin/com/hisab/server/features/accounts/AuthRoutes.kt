package com.hisab.server.features.accounts

import com.hisab.server.features.media.DEFAULT_PROFILE_URL

import com.hisab.server.database.MongoClientProvider
import com.hisab.server.models.UserDocument
import com.hisab.server.models.PrivacyAcknowledgment
import com.mongodb.ErrorCategory
import com.mongodb.MongoWriteException
import com.hisab.server.plugins.respondFailure
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.litote.kmongo.eq

@Serializable data class RegisterRequest(val name: String, val email: String, val password: String, val privacyPolicyVersion: String? = null)
@Serializable data class LoginRequest(val email: String, val password: String)
@Serializable data class AuthResponse(val token: String, val user: UserResponse)

private val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
internal fun validAccountEmail(email: String) = email.length <= 254 && emailPattern.matches(email)

fun Route.authRoutes() {
    route("/auth") {
        post("/register") {
            val req = call.receive<RegisterRequest>()
            if (req.name.isBlank() || req.name.length > 120 || !validAccountEmail(req.email) ||
                !PasswordUtil.meetsRequirements(req.password)
            ) return@post call.respondFailure(HttpStatusCode.BadRequest, "Invalid registration")
            val policy = currentPrivacyPolicy
            if (req.privacyPolicyVersion != policy.version) {
                return@post call.respondFailure(privacyAcknowledgmentRequiredStatus, "Review and acknowledge the current Privacy Policy before registering")
            }
            val users = MongoClientProvider.users
            
            if (users.findOne(UserDocument::email eq req.email) != null) {
                call.respondFailure(HttpStatusCode.Conflict, "Email already exists")
                return@post
            }
            
            val uniqueId = UniqueIdUtil.generate(users)
            val time = System.currentTimeMillis()
            
            val newUser = UserDocument(
                name = req.name,
                email = req.email,
                passwordHash = PasswordUtil.hash(req.password),
                uniqueId = uniqueId,
                profilePicUrl = DEFAULT_PROFILE_URL,
                privacyPolicyVersion = policy.version,
                privacyPolicyAcknowledgmentVersion = policy.acknowledgmentVersion,
                privacyPolicyAcceptedAt = time,
                privacyPolicyHistory = listOf(PrivacyAcknowledgment(policy.version, policy.acknowledgmentVersion, time))
            )
            
            try {
                users.insertOne(newUser)
            } catch (error: MongoWriteException) {
                if (error.error.category != ErrorCategory.DUPLICATE_KEY) throw error
                return@post call.respondFailure(HttpStatusCode.Conflict, "Account already exists. Try signing in.")
            }
            val token = JwtUtil.generateToken(newUser)
            
            EmailUtil.sendWelcomeEmail(newUser.email, newUser.name)
            
            call.respond(HttpStatusCode.Created, AuthResponse(token, newUser.toResponse()))
        }

        post("/login") {
            val req = call.receive<LoginRequest>()
            if (req.email.isBlank() || req.email.length > 254 || !PasswordUtil.acceptsInput(req.password)) {
                return@post call.respondFailure(HttpStatusCode.Unauthorized, "Invalid credentials")
            }
            val user = MongoClientProvider.users.findOne(UserDocument::email eq req.email)
            
            if (user != null && PasswordUtil.verify(req.password, user.passwordHash) && isActiveAccount(user.accountStatus)) {
                val token = JwtUtil.generateToken(user)
                call.respond(HttpStatusCode.OK, AuthResponse(token, user.toResponse()))
            } else {
                call.respondFailure(HttpStatusCode.Unauthorized, "Invalid credentials")
            }
        }
    }
}
