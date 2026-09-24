// DashboardScreen.kt — v1.6.0 — 2026-09-23
//
// Versione precedente (v1.0.0 — 2026-09-20 00:10 UTC): stub con solo il titolo,
// sostituita il 2026-09-23 dall'implementazione (requisito 17): ore totali, km
// percorsi, valore materiali e numero interventi del mese corrente, con
// ripartizione per cliente. Calcolata on-demand lato client aggregando le Stop
// del periodo (volume contenuto: nessun job di aggregazione lato backend).
package com.plumberdiary.app.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.plumberdiary.app.ui.common.Format
import com.plumberdiary.app.ui.common.rememberActiveSession
import java.util.Calendar
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(navController: NavHostController) {
    val session = rememberActiveSession()

    var monthLabel by remember { mutableStateOf("") }
    var totalMinutes by remember { mutableStateOf(0L) }
    var totalKm by remember { mutableStateOf(0.0) }
    var totalMaterials by remember { mutableStateOf(0.0) }
    var interventions by remember { mutableStateOf(0) }
    var perClient by remember { mutableStateOf<List<Pair<String, Triple<Long, Double, Int>>>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (session == null) return@LaunchedEffect
        try {
            val cal = Calendar.getInstance()
            cal.set(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            val monthStart = cal.timeInMillis
            monthLabel = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.ITALY).format(java.util.Date(monthStart))

            val stops = StopRepository().getStopsForDay(session.teamId, session.uid, monthStart, System.currentTimeMillis())
                .filter { it.endedAt > 0L }
                .sortedBy { it.startedAt }
            totalMinutes = stops.sumOf { Format.durationMinutes(it.startedAt, it.endedAt) }
            totalKm = DailyDistanceCalculator.totalKm(DailyDistanceCalculator.withDistances(stops))
            totalMaterials = stops.sumOf { s -> s.articleLines.sumOf { it.unitPrice * it.quantity } }
            interventions = stops.count { it.kind == StopKind.CLIENT || it.clientId != null }

            val clients = ClientRepository().getAll(session.teamId).associateBy { it.id }
            perClient = stops.filter { it.clientId != null }
                .groupBy { it.clientId!! }
                .map { (clientId, list) ->
                    Triple(
                        Format.durationLabel(list.sumOf { Format.durationMinutes(it.startedAt, it.endedAt) }),
                        list.sumOf { s -> s.articleLines.sumOf { it.unitPrice * it.quantity } },
                        list.size,
                    ).let { (clientId to it) }
                }
                .map { (id, t) -> (clients[id]?.name ?: id) to t }
                .sortedByDescending { (_, t) -> t.second }
        } catch (e: Exception) { errorMessage = e.message }
    }

    Column(modifier = Modifier.padding(20.dp)) {
        Text("Dashboard mensile — $monthLabel")

        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            StatTile("Ore", Format.durationLabel(totalMinutes), Modifier.weight(1f))
            StatTile("Km percorsi", Format.km(totalKm * 1000), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth()) {
            StatTile("Materiali", Format.euros(totalMaterials), Modifier.weight(1f))
            StatTile("Interventi", interventions.toString(), Modifier.weight(1f))
        }

        errorMessage?.let { Text(it) }

        if (perClient.isEmpty()) {
            Text("Nessun intervento associato a un cliente questo mese.")
        } else {
            Text("Ripartizione per cliente")
            perClient.forEach { (name, t) ->
                ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, modifier = Modifier.weight(1f))
                        Text("${t.first} · ${Format.euros(t.second)} · ${t.third} int.")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier.padding(4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(value)
            Text(label)
        }
    }
}
