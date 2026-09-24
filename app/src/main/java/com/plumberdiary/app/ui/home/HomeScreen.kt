// HomeScreen.kt — v1.6.0 — 2026-09-23
//
// Versione precedente (v1.0.0 — 2026-09-20 00:10 UTC): stub con solo il titolo
// "Giornata di oggi" e un TODO, sostituita il 2026-09-23 dall'implementazione
// reale: timeline delle soste del giorno da StopRepository, card della sosta
// in corso (persistita dal service con endedAt = 0), start/stop del
// tracciamento e flusso di permessi progressivo (foreground → background).
package com.plumberdiary.app.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.plumberdiary.app.data.model.Stop
import com.plumberdiary.app.data.model.StopKind
import com.plumberdiary.app.data.repository.ClientRepository
import com.plumberdiary.app.data.repository.StopRepository
import com.plumberdiary.app.location.LocationTrackingService
import com.plumberdiary.app.recap.DailyDistanceCalculator
import com.plumberdiary.app.ui.Routes
import com.plumberdiary.app.ui.common.Format
import com.plumberdiary.app.ui.common.PlumberScaffold
import com.plumberdiary.app.ui.common.rememberActiveSession
import java.util.Calendar
import kotlinx.coroutines.launch

/**
 * Mockup "Home.dc.html": giornata in corso, timeline delle soste di oggi,
 * card della sosta corrente. Alimentata da [StopRepository] (refresh al
 * compose + pulsante manuale: la sosta in corso viene aggiornata dal
 * foreground service a ogni fix, quindi il refresh mostra subito i dati).
 */
@Composable
fun HomeScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = rememberActiveSession()

    var stops by remember { mutableStateOf<List<Stop>>(emptyList()) }
    var clientNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var trackingOn by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Permessi progressivi (vedi AndroidManifest: mai richiesti insieme su API 30+).
    var fineGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED)
    }
    var backgroundGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            == PackageManager.PERMISSION_GRANTED)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        backgroundGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            == PackageManager.PERMISSION_GRANTED
    }

    suspend fun refresh() {
        if (session == null) return
        try {
            val dayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val all = StopRepository().getStopsForDay(session.teamId, session.uid, dayStart, System.currentTimeMillis() + 60_000L)
            stops = DailyDistanceCalculator.withDistances(all.sortedBy { it.startedAt })
            clientNames = ClientRepository().getAll(session.teamId).associate { it.id to it.name }
            trackingOn = all.any { it.endedAt == 0L }
        } catch (e: Exception) {
            errorMessage = e.message
        }
    }

    LaunchedEffect(Unit) { refresh() }

    PlumberScaffold(navController = navController, currentRoute = Routes.HOME) { padding ->
        Column(modifier = Modifier.padding(padding).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Giornata di oggi", modifier = Modifier.weight(1f))
                if (fineGranted) {
                    Button(onClick = {
                        val intent = Intent(context, LocationTrackingService::class.java)
                        if (trackingOn) context.stopService(intent) else ContextCompat.startForegroundService(context, intent)
                        trackingOn = !trackingOn
                    }) {
                        Text(if (trackingOn) "Ferma tracciamento" else "Inizia tracciamento")
                    }
                }
            }

            if (!fineGranted) {
                ElevatedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Permessi posizione necessari")
                        Text(
                            "Il tracciamento richiede la posizione in foreground. " +
                                "Dopo, ti chiederemo quella in background (passo separato).",
                        )
                        Button(
                            onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) { Text("Concedi posizione") }
                    }
                }
            } else if (fineGranted && !backgroundGranted) {
                OutlinedButton(
                    onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) { Text("Abilita posizione in background (consigliato per il tracciamento continuo)") }
            }

            if (stops.isNotEmpty()) {
                val totalMinutes = stops.filter { it.endedAt > 0L }.sumOf { Format.durationMinutes(it.startedAt, it.endedAt) }
                Text("Totale: ${Format.durationLabel(totalMinutes)} · ${Format.km(DailyDistanceCalculator.totalKm(stops))}")
            }

            errorMessage?.let { Text(it, color = androidx.compose.ui.graphics.Color.Red) }

            if (stops.isEmpty()) {
                Text("Nessuna sosta registrata oggi. Inizia il tracciamento e la timeline si popolerà automaticamente.")
            } else {
                // Timeline in ordine inverso: la più recente (eventualmente in corso) in cima.
                stops.asReversed().forEach { stop ->
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        onClick = { navController.navigate(Routes.dettaglio(stop.id)) },
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (stop.endedAt == 0L) {
                                    Text("● ", color = androidx.compose.ui.graphics.Color(0xFFD97706))
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
                            Text(
                                when {
                                    stop.clientId != null -> "Cliente: ${clientNames[stop.clientId] ?: "?"}"
                                    stop.kind == StopKind.DEPOT -> "Sede / deposito"
                                    stop.kind == StopKind.BREAK -> "Pausa"
                                    else -> "Nessun cliente associato (correggilo nel recap)"
                                },
                            )
                            if (stop.distanceFromPreviousMeters > 0.5) {
                                Text("${Format.km(stop.distanceFromPreviousMeters)} dalla sosta precedente")
                            }
                            if (stop.notes.isNotBlank()) Text(stop.notes)
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { scope.launch { refresh() } },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text("Aggiorna") }
        }
    }
}
