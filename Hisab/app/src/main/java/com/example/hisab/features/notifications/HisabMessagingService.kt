package com.example.hisab.features.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class HisabMessagingService : FirebaseMessagingService() {
    @Inject lateinit var state: PushState
    @Inject lateinit var publisher: PushPublisher
    override fun onNewToken(token: String) {
        state.setToken(token)
        schedulePushSync(applicationContext)
    }
    override fun onMessageReceived(message: RemoteMessage) { publisher.receive(message.data) }
    override fun onDeletedMessages() { schedulePushSync(applicationContext) }
}
