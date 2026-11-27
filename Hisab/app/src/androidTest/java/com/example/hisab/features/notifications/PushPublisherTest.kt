package com.example.hisab.features.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hisab.util.SessionCredentials
import com.example.hisab.util.SessionManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PushPublisherTest {
    @Test fun deliveryIsPrivateDeduplicatedAcrossStoreRestartAndSuppressedAfterAccountSwitch() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val owner = UUID.randomUUID().toString()
        val other = UUID.randomUUID().toString()
        val event = UUID.randomUUID().toString()
        val credentials = object : SessionCredentials {
            var ownerId: String? = owner
            override fun getUserId() = ownerId
            override fun getToken() = "disposable-notification-test"
            override fun clearAll() { ownerId = null }
            override fun cachePrivacyVersion(userId: String, version: String?) {}
            override fun cachedPrivacyVersion(userId: String): String? = null
        }
        val state = PushState(context)
        state.decide(owner, true)
        val data = mapOf("eventId" to event, "ownerId" to owner, "kind" to "DEBT", "title" to "Private financial title",
            "body" to "Private financial PKR 123.456", "groupId" to UUID.randomUUID().toString(),
            "recordId" to UUID.randomUUID().toString(), "sourceId" to UUID.randomUUID().toString(),
            "expiresAt" to (System.currentTimeMillis() + 600_000).toString())
        val system = context.getSystemService(NotificationManager::class.java)
        val publisher = PushPublisher(context, credentials, state, SessionManager())
        try {
            publisher.receive(data); publisher.receive(data)
            val until = SystemClock.elapsedRealtime() + 5000
            while (system.activeNotifications.none { it.tag == event } && SystemClock.elapsedRealtime() < until) SystemClock.sleep(20)
            val posted = system.activeNotifications.filter { it.tag == event }
            assertEquals(1, posted.size)
            assertFalse(posted.single().notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("123.456"))
            assertEquals(Notification.VISIBILITY_PRIVATE, posted.single().notification.visibility)
            val restarted = PushState(context)
            assertTrue(restarted.delivered(owner, event))
            system.cancel(event, 0)
            val cancelledBy = SystemClock.elapsedRealtime() + 5000
            while (system.activeNotifications.any { it.tag == event } && SystemClock.elapsedRealtime() < cancelledBy) SystemClock.sleep(20)
            PushPublisher(context, credentials, restarted, SessionManager()).receive(data)
            instrumentation.waitForIdleSync()
            assertTrue(system.activeNotifications.none { it.tag == event })
            credentials.ownerId = other
            publisher.receive(data + ("eventId" to UUID.randomUUID().toString()))
            assertTrue(system.activeNotifications.isEmpty())
            state.decide(owner, false)
            credentials.ownerId = owner
            publisher.receive(data + ("eventId" to UUID.randomUUID().toString()))
            assertTrue(system.activeNotifications.isEmpty())
        } finally {
            NotificationManagerCompat.from(context).cancelAll()
            state.erase(owner); state.erase(other)
        }
    }
}
