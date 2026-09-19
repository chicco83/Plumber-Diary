// BootRestartReceiver.kt — v1.0.0 — 2026-09-20 00:10 UTC
package com.plumberdiary.app.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.plumberdiary.app.session.CurrentSession

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
        if (CurrentSession.current() == null) return

        val serviceIntent = Intent(context, LocationTrackingService::class.java)
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
