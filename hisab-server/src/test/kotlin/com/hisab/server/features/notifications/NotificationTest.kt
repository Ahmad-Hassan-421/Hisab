package com.hisab.server.features.notifications

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import java.util.UUID

class NotificationTest {
    @Test fun serviceAccountAssertionsAreSignedAndBoundToTheMessagingAudience() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val raw = buildJsonObject {
            put("type", "service_account"); put("project_id", "hisab-90ff2"); put("client_email", "fixture@example.invalid")
            put("private_key", Base64.getEncoder().encodeToString(pair.private.encoded))
        }.toString()
        val credential = FirebaseCredential.parse(raw)
        val jwt = credential.assertion(1000).split('.')
        val claims = Json.parseToJsonElement(String(Base64.getUrlDecoder().decode(jwt[1]))).jsonObject
        assertEquals("https://oauth2.googleapis.com/token", claims["aud"]!!.jsonPrimitive.content)
        assertEquals("https://www.googleapis.com/auth/firebase.messaging", claims["scope"]!!.jsonPrimitive.content)
        assertEquals(4600, claims["exp"]!!.jsonPrimitive.int)
        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(pair.public); verifier.update("${jwt[0]}.${jwt[1]}".toByteArray(Charsets.US_ASCII))
        assertTrue(verifier.verify(Base64.getUrlDecoder().decode(jwt[2])))
        assertTrue(runCatching { FirebaseCredential.parse("{}") }.isFailure)
    }
    @Test fun previewsAreGenericByDefaultAndEventIdentityIsStableForRetries() {
        val owner = UUID.randomUUID().toString()
        val source = UUID.randomUUID().toString()
        val event = notificationDocument(owner, "DEBT", source, "Private title", "PKR 100 private description", "group", "actor", "entry")
        assertEquals(event.getString("_id"), notificationDocument(owner, "DEBT", source, "Changed title", "Changed body").getString("_id"))
        val data = notificationData(event, NotificationPreferences())
        assertFalse(data.values.any { it.contains("PKR") || it.contains("private description") || it.contains("Private title") })
        assertEquals("PKR 100 private description", notificationData(event, NotificationPreferences(previews = true))["body"])
        assertFalse(NotificationPreferences(debts = false).accepts("DEBT"))
        assertFalse(NotificationPreferences().accepts("arbitrary"))
    }
    @Test fun retryBackoffIsBoundedAndNeverSpins() {
        assertEquals(60_000L, notificationBackoff(0))
        assertEquals(120_000L, notificationBackoff(1))
        assertEquals(6 * 3_600_000L, notificationBackoff(100))
    }
    @Test fun preferenceResponsesAlwaysIncludeAllBooleansIncludingPrivatePreviewDefault() {
        val encoded = Json.parseToJsonElement(Json.encodeToString(NotificationPreferences.serializer(), NotificationPreferences())).jsonObject
        assertEquals(setOf("debts", "payments", "invitations", "recovery", "updates", "previews"), encoded.keys)
        assertFalse(encoded.getValue("previews").jsonPrimitive.boolean)
        assertTrue(encoded.getValue("debts").jsonPrimitive.boolean)
    }
}
