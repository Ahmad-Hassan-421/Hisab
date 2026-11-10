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

    private val log = LoggerFactory.getLogger(EmailUtil::class.java)
    private val httpClient = lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 5_000
            }
        }
    }

    suspend fun sendWelcomeEmail(toEmail: String, toName: String) {
        val apiKey = System.getenv("BREVO_API_KEY") ?: return
        val from = System.getenv("BREVO_SENDER_EMAIL") ?: return
        try {
            val requestBody = EmailRequest(
                sender = Sender("Hisab App", from),
                to = listOf(Recipient(toName, toEmail)),
                subject = "Welcome to Hisab!",
                htmlContent = "<html><body><h1>Welcome to Hisab!</h1><p>Start tracking your expenses.</p></body></html>"
            )

            val response = httpClient.value.post(BREVO_URL) {
                header("accept", "application/json")
                header("api-key", apiKey)
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
            if (!response.status.isSuccess()) log.warn("Welcome email provider returned HTTP ${response.status.value}")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log.warn("Welcome email could not be sent (${error.javaClass.simpleName})")
        }
    }

    fun close() {
        if (httpClient.isInitialized()) httpClient.value.close()
    }
}
