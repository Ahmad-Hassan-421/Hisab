package com.hisab.server.features.accounts

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import java.net.URI

@Serializable
data class Sender(val name: String, val email: String)
@Serializable
data class Recipient(val name: String, val email: String)
@Serializable
data class EmailRequest(
    val sender: Sender,
    val to: List<Recipient>,
    val subject: String,
    val htmlContent: String
)

object EmailUtil {
    private const val BREVO_URL = "https://api.brevo.com/v3/smtp/email"
    private val endpoint by lazy {
        val override = System.getenv("HISAB_DEV_BREVO_URL")
            ?.takeIf { System.getenv("HISAB_ENV") == "development" }
            ?: return@lazy BREVO_URL
        val parsed = URI(override)
        require(parsed.scheme == "http" && parsed.host == "127.0.0.1" && parsed.userInfo == null &&
            parsed.query == null && parsed.fragment == null) { "Development email sink must use loopback HTTP" }
        override
    }

    private val log = LoggerFactory.getLogger(EmailUtil::class.java)
    private val httpClient = lazy {
        HttpClient(CIO) {
            followRedirects = false
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 5_000
            }
        }
    }

    suspend fun sendWelcomeEmail(toEmail: String, toName: String) {
        send(toEmail, toName, "Welcome to Hisab!",
            "<html><body><h1>Welcome to Hisab!</h1><p>Start tracking your expenses.</p></body></html>")
    }

    fun isConfigured(): Boolean = !System.getenv("BREVO_API_KEY").isNullOrBlank() &&
        !System.getenv("BREVO_SENDER_EMAIL").isNullOrBlank()

    suspend fun sendAccountCode(toEmail: String, code: String, verification: Boolean, minutes: Int): Boolean {
        val title = if (verification) "Verify your Hisab email" else "Reset your Hisab password"
        val steps = if (verification) "Open your Hisab profile, tap Verify recovery email, and paste this code."
            else "Open Hisab, tap Forgot password? then I already have a code, and paste this code."
        val notice = "This code expires in $minutes minutes and can be used once. If you didn’t request it, you can ignore this email."
        return send(toEmail, "Hisab user", title,
            "<html><body><p>Hisab by Hantalyze</p><h1>$title</h1><p>$steps</p><p><code>$code</code></p><p>$notice</p></body></html>")
    }

    private suspend fun send(toEmail: String, toName: String, subject: String, html: String): Boolean {
        val apiKey = System.getenv("BREVO_API_KEY")?.takeIf(String::isNotBlank) ?: return false
        val from = System.getenv("BREVO_SENDER_EMAIL")?.takeIf(String::isNotBlank) ?: return false
        return try {
            val requestBody = EmailRequest(Sender("Hisab by Hantalyze", from), listOf(Recipient(toName, toEmail)), subject, html)

            val response = httpClient.value.post(endpoint) {
                header("accept", "application/json")
                header("api-key", apiKey)
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
            if (!response.status.isSuccess()) log.warn("Email provider returned HTTP {}", response.status.value)
            response.status.isSuccess()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log.warn("Email could not be sent ({})", error.javaClass.simpleName)
            false
        }
    }

    fun close() {
        if (httpClient.isInitialized()) httpClient.value.close()
    }
}
