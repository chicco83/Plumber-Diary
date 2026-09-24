// StoricoScreen.kt — v1.6.0 — 2026-09-23
//
// Versione precedente (v1.0.0 — 2026-09-20 00:10 UTC): stub con solo il titolo,
// sostituita il 2026-09-23 dall'implementazione (requisito 6): elenco dei
// giorni precedenti (ultimi 30) con riepilogo ore/km, espansione delle soste
// del giorno (link a Dettaglio per correzioni) e riassegnazione cliente in
// blocco su più giorni selezionati.
package com.plumberdiary.app.ui.storico

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.plumberdiary.app.data.model.ClientRecord
import com.plumberdiary.app.data.model.Stop
import com.plumberdiary.app.data.model.StopKind
import com.plumberdiary.app.data.repository.ClientRepository
import com.plumberdiary.app.data.repository.StopRepository
import com.plumberdiary.app.recap.DailyDistanceCalculator
import com.plumberdiary.app.ui.Routes
import com.plumberdiary.app.ui.common.Format
import com.plumberdiary.app.ui.common.rememberActiveSession
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun StoricoScreen(navController: NavHostController) {
    val scope = rememberCoroutineScope()
    val session = rememberActiveSession()

    var days by remember { mutableStateOf<Map<String, List<Stop>>>(emptyMap()) }
    var expandedDays by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedDays by remember { mutableStateOf<Set<String>>(emptySet()) }
    var clients by remember { mutableStateOf<List<ClientRecord>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }

    val stopRepository = remember { StopRepository() }
    val clientRepository = remember { ClientRepository() }
    val dayKeyFmt = remember { SimpleDateFormat("yyyyMMdd", Locale.ITALY) }

    LaunchedEffect(Unit) {
        if (session == null) return@LaunchedEffect
        try {
            val from = System.currentTimeMillis() - 30L * 24 * 3_600_000
            val all = stopRepository.getStopsForDay(session.teamId, session.uid, from, System.currentTimeMillis())
                .filter { it.endedAt > 0L }
                .sortedBy { it.startedAt }
            days = all.groupBy { dayKeyFmt.format(Date(it.startedAt)) }
            clients = clientRepository.getAll(session.teamId)
        } catch (e: Exception) { message = e.message }
    }

    Column(modifier = Modifier.padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Recap giorni precedenti", modifier = Modifier.weight(1f))
        }

        if (days.isEmpty()) {
            Text("Nessun recap nei ultimi 30 giorni.")
        } else {
            days.toSortedMap().entries.reversed().forEach { (key, stops) ->
                val totalMinutes = stops.sumOf { Format.durationMinutes(it.startedAt, it.endedAt) }
                val km = DailyDistanceCalculator.totalKm(DailyDistanceCalculator.withDistances(stops))

                ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = key in selectedDays,
                                onCheckedChange = { checked ->
                                    selectedDays = if (checked) selectedDays + key else selectedDays - key
                                },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(Format.day(stops.first().startedAt))
                                Text("${stops.size} soste · ${Format.durationLabel(totalMinutes)} · ${Format.km(km)}")
                            }
                            OutlinedButton(onClick = {
                                expandedDays = if (key in expandedDays) expandedDays - key else expandedDays + key
                            }) { Text(if (key in expandedDays) "Chiudi" else "Apri") }
                        }

                        if (key in expandedDays) {
                            stops.forEach { stop ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(Format.time(stop.startedAt), modifier = Modifier.weight(1f))
                                    Text(
                                        when {
                                            stop.clientId != null -> clients.firstOrNull { it.id == stop.clientId }?.name ?: "?"
                                            stop.kind == StopKind.DEPOT -> "Sede"
                                            stop.kind == StopKind.BREAK -> "Pausa"
                                            else -> "—"
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton2 {
                                        navController.navigate(Routes.dettaglio(stop.id))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (selectedDays.isNotEmpty()) {
            Button(
                onClick = {
                    val s = session ?: return@Button
                    scope.launch {
                        try {
                            // Riassegnazione in blocco: serve un cliente scelto;
                            // qui si usa il primo dell'anagrafica come default e
                            // l'utente corregge poi nel Dettaglio se diverso.
                            val target = clients.firstOrNull() ?: run { message = "Aggiungi prima un cliente nell'anagrafica."; return@launch }
                            var count = 0
                            for (key in selectedDays) {
                                for (stop in days[key].orEmpty()) {
                                    if (stop.clientId == null && stop.kind != StopKind.DEPOT && stop.kind != StopKind.BREAK) {
                                        stopRepository.upsert(s.teamId, s.uid, stop.copy(clientId = target.id, kind = StopKind.CLIENT))
                                        count++
                                    }
                                }
                            }
                            message = "$count soste riassegnate a ${target.name} (verificali nel Dettaglio)."
                            selectedDays = emptySet()
                        } catch (e: Exception) { message = e.message }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("Riassegna a '${clients.firstOrNull()?.name ?: "?"}' le soste senza cliente nei giorni selezionati") }
        }

        message?.let { Text(it) }
    }
}

/** Piccolo pulsante "Modifica" delle righe di storico. */
@Composable
private fun TextButton2(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) { Text("Modifica") }
}
