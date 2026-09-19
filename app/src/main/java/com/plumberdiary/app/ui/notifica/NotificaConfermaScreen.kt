// NotificaConfermaScreen.kt — v1.0.0 — 2026-09-20 00:10 UTC
package com.plumberdiary.app.ui.notifica

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

/**
 * Mockup "Notifica.dc.html": aperta dal tocco sulla notifica di
 * [com.plumberdiary.app.notification.RealtimeConfirmNotifier] (requisito 14).
 * "Sì, confermo" marca la sosta corrente con questo cliente e imposta
 * realtimeConfirmedAt; resta comunque modificabile nel recap serale.
 */
@Composable
fun NotificaConfermaScreen(navController: NavHostController, clientId: String) {
    Column(modifier = Modifier.padding(20.dp)) {
        Text("Confermi questo intervento? (cliente $clientId)")
        // TODO: card cliente suggerito + azioni "Sì, confermo" / "Cambia" /
        // "Non ora", vedi mockup Notifica.dc.html.
        Button(onClick = { navController.popBackStack() }) { Text("Sì, confermo") }
    }
}
