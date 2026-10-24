package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R

object NotificationHelper {

    const val CHANNEL_CLASSES = "channel_classes"
    const val CHANNEL_FEES = "channel_fees"
    const val CHANNEL_GRADINGS = "channel_gradings"
    const val CHANNEL_ANNOUNCEMENTS = "channel_announcements"
    const val CHANNEL_ADMISSIONS = "channel_admissions"

    fun initNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val classChannel = NotificationChannel(
                CHANNEL_CLASSES,
                "Class Schedules & Sessions",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for upcoming sessions, cancellations, and schedule changes"
                enableVibration(true)
            }

            val feeChannel = NotificationChannel(
                CHANNEL_FEES,
                "Fees, Invoices & Payments",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for invoice due dates, receipt verifications, and refunds"
                enableVibration(true)
            }

            val gradingChannel = NotificationChannel(
                CHANNEL_GRADINGS,
                "Belt Gradings & Certifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for assessment submissions, promotion approvals, and certificates"
                enableVibration(true)
            }

            val annChannel = NotificationChannel(
                CHANNEL_ANNOUNCEMENTS,
                "Academy Announcements",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General announcements, tournaments, and holiday notices"
            }

            val admChannel = NotificationChannel(
                CHANNEL_ADMISSIONS,
                "Admissions & Approvals",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Application submissions and admission approval/rejection updates"
            }

            manager.createNotificationChannels(
                listOf(classChannel, feeChannel, gradingChannel, annChannel, admChannel)
            )
        }
    }

    fun showSystemNotification(
        context: Context,
        channelId: String,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap(),
        notificationId: Int = System.currentTimeMillis().toInt()
    ) {
        try {
            initNotificationChannels(context)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                data.forEach { (k, v) -> putExtra(k, v) }
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            val manager = NotificationManagerCompat.from(context)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                manager.notify(notificationId, builder.build())
            }
        } catch (e: Exception) {
            android.util.Log.w("NotificationHelper", "Could not post notification: ${e.message}")
        }
    }
}
