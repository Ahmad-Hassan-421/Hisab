package com.example.hisab.qa

import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONObject

/** Shell-only QA probe: reads this test package's notifications without opening the user's notification shade. */
class PushProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (context.packageName != "com.example.hisab.qa") return
        val id = intent.getStringExtra("eventId")?.takeIf { Regex("^[a-f0-9-]{36}$").matches(it) } ?: return
        val manager = context.getSystemService(NotificationManager::class.java)
        val records = manager.activeNotifications.filter { it.tag == id }
        val notification = records.singleOrNull()?.notification
        resultCode = Activity.RESULT_OK
        resultData = JSONObject().put("delivered", notification != null).put("count", records.size)
            .put("private", notification?.visibility == Notification.VISIBILITY_PRIVATE)
            .put("financialPreview", notification?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.contains("PKR") == true).toString()
        if (notification != null && intent.getBooleanExtra("open", false)) {
            notification.contentIntent.send()
            manager.cancel(id, 0)
        }
    }
}
