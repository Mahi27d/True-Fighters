package com.example.service

import android.util.Log
import com.example.data.repository.TrueFightersRepository
import com.example.notification.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TrueFightersMessagingService : FirebaseMessagingService() {

    private val TAG = "TF_MessagingService"
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM Token received: $token")
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null && !currentUser.uid.startsWith("demo_")) {
            serviceScope.launch {
                try {
                    val repo = TrueFightersRepository(applicationContext)
                    repo.registerFcmToken(currentUser.uid, token)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to register new FCM token: ${e.message}")
                }
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM message received from: ${remoteMessage.from}")

        val data = remoteMessage.data
        val notification = remoteMessage.notification

        val title = notification?.title ?: data["title"] ?: "True Fighters Academy"
        val body = notification?.body ?: data["body"] ?: "New notification received."
        val channelId = data["channelId"] ?: NotificationHelper.CHANNEL_ANNOUNCEMENTS

        NotificationHelper.showSystemNotification(
            context = applicationContext,
            channelId = channelId,
            title = title,
            body = body,
            data = data
        )
    }
}
