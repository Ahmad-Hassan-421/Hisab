package com.example.hisab.features.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.hisab.R
import com.example.hisab.ui.MainActivity
import com.example.hisab.util.SessionCredentials
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushPublisher @Inject constructor(@ApplicationContext private val context: Context,
    private val tokens: SessionCredentials, private val state: PushState, private val session: com.example.hisab.util.SessionManager) {
    @Synchronized
    fun receive(data: Map<String, String>) {
        val owner = tokens.getUserId()?.takeIf { !tokens.getToken().isNullOrBlank() } ?: return
        val event = PushEvent.parse(data, owner, System.currentTimeMillis()) ?: return
        session.noteActivity(owner, event.groupId)
        val prefs = state.preferences(owner)
        val manager = NotificationManagerCompat.from(context)
        if (!state.enabled(owner) || !prefs.accepts(event.kind) || !manager.areNotificationsEnabled() || state.delivered(owner, event.eventId)) return
        if (Build.VERSION.SDK_INT >= 26) {
            val system = context.getSystemService(NotificationManager::class.java)
            system.createNotificationChannel(NotificationChannel("hisab_activity", "Hisab activity", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Debts, payments, invitations, recovery and app updates" })
        }
        val intent = Intent(context, MainActivity::class.java)
            .setData(Uri.parse("hisab://event/${event.eventId}"))
            .putExtra(PushNavigation.EXTRA_EVENT, Gson().toJson(data))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val generic = NotificationCompat.Builder(context, "hisab_activity").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Hisab update").setContentText("Open Hisab to view your update.").build()
        val title = if (prefs.previews) event.title.ifBlank { "Hisab update" } else "Hisab update"
        val body = if (prefs.previews) event.body else "Open Hisab to view your update."
        val notification = NotificationCompat.Builder(context, "hisab_activity").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(generic)
            .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true).setPriority(NotificationCompat.PRIORITY_HIGH).build()
        try {
            if (tokens.getUserId() != owner || tokens.getToken().isNullOrBlank()) return
            manager.notify(event.eventId, 0, notification)
            state.markDelivered(owner, event.eventId)
        } catch (_: SecurityException) { /* Android permission can change during message handling. */ }
    }
}

@Singleton
class PushNavigation @Inject constructor(private val tokens: SessionCredentials) {
    companion object { const val EXTRA_EVENT = "hisab.notification.event" }
    private val mutable = MutableStateFlow<PushEvent?>(null)
    val pending = mutable.asStateFlow()
    fun receive(intent: Intent?) {
        val json = intent?.getStringExtra(EXTRA_EVENT)?.takeIf { it.length < 8000 } ?: return
        val data = runCatching { Gson().fromJson<Map<String, String>>(json, object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type) }.getOrNull() ?: return
        mutable.value = PushEvent.parse(data, tokens.getUserId(), System.currentTimeMillis())
        intent.removeExtra(EXTRA_EVENT)
    }
    fun consume(event: PushEvent) { mutable.compareAndSet(event, null) }
}
