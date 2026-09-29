// StoricoScreen.kt — v1.8.0 — 2026-09-29 (v1.7.0 — 2026-09-23; v1.0.0 — 2026-09-20 00:10 UTC)
//
// Requisito 6: elenco dei giorni precedenti (ultimi 30) con riepilogo
// ore/km, espansione delle soste del giorno (link a Dettaglio per le
// correzioni) e riassegnazione cliente in blocco su più giorni selezionati.
// Requisito 11: con l'opzione "Vedi i recap dei colleghi" attiva (default
// OFF) si possono consultare, in sola lettura, i recap confermati degli
// altri membri della squadra.
//
// Versione precedente (v1.7.0 — 2026-09-23), sostituita il 2026-09-29:
//  - la riassegnazione in blocco usava sempre il PRIMO cliente dell'anagrafica,
//    senza chiedere quale:
//
//      val target = clients.firstOrNull() ?: run { message = "Aggiungi prima un cliente nell'anagrafica."; return@launch }
//      ... stopRepository.upsert(s.teamId, s.uid, stop.copy(clientId = target.id, kind = StopKind.CLIENT))
//
//  - upsert dell'intero documento della sosta (vedi StopRepository v1.8.0);
//  - l'opzione "vedi recap dei colleghi" non aveva alcun effetto;
//  - niente scorrimento né freccia indietro.
package com.plumberdiary.app.ui.storico

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.plumberdiary.app.data.model.TeamMember
import com.plumberdiary.app.data.repository.ClientRepository
import com.plumberdiary.app.data.repository.SettingsRepository
import com.plumberdiary.app.data.repository.StopRepository
import com.plumberdiary.app.data.repository.TeamRepository
import com.plumberdiary.app.recap.DailyDistanceCalculator
import com.plumberdiary.app.ui.Routes
import com.plumberdiary.app.ui.common.Format
import com.plumberdiary.app.ui.common.rememberActiveSession
import java.text.SimpleDateFormat
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
    var targetClient by remember { mutableStateOf<ClientRecord?>(null) }
    var showClientMenu by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    // Recap dei colleghi (requisito 11): membro di cui si consulta lo storico.
    var canSeeTeammates by remember { mutableStateOf(false) }
    var members by remember { mutableStateOf<List<TeamMember>>(emptyList()) }
    var viewedUid by remember { mutableStateOf(session?.uid) }
    var showMemberMenu by remember { mutableStateOf(false) }
    val viewingOwn = viewedUid == session?.uid

    val stopRepository = remember { StopRepository() }
    val clientRepository = remember { ClientRepository() }
    val dayKeyFmt = remember { SimpleDateFormat("yyyyMMdd", Locale.ITALY) }

    suspend fun load() {
        val s = session ?: return
        val uid = viewedUid ?: return
        val from = System.currentTimeMillis() - 30L * 24 * 3_600_000
        val all = stopRepository.getStopsForDay(s.teamId, uid, from, System.currentTimeMillis())
            .filter { it.endedAt > 0L }
            // Dei colleghi si mostrano solo i recap già confermati.
            .filter { uid == s.uid || it.confirmedInRecap }
            .sortedBy { it.startedAt }
        days = all.groupBy { dayKeyFmt.format(Date(it.startedAt)) }
    }

    LaunchedEffect(Unit) {
        val s = session ?: return@LaunchedEffect
        try {
            clients = clientRepository.getAll(s.teamId)
            canSeeTeammates = SettingsRepository().get(s.teamId, s.uid).seeTeammatesRecapEnabled
            if (canSeeTeammates) members = TeamRepository().getMembers(s.teamId)
        } catch (e: Exception) { message = e.message }
    }

    LaunchedEffect(viewedUid) {
        selectedDays = emptySet()
        expandedDays = emptySet()
        try { load() } catch (e: Exception) { message = e.message }
    }

    Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, null) }
            Text("Recap giorni precedenti", modifier = Modifier.weight(1f))
        }

        if (canSeeTeammates && members.size > 1) {
            Box {
                val viewedName = members.firstOrNull { it.uid == viewedUid }
                    ?.let { m -> m.displayName.ifBlank { m.email } } ?: "I miei recap"
                OutlinedButton(onClick = { showMemberMenu = true }) {
                    Text(if (viewingOwn) "I miei recap" else "Recap di $viewedName (sola lettura)")
                }
                DropdownMenu(expanded = showMemberMenu, onDismissRequest = { showMemberMenu = false }) {
                    members.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(if (m.uid == session?.uid) "I miei recap" else m.displayName.ifBlank { m.email }) },
                            onClick = { viewedUid = m.uid; showMemberMenu = false },
                        )
                    }
                }
            }
        }

        if (days.isEmpty()) {
            Text(if (viewingOwn) "Nessun recap negli ultimi 30 giorni." else "Nessun recap confermato negli ultimi 30 giorni.")
        } else {
            days.toSortedMap().entries.reversed().forEach { (key, stops) ->
                val totalMinutes = stops.sumOf { Format.durationMinutes(it.startedAt, it.endedAt) }
                val km = DailyDistanceCalculator.totalKm(DailyDistanceCalculator.withDistances(stops))

                ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (viewingOwn) {
                                Checkbox(
                                    checked = key in selectedDays,
                                    onCheckedChange = { checked ->
                                        selectedDays = if (checked) selectedDays + key else selectedDays - key
                                    },
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(Format.day(stops.first().startedAt))
                                // Format.km vuole metri, totalKm restituisce km.
                                Text("${stops.size} soste · ${Format.durationLabel(totalMinutes)} · ${Format.km(km * 1000)}")
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
                                    // Le soste dei colleghi sono in sola lettura.
                                    if (viewingOwn) {
                                        OutlinedButton(onClick = { navController.navigate(Routes.dettaglio(stop.id)) }) {
                                            Text("Modifica")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (viewingOwn && selectedDays.isNotEmpty()) {
            Text("Correzione massiva: assegna un cliente alle soste senza cliente dei giorni selezionati", modifier = Modifier.padding(top = 12.dp))
            Box {
                OutlinedButton(onClick = { showClientMenu = true }) {
                    Text(targetClient?.name ?: "Scegli il cliente")
                }
                DropdownMenu(expanded = showClientMenu, onDismissRequest = { showClientMenu = false }) {
                    clients.forEach { c ->
                        DropdownMenuItem(text = { Text(c.name) }, onClick = { targetClient = c; showClientMenu = false })
                    }
                }
            }
            Button(
                enabled = targetClient != null,
                onClick = {
                    val s = session ?: return@Button
                    val target = targetClient ?: return@Button
                    scope.launch {
                        try {
                            var count = 0
                            for (key in selectedDays) {
                                for (stop in days[key].orEmpty()) {
                                    if (stop.clientId == null && stop.kind != StopKind.DEPOT && stop.kind != StopKind.BREAK) {
                                        stopRepository.saveUserEdits(s.teamId, s.uid, stop.copy(clientId = target.id, kind = StopKind.CLIENT))
                                        count++
                                    }
                                }
                            }
                            message = "$count soste assegnate a ${target.name}."
                            selectedDays = emptySet()
                            load()
                        } catch (e: Exception) { message = e.message }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("Assegna a ${targetClient?.name ?: "…"} nei giorni selezionati") }
        }

        message?.let { Text(it) }
    }
}
