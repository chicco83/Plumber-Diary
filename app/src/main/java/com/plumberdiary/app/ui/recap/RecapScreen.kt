// RecapScreen.kt — v1.11.0 — 2026-09-29 (v1.8.0 — 2026-09-29; v1.7.0 — 2026-09-23)
//
// Versione precedente (v1.0.0 — 2026-09-20 00:10 UTC): stub con solo il titolo
// "Riepilogo giornata", sostituita il 2026-09-23 dall'implementazione reale:
// una card per sosta del giorno con cliente suggerito (ClientMatcher su
// posizioni note), note modificabili, km dalla sosta precedente (requisito 16),
// link a Dettaglio/Mandatino, conferma recap e invio email all'amministrazione
// (requisiti 6/12) tramite l'endpoint Vercel send-recap-email.
package com.plumberdiary.app.ui.recap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
// v1.8.0 — 2026-09-29: import mancante, items(stops) non compilava.
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.plumberdiary.app.auth.AuthRepository
import com.plumberdiary.app.data.BackendClient
import com.plumberdiary.app.data.BackendConfig
import com.plumberdiary.app.data.model.ClientRecord
import com.plumberdiary.app.data.model.Stop
import com.plumberdiary.app.data.model.StopKind
import com.plumberdiary.app.data.repository.ClientRepository
import com.plumberdiary.app.data.repository.SettingsRepository
import com.plumberdiary.app.data.repository.StopRepository
import com.plumberdiary.app.location.ClientMatcher
import com.plumberdiary.app.recap.DailyDistanceCalculator
import com.plumberdiary.app.ui.Routes
import com.plumberdiary.app.ui.common.Format
import com.plumberdiary.app.ui.common.PlumberScaffold
import com.plumberdiary.app.ui.common.rememberActiveSession
import java.util.Calendar
import kotlinx.coroutines.launch

@Composable
fun RecapScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = rememberActiveSession()

    var stops by remember { mutableStateOf<List<Stop>>(emptyList()) }
    var clients by remember { mutableStateOf<List<ClientRecord>>(emptyList()) }
    var dayStartMillis by remember { mutableStateOf(0L) }
    var recapMessage by remember { mutableStateOf<String?>(null) }
    var emailMessage by remember { mutableStateOf<String?>(null) }

    val stopRepository = remember { StopRepository() }
    val clientRepository = remember { ClientRepository() }
    val settingsRepository = remember { SettingsRepository() }
    val backendClient = remember { BackendClient(BackendConfig.BASE_URL, AuthRepository(context)) }

    suspend fun refresh() {
        if (session == null) return
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        dayStartMillis = cal.timeInMillis
        val all = stopRepository.getStopsForDay(session.teamId, session.uid, dayStartMillis, System.currentTimeMillis() + 60_000L)
        stops = DailyDistanceCalculator.withDistances(all.sortedBy { it.startedAt })
        clients = clientRepository.getAll(session.teamId)
    }

    LaunchedEffect(Unit) { refresh() }

    /** Invia il recap all'amministrazione se attivo nelle Opzioni; ritorna l'esito da mostrare. */
    suspend fun sendRecapEmail(teamId: String, uid: String): String = try {
        val settings = settingsRepository.get(teamId, uid)
        val recipient = settings.recapEmailAddress.trim()
        when {
            !settings.recapEmailEnabled -> "Invio recap via email disattivato nelle Opzioni."
            recipient.isEmpty() -> "Invio recap fallito: manca l'email dell'amministrazione nelle Opzioni."
            else -> {
                backendClient.sendRecapEmail(teamId, dayStartMillis, System.currentTimeMillis() + 60_000L, recipient)
                "Recap inviato a $recipient (foto in alta risoluzione incluse)."
            }
        }
    } catch (e: Exception) {
        "Invio recap fallito: ${e.message}"
    }

    PlumberScaffold(navController = navController, currentRoute = Routes.RECAP) { padding ->
        Column(modifier = Modifier.padding(padding).padding(20.dp)) {
            Text("Riepilogo giornata")
            if (stops.isNotEmpty()) {
                val totalMinutes = stops.filter { it.endedAt > 0L }.sumOf { Format.durationMinutes(it.startedAt, it.endedAt) }
                // v1.8.0 — 2026-09-29: Format.km vuole metri e totalKm restituisce km:
                // prima il totale del giorno risultava mille volte più piccolo.
                val totalKmLabel = Format.km(DailyDistanceCalculator.totalKm(stops) * 1000)
                Text("Totale: ${Format.durationLabel(totalMinutes)} · ${totalKmLabel} · ${stops.size} soste")
            }

            if (stops.isEmpty()) {
                Text("Nessuna sosta registrata oggi.")
            } else {
                // v1.8.0 — 2026-09-29: weight(1f) — senza, con molte soste la lista
                // occupava tutto lo schermo e i pulsanti "Conferma recap"/"Invia"
                // sotto restavano irraggiungibili.
                // LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 12.dp)) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f).padding(vertical = 12.dp)) {
                    items(stops) { stop ->
                        val suggested: ClientRecord? = if (stop.clientId == null && stop.kind != StopKind.DEPOT && stop.kind != StopKind.BREAK) {
                            ClientMatcher.findSuggestedClient(stop.lat, stop.lon, clients)
                        } else null

                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (stop.endedAt == 0L) {
                                        Text("● ", color = Color(0xFFD97706))
                                        Text("In corso da ${Format.time(stop.startedAt)}", modifier = Modifier.weight(1f))
                                    } else {
                                        Text(
                                            "${Format.time(stop.startedAt)} – ${Format.time(stop.endedAt)} · ${
                                                Format.durationLabel(Format.durationMinutes(stop.startedAt, stop.endedAt))
                                            }",
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }

                                when {
                                    stop.clientId != null -> Text("Cliente: ${clients.firstOrNull { it.id == stop.clientId }?.name ?: "?"}")
                                    suggested != null -> OutlinedButton(onClick = {
                                        val updated = stop.copy(clientId = suggested.id, kind = StopKind.CLIENT, clientSuggested = true)
                                        stops = stops.map { if (it.id == stop.id) updated else it }
                                        scope.launch {
                                            try {
                                                // v1.8.0 — 2026-09-29: solo i campi dell'utente (prima
                                                // upsert dell'intero documento, in conflitto con il service).
                                                stopRepository.saveUserEdits(session!!.teamId, session.uid, updated)
                                                clientRepository.recordVisit(session.teamId, suggested.id, stop.lat, stop.lon, System.currentTimeMillis())
                                                recapMessage = "Cliente confermato: ${suggested.name}"
                                            } catch (e: Exception) { recapMessage = e.message }
                                        }
                                    }) {
                                        Text("Suggerito: ${suggested.name} — Conferma")
                                    }
                                    stop.kind == StopKind.DEPOT -> Text("Sede / deposito (esclusa dal rilevamento cliente)")
                                    stop.kind == StopKind.BREAK -> Text("Pausa (esclusa dal rilevamento cliente)")
                                    else -> Text("Nessun cliente noto qui — associane uno nel Dettaglio")
                                }

                                OutlinedTextField(
                                    value = stop.notes,
                                    onValueChange = { newText -> stops = stops.map { if (it.id == stop.id) it.copy(notes = newText) else it } },
                                    label = { Text("Note") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                if (stop.distanceFromPreviousMeters > 0.5) {
                                    Text("${Format.km(stop.distanceFromPreviousMeters)} dalla sosta precedente")
                                }

                                Row(Modifier.padding(top = 8.dp)) {
                                    OutlinedButton(onClick = { navController.navigate(Routes.dettaglio(stop.id)) }) {
                                        Text("Dettaglio")
                                    }
                                    Spacer(Modifier.weight(1f).height(0.dp))
                                    // v1.11.0 — 2026-09-29: era "Rapportino con firma" (stessa cosa
                                    // del mandatino). Il mandatino è per cliente: serve una sosta
                                    // già associata a un cliente.
                                    OutlinedButton(
                                        onClick = { stop.clientId?.let { navController.navigate(Routes.mandatino(it)) } },
                                        enabled = stop.clientId != null,
                                    ) {
                                        Text("Mandatino")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            recapMessage?.let { Text(it) }

            if (session != null) {
                Button(
                    onClick = {
                        scope.launch {
                            // Versione precedente (v1.7.0 — 2026-09-23), sostituita il 2026-09-29:
                            // upsert dell'intero documento di ogni sosta (sovrascriveva i
                            // campi del service sulla sosta ancora aperta) e nessun invio
                            // della mail, che restava solo manuale.
                            try {
                                val updated = stops.map { it.copy(confirmedInRecap = true) }
                                for (s in updated) stopRepository.saveUserEdits(session.teamId, session.uid, s)
                                // Aggiorna le posizioni note dei clienti confermati (requisito 4).
                                for (s in updated) {
                                    val clientId = s.clientId ?: continue
                                    clientRepository.recordVisit(session.teamId, clientId, s.lat, s.lon, System.currentTimeMillis())
                                }
                                stops = updated
                                recapMessage = "Recap confermato: le modifiche sono salvate."
                            } catch (e: Exception) {
                                recapMessage = e.message
                                return@launch
                            }

                            // Requisito 6: mail all'amministrazione, attiva di default,
                            // inviata alla conferma così contiene i dati già corretti.
                            emailMessage = sendRecapEmail(session.teamId, session.uid)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) { Text("Conferma recap") }

                OutlinedButton(
                    onClick = {
                        if (session == null) return@OutlinedButton
                        scope.launch {
                            emailMessage = null
                            emailMessage = sendRecapEmail(session.teamId, session.uid)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Invia recap via email") }
            }

            emailMessage?.let { Text(it, color = if (it.startsWith("Invio recap fallito")) Color.Red else Color(0xFF0F766E)) }
        }
    }
}

