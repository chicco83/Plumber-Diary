// RealtimeConfirmNotifier.kt — v1.0.0 — 2026-09-20 00:10 UTC
package com.plumberdiary.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.plumberdiary.app.MainActivity
import com.plumberdiary.app.R
import com.plumberdiary.app.data.model.ClientRecord
import com.plumberdiary.app.data.model.Stop

/**
 * Requisito 14: notifica locale immediata "sei da [cliente]?" al superamento
 * della soglia — non richiede rete, il match con lo storico è già disponibile
 * sul device (vedi [com.plumberdiary.app.location.LocationTrackingService]).
 * Le azioni rapide aprono l'app sulla schermata di conferma (mockup
 * "Notifica"): la vera scrittura su Firestore avviene lì, non nel
 * BroadcastReceiver dell'azione, per riusare gli stessi controlli/permessi
 * della UI invece di duplicarli.
 */
object RealtimeConfirmNotifier {
    private const val CHANNEL_ID = "realtime_client_confirm"

    fun notify(context: Context, suggestedClient: ClientRecord, openStop: Stop) {
        ensureChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_CONFIRM_CLIENT
            putExtra(EXTRA_CLIENT_ID, suggestedClient.id)
            putExtra(EXTRA_STOP_STARTED_AT, openStop.startedAt)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, openStop.startedAt.toInt(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tracking)
            .setContentTitle("Sei da ${suggestedClient.name}?")
            .setContentText("Sei fermo qui da un po', posizione già nota")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(openStop.startedAt.toInt(), notification)
    }

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID, "Conferma cliente in tempo reale", NotificationManager.IMPORTANCE_HIGH,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    const val ACTION_CONFIRM_CLIENT = "com.plumberdiary.app.action.CONFIRM_CLIENT"
    const val EXTRA_CLIENT_ID = "extra_client_id"
    const val EXTRA_STOP_STARTED_AT = "extra_stop_started_at"
}
