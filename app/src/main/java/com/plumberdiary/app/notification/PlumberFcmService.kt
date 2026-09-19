// PlumberFcmService.kt — v1.0.0 — 2026-09-20 00:10 UTC
package com.plumberdiary.app.notification

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Riceve i push inviati dal backend (recap pronto, promemoria squadra, ecc.).
 * Lezione dal progetto gemello gwatch-child-tracker (vedi context.md,
 * "Architettura backend"): i payload che devono attivare comportamento anche
 * ad app in background/uccisa vanno mandati DATA-ONLY, non
 * notification+data — vanno quindi gestiti qui esplicitamente PRIMA di un
 * eventuale controllo su remoteMessage.notification, che altrimenti li scarta.
 */
class PlumberFcmService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        when (remoteMessage.data["type"]) {
            "recap_ready" -> {
                // TODO: mostrare la notifica locale di recap pronto e/o
                // aggiornare uno stato osservabile dalla UI Home.
            }
            "team_invite_accepted" -> {
                // TODO: notificare che un collega si è unito alla squadra.
            }
            else -> {
                // Messaggio con solo `notification`: gestito dal sistema di
                // default, nessuna azione custom necessaria qui.
            }
        }
    }

    override fun onNewToken(token: String) {
        // TODO: registrare il token su teams/{teamId}/members/{uid} tramite
        // un endpoint backend (stesso pattern di register-watch-token nel
        // progetto gemello), non con una scrittura diretta se il token deve
        // anche autorizzare l'invio push lato server.
    }
}
