// NotificaConfermaScreen.kt — v1.6.0 — 2026-09-24
//
// Versione precedente (v1.0.0 — 2026-09-20 00:10 UTC): stub con un solo
// pulsante "Sì, confermo" che non scriveva nulla, sostituita il 2026-09-24
// dall'implementazione reale (requisito 14): card del cliente suggerito +
// azioni "Sì, confermo" / "Cambia" / "Non ora".
// - "Sì, confermo": associa il cliente alla sosta APERTA corrente e imposta
//   realtimeConfirmedAt; la posizione nota viene aggiornata (requisito 4) così
//   la prossima volta il match è immediato.
// - "Cambia": apre il Dettaglio della stessa sosta per scegliere un altro
//   cliente o crearne uno nuovo.
// - "Non ora": imposta realtimeDismissedAt sul campo Stop, altrimenti il
//   service ri-notificherebbe ad ogni fix finché la soglia resta superata.
// In tutti i casi la sosta resta correggibile nel recap serale.
package com.plumberdiary.app.ui.notifica

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.plumberdiary.app.data.model.ClientRecord
import com.plumberdiary.app.data.model.Stop
import com.plumberdiary.app.data.model.StopKind
import com.plumberdiary.app.data.repository.ClientRepository
import com.plumberdiary.app.data.repository.StopRepository
import com.plumberdiary.app.ui.Routes
import com.plumberdiary.app.ui.common.Format
import com.plumberdiary.app.ui.common.rememberActiveSession
import kotlinx.coroutines.launch

@Composable
fun NotificaConfermaScreen(navController: NavHostController, clientId: String) {
    val scope = rememberCoroutineScope()
    val session = rememberActiveSession()

    var client by remember { mutableStateOf<ClientRecord?>(null) }
    var openStop by remember { mutableStateOf<Stop?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val stopRepository = remember { StopRepository() }
    val clientRepository = remember { ClientRepository() }

    LaunchedEffect(clientId) {
        if (session == null) return@LaunchedEffect
        try {
            client = clientRepository.getById(session.teamId, clientId)
            openStop = stopRepository.getOpenStop(session.teamId, session.uid)
        } catch (e: Exception) {
            message = e.message
        }
    }

    // Questa schermata può essere la destinazione iniziale (deep link dalla
    // notifica): popBackStack() qui non tornerebbe da nessuna parte, quindi si
    // naviga esplicitamente alla Home.
    fun goHome() {
        navController.navigate(Routes.HOME) {
            popUpTo(Routes.HOME) { inclusive = false }
            launchSingleTop = true
        }
    }

    Column(modifier = Modifier.padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = goHome) { Icon(Icons.Filled.ArrowBack, null) }
            Text("Conferma intervento", modifier = Modifier.weight(1f))
        }

        val c = client
        val stop = openStop
        if (c == null || stop == null) {
            Text(if (message != null) message!! else "Caricamento... o nessuna sosta in corso da confermare.")
            Button(onClick = goHome, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Torna alla Home") }
        } else {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Sei da ${c.name}?", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text("Fermo qui dalle ${Format.time(stop.startedAt)}")
                    if (c.phone.isNotBlank()) Text("Tel: ${c.phone}")
                    Text(c.defaultAddressLabel.ifBlank { "Posizione già nota da un intervento precedente" })
                }
            }

            Button(
                onClick = {
                    val s = session ?: return@Button
                    busy = true
                    scope.launch {
                        try {
                            stopRepository.upsert(
                                s.teamId, s.uid,
                                stop.copy(
                                    clientId = c.id,
                                    kind = StopKind.CLIENT,
                                    clientSuggested = true,
                                    realtimeConfirmedAt = System.currentTimeMillis(),
                                ),
                            )
                            clientRepository.recordVisit(s.teamId, c, stop.lat, stop.lon, System.currentTimeMillis())
                            goHome()
                        } catch (e: Exception) {
                            message = e.message
                            busy = false
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) { Text(if (busy) "Conferma..." else "Sì, confermo") }

            OutlinedButton(
                onClick = { navController.navigate(Routes.dettaglio(stop.id)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Cambia cliente / modifica la sosta") }

            TextButton(
                onClick = {
                    val s = session ?: return@TextButton
                    busy = true
                    scope.launch {
                        try {
                            stopRepository.upsert(
                                s.teamId, s.uid,
                                stop.copy(realtimeDismissedAt = System.currentTimeMillis()),
                            )
                            goHome()
                        } catch (e: Exception) {
                            message = e.message
                            busy = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Non ora") }

            message?.let { Text(it, color = Color.Red) }
        }
    }
}
