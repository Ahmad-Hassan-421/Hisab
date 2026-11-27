package com.hisab.server.features.notifications

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

internal enum class PushResult { ACCEPTED, INVALID_TOKEN, RETRY, REJECTED }
internal interface PushTransport {
    suspend fun send(token: String, packageName: String, data: Map<String, String>): PushResult
}

internal data class FirebaseCredential(val project: String, val email: String, val key: RSAPrivateKey) {
    companion object {
        fun parse(raw: String): FirebaseCredential {
            val value = Json.parseToJsonElement(raw).jsonObject
            fun field(key: String) = requireNotNull(value[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() })
            require(field("type") == "service_account")
            val project = field("project_id")
            require(project == (System.getenv("FIREBASE_PROJECT_ID") ?: "hisab-90ff2"))
            val encoded = field("private_key").lineSequence().filterNot { it.startsWith("-----") }
                .joinToString("").filterNot(Char::isWhitespace)
            val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(encoded))) as RSAPrivateKey
            require(key.modulus.bitLength() >= 2048)
            return FirebaseCredential(project, field("client_email"), key)
        }
    }
    fun assertion(now: Long): String {
        fun encode(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val header = encode("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".toByteArray(Charsets.UTF_8))
        val claims = buildJsonObject {
            put("iss", email); put("scope", "https://www.googleapis.com/auth/firebase.messaging")
            put("aud", "https://oauth2.googleapis.com/token"); put("iat", now); put("exp", now + 3600)
        }
        val body = "$header.${encode(claims.toString().toByteArray(Charsets.UTF_8))}"
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(key); signer.update(body.toByteArray(Charsets.US_ASCII))
        return "$body.${encode(signer.sign())}"
    }
}

internal class FcmTransport(private val credential: FirebaseCredential) : PushTransport {
    private val log = org.slf4j.LoggerFactory.getLogger(FcmTransport::class.java)
    private val client = HttpClient(CIO) {
        install(HttpTimeout) { requestTimeoutMillis = 12_000; connectTimeoutMillis = 5_000; socketTimeoutMillis = 12_000 }
    }
    private val access = Mutex()
    private var cachedToken: String? = null
    private var expiresAt = 0L
    private suspend fun accessToken(): String = access.withLock {
        val now = System.currentTimeMillis()
        cachedToken?.takeIf { now < expiresAt }?.let { return@withLock it }
        val response = client.submitForm("https://oauth2.googleapis.com/token", parameters {
            append("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
            append("assertion", credential.assertion(now / 1000))
        })
        if (response.status != HttpStatusCode.OK) {
            log.warn("Firebase authorization returned status={}", response.status.value)
            error("Firebase authorization unavailable")
        }
        val value = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val token = requireNotNull(value["access_token"]?.jsonPrimitive?.content)
        val seconds = value["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600
        cachedToken = token; expiresAt = now + (seconds - 120).coerceIn(0, 3480) * 1000
        token
    }
    override suspend fun send(token: String, packageName: String, data: Map<String, String>): PushResult {
        val body = buildJsonObject {
            putJsonObject("message") {
                put("token", token)
                putJsonObject("data") { data.forEach { (key, value) -> put(key, value) } }
                putJsonObject("android") { put("priority", "HIGH"); put("ttl", "86400s"); put("restricted_package_name", packageName) }
            }
        }
        val response = client.post("https://fcm.googleapis.com/v1/projects/${credential.project}/messages:send") {
            bearerAuth(accessToken()); contentType(ContentType.Application.Json); setBody(body.toString())
        }
        if (response.status.value in 200..299) return PushResult.ACCEPTED
        val error = runCatching { Json.parseToJsonElement(response.bodyAsText()).jsonObject["error"]?.jsonObject }.getOrNull()
        val codes = error?.get("details")?.jsonArray?.mapNotNull {
            it.jsonObject["errorCode"]?.jsonPrimitive?.contentOrNull
        }.orEmpty()
        if ("UNREGISTERED" in codes) return PushResult.INVALID_TOKEN
        log.warn("Firebase send returned status={}", response.status.value)
        if (response.status.value == 401) access.withLock { expiresAt = 0; cachedToken = null }
        return if (response.status.value in listOf(401, 403, 408, 429) || response.status.value >= 500) PushResult.RETRY else PushResult.REJECTED
    }
}

internal object PushGateway {
    val transport: PushTransport? by lazy {
        runCatching { System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON")?.takeIf { it.isNotBlank() }
            ?.let { FcmTransport(FirebaseCredential.parse(it)) } }.getOrNull()
    }
    val configured: Boolean get() = transport != null
}
