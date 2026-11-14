package com.example.hisab.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import coil.imageLoader
import coil.request.ImageRequest
import com.example.hisab.R
import com.example.hisab.features.invitations.domain.HisabRequest
import com.example.hisab.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InviteNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val failures: FailureReporter
) {
    companion object {
        const val CHANNEL_ID = "hisab_invites_channel"
        const val CHANNEL_NAME = "Hisab Invites"
    }

    private val notificationManager = NotificationManagerCompat.from(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming Hisab group invites"
                enableLights(true)
                enableVibration(true)
            }
            val systemManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            systemManager?.createNotificationChannel(channel)
        }
    }

    fun showInviteNotification(request: HisabRequest) {
        scope.launch {
            try {
                val avatarBitmap = loadAvatarBitmap(
                    request.profilePictureUrl?.takeIf { it.isNotBlank() } ?: request.senderProfilePicUrl
                )
                postNotification(request, avatarBitmap)
            } catch (e: Exception) {
                failures.report("Show invite notification", e)
            }
        }
    }

    private suspend fun loadAvatarBitmap(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return try {
            withTimeoutOrNull(3000L) {
                val fullUrl = Constants.profileImageUrl(url) ?: return@withTimeoutOrNull null
                val imageRequest = ImageRequest.Builder(context)
                    .data(fullUrl)
                    .size(128)
                    .allowHardware(false)
                    .build()
                val result = context.imageLoader.execute(imageRequest)
                (result.drawable as? BitmapDrawable)?.bitmap
            }
        } catch (e: Exception) {
            failures.report("Load notification picture", e)
            null
        }
    }

    private fun postNotification(request: HisabRequest, avatarBitmap: Bitmap?) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "notifications")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            request.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("New Hisab Invite")
            .setContentText("${request.senderName} invited you to join \"${request.groupName}\"")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${request.senderName} has sent you an invite to join \"${request.groupName}\". Open the app to accept or decline.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (avatarBitmap != null) {
            builder.setLargeIcon(avatarBitmap)
        }

        try {
            if (notificationManager.areNotificationsEnabled()) {
                notificationManager.notify(request.id.hashCode(), builder.build())
            }
        } catch (e: SecurityException) {
            failures.report("Post invite notification", e)
        } catch (e: Exception) {
            failures.report("Post invite notification", e)
        }
    }
}
