// BootRestartReceiver.kt — v1.12.0 — 2026-09-29 (v1.7.0 — 2026-09-23: ripristino bloccante della sessione; v1.0.0 — 2026-09-20 00:10 UTC)
package com.plumberdiary.app.location

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.plumberdiary.app.session.CurrentSession
import com.plumberdiary.app.session.SessionStore
import kotlinx.coroutines.runBlocking

/**
 * Riavvia il tracciamento dopo un riavvio del telefono, se l'utente aveva
 * una sessione/squadra attiva e il tracciamento acceso (requisito 1).
 *
 * v1.12.0 — 2026-09-29:
 *  - riparte SOLO se l'utente l'aveva lasciato acceso ([SessionStore.readTrackingEnabled]):
 *    prima ripartiva sempre, anche dopo "Ferma tracciamento";
 *  - controlla il permesso di posizione: da Android 14 avviare un foreground
 *    service di tipo "location" senza permesso lancia SecurityException, e
 *    un permesso revocato mandava in crash l'app a ogni accensione.
 */
class BootRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // v1.7.0 — 2026-09-23: ripristino bloccante della sessione (lettura
        // DataStore rapida, accettabile in un receiver): il restore in
        // PlumberDiaryApp.onCreate è asincrono e poteva non essere completato.
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val store = SessionStore(context)
        // Versione precedente (v1.7.0 — 2026-09-23), sostituita il 2026-09-29:
        // if (CurrentSession.current() == null) {
        //     val teamId = runBlocking { SessionStore(context).readTeamId() } ?: return
        //     CurrentSession.set(uid, teamId)
        // }
        val (teamId, trackingEnabled) = runBlocking { store.readTeamId() to store.readTrackingEnabled() }
        if (teamId == null || !trackingEnabled) return
        if (CurrentSession.current() == null) CurrentSession.set(uid, teamId)

        val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fineGranted) return

        val serviceIntent = Intent(context, LocationTrackingService::class.java)
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
