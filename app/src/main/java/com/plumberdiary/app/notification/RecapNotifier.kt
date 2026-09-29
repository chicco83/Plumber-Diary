// RecapNotifier.kt — v1.8.0 — 2026-09-29
package com.plumberdiary.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.plumberdiary.app.MainActivity
import com.plumberdiary.app.R

/** Notifica serale "Recap pronto" (requisito 3): il tocco apre la schermata Recap. */
object RecapNotifier {
    private const val CHANNEL_ID = "recap_serale"
    private const val NOTIFICATION_ID = 2001
    const val ACTION_OPEN_RECAP = "com.plumberdiary.app.action.OPEN_RECAP"

    fun notify(context: Context, stopsToReview: Int) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Recap serale", NotificationManager.IMPORTANCE_DEFAULT),
        )

        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_RECAP
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, NOTIFICATION_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tracking)
            .setContentTitle("Recap di oggi pronto")
            .setContentText(
                if (stopsToReview == 1) "1 sosta da rivedere e confermare"
                else "$stopsToReview soste da rivedere e confermare",
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }
}
