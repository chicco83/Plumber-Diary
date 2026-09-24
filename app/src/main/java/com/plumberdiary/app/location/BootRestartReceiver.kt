// BootRestartReceiver.kt — v1.0.0 — 2026-09-20 00:10 UTC
package com.plumberdiary.app.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.plumberdiary.app.session.CurrentSession
import com.plumberdiary.app.session.SessionStore
import kotlinx.coroutines.runBlocking

/**
 * Riavvia il tracciamento dopo un riavvio del telefono, se l'utente aveva
 * una sessione/squadra attiva (requisito 1: tracciamento "sempre attivo").
 * La sessione va ripristinata da storage persistente prima di questa
 * chiamata (vedi nota in [CurrentSession]); qui si presume già fatto da un
 * eventuale Application.onCreate — dettaglio da completare insieme al login.
 */
class BootRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // v1.6.0 — 2026-09-23: race condition dello scaffolding — il restore di
        // CurrentSession in PlumberDiaryApp.onCreate è asincrono (coroutine su
        // Dispatchers.IO), quindi al momento del broadcast BOOT_COMPLETED poteva
        // non essere ancora completato e il tracciamento non ripartiva mai.
        // Qui si ripristina la sessione in modo bloccante (lettura DataStore
        // rapida, accettabile in un receiver) prima di controllare lo stato.
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (CurrentSession.current() == null) {
            val teamId = runBlocking { SessionStore(context).readTeamId() } ?: return
            CurrentSession.set(uid, teamId)
        }

        val serviceIntent = Intent(context, LocationTrackingService::class.java)
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
