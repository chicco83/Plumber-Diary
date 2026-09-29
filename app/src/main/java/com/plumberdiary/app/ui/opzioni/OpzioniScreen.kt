// OpzioniScreen.kt — v1.12.0 — 2026-09-29 (v1.8.0 — 2026-09-29; v1.7.0 — 2026-09-23)
//
// Versione precedente (v1.0.0 — 2026-09-20 00:10 UTC): stub con solo il titolo,
// sostituita il 2026-09-23 dalla schermata completa: tutte le UserSettings
// (requisiti 6/13/14) editabili, sede/deposito con "usa posizione attuale",
// pause orarie, sezione squadra con codice invito, link a Storico/Dashboard e
// logout. Scrittura unica su Firestore al pulsante "Salva".
package com.plumberdiary.app.ui.opzioni

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.plumberdiary.app.auth.AuthRepository
import com.plumberdiary.app.data.BackendClient
import com.plumberdiary.app.data.BackendConfig
import com.plumberdiary.app.data.model.ScheduledBreak
import com.plumberdiary.app.data.model.UserSettings
import com.plumberdiary.app.data.repository.SettingsRepository
import com.plumberdiary.app.data.repository.TeamRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import com.plumberdiary.app.location.LocationTrackingService
import com.plumberdiary.app.recap.RecapScheduler
import com.plumberdiary.app.session.CurrentSession
import com.plumberdiary.app.session.SessionStore
import com.plumberdiary.app.ui.Routes
import com.plumberdiary.app.ui.common.PlumberScaffold
import com.plumberdiary.app.ui.common.rememberActiveSession
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun OpzioniScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = rememberActiveSession()

    var settings by remember { mutableStateOf<UserSettings?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var inviteCode by remember { mutableStateOf<String?>(null) }
    var depotLatText by remember { mutableStateOf("") }
    var depotLonText by remember { mutableStateOf("") }

    val settingsRepository = remember { SettingsRepository() }
    val backendClient = remember { BackendClient(BackendConfig.BASE_URL, AuthRepository(context)) }

    LaunchedEffect(Unit) {
        if (session == null) return@LaunchedEffect
        try {
            val s = settingsRepository.get(session.teamId, session.uid)
            settings = s
            depotLatText = s.depotLat?.toString() ?: ""
            depotLonText = s.depotLon?.toString() ?: ""
        } catch (e: Exception) { message = e.message }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) return@rememberLauncherForActivityResult
        val fused: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
        // v1.8.0 — 2026-09-29: await() è sospendente, prima era chiamato
        // direttamente nella callback (non compilava, e mancava l'import).
        scope.launch {
            try {
                val loc = fused.lastLocation.await()
                if (loc != null) {
                    // Locale.US: il separatore decimale deve essere il punto,
                    // altrimenti toDoubleOrNull() al salvataggio fallisce.
                    depotLatText = String.format(java.util.Locale.US, "%.6f", loc.latitude)
                    depotLonText = String.format(java.util.Locale.US, "%.6f", loc.longitude)
                    message = "Posizione attuale impostata come sede: premi Salva opzioni."
                } else {
                    message = "Nessuna posizione recente disponibile."
                }
            } catch (e: Exception) { message = e.message }
        }
    }

    fun update(transform: (UserSettings) -> UserSettings) {
        val current = settings ?: return
        settings = transform(current)
    }

    PlumberScaffold(navController = navController, currentRoute = Routes.OPZIONI) { padding ->
        // v1.8.0 — 2026-09-29: verticalScroll, prima le sezioni in fondo (Salva, logout) erano fuori schermo.
        Column(modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("Opzioni")

            val s = settings
            if (s == null) {
                Text("Caricamento opzioni...")
            } else {
                ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        SectionTitle("Recap serale")
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("Orario recap: ${"%02d".format(s.recapTimeHour)}:${"%02d".format(s.recapTimeMinute)}", modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = { update { it.copy(recapTimeHour = (it.recapTimeHour + 23) % 24) } }) { Text("−1h") }
                            OutlinedButton(onClick = { update { it.copy(recapTimeHour = (it.recapTimeHour + 1) % 24) } }) { Text("+1h") }
                            OutlinedButton(onClick = { update { it.copy(recapTimeMinute = (it.recapTimeMinute + 15) % 60) } }) { Text("+15m") }
                        }
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("Soglia nuovo cliente: ${s.clientDetectionThresholdMinutes} min", modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = { update { it.copy(clientDetectionThresholdMinutes = (it.clientDetectionThresholdMinutes + 5).coerceAtLeast(5) ) } }) { Text("+5") }
                            OutlinedButton(onClick = { update { it.copy(clientDetectionThresholdMinutes = (it.clientDetectionThresholdMinutes - 5).coerceAtLeast(5) ) } }) { Text("−5") }
                        }
                        // v1.8.0 — 2026-09-29: prima { update { it.copy(x = it) } } — il secondo
                        // `it` era UserSettings, non il valore dello switch: non compilava.
                        SwitchRow("Conferma cliente in tempo reale (notifica)", s.realtimeConfirmationEnabled) { checked -> update { it.copy(realtimeConfirmationEnabled = checked) } }
                        SwitchRow("Invio recap via email alla conferma del recap", s.recapEmailEnabled) { checked -> update { it.copy(recapEmailEnabled = checked) } }
                        OutlinedTextField(
                            value = s.recapEmailAddress, onValueChange = { text -> update { it.copy(recapEmailAddress = text) } },
                            label = { Text("Email amministrazione per il recap") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        SectionTitle("Sede e pause (escluse dal rilevamento cliente)")
                        OutlinedTextField(value = depotLatText, onValueChange = { depotLatText = it }, label = { Text("Latitudine sede") }, singleLine = true)
                        OutlinedTextField(value = depotLonText, onValueChange = { depotLonText = it }, label = { Text("Longitudine sede") }, singleLine = true)
                        Button(onClick = { locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) {
                            Text("Usa posizione attuale come sede")
                        }

                        s.breaks.forEachIndexed { index, b ->
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(
                                    "${b.label}: ${"%02d".format(b.startHour)}:${"%02d".format(b.startMinute)} – ${"%02d".format(b.endHour)}:${"%02d".format(b.endMinute)}",
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedButton(onClick = { update { it.copy(breaks = it.breaks.filterIndexed { i, _ -> i != index }) } }) {
                                    Text("Rimuovi")
                                }
                            }
                        }
                        OutlinedButton(onClick = { update { it.copy(breaks = it.breaks + ScheduledBreak()) } }) {
                            Text("Aggiungi pausa (default 12:30–13:30)")
                        }
                    }
                }

                ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        SectionTitle("Squadra")
                        // v1.12.0 — 2026-09-29: campo rinominato (vedi UserSettings). Prima:
                        // SwitchRow("Condividi la mia posizione live sulla mappa", s.seeTeamLocationEnabled) { checked -> update { it.copy(seeTeamLocationEnabled = checked) } }
                        SwitchRow("Condividi la mia posizione live sulla mappa", s.shareOwnLocationEnabled) { checked -> update { it.copy(shareOwnLocationEnabled = checked) } }
                        SwitchRow("Vedi i recap confermati dei colleghi (nello Storico, default OFF)", s.seeTeammatesRecapEnabled) { checked -> update { it.copy(seeTeammatesRecapEnabled = checked) } }
                        Button(onClick = {
                            if (session == null) return@Button
                            scope.launch {
                                try { inviteCode = backendClient.createInvite(session.teamId); message = "Codice invito generato." }
                                catch (e: Exception) { message = e.message }
                            }
                        }) { Text("Genera codice invito per un collega") }
                        inviteCode?.let { code ->
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                // v1.12.0 — 2026-09-29: il codice è ora completo (squadra.codice):
                                // basta questo al collega per unirsi.
                                Text("Codice: $code (valido 7 giorni, da incollare in \"Unisciti\")", modifier = Modifier.weight(1f))
                                OutlinedButton(onClick = {
                                    val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Invito squadra", code))
                                }) { Text("Copia") }
                            }
                        }
                    }
                }

                ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        SectionTitle("Cronologia e privacy")
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("Retention posizioni: ${s.positionRetentionMonths} mesi", modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = { update { it.copy(positionRetentionMonths = (it.positionRetentionMonths + 6).coerceAtMost(60) ) } }) { Text("+6") }
                            OutlinedButton(onClick = { update { it.copy(positionRetentionMonths = (it.positionRetentionMonths - 6).coerceAtLeast(1) ) } }) { Text("−6") }
                        }
                        Spacer(Modifier.height(8.dp))
                        // v1.8.0 — 2026-09-29: accesso all'anagrafica clienti e al listino
                        // (prima la scheda Cliente non era raggiungibile da nessuna schermata).
                        OutlinedButton(onClick = { navController.navigate(Routes.CLIENTI) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Anagrafica clienti e listino articoli")
                        }
                        Row {
                            OutlinedButton(onClick = { navController.navigate(Routes.STORICO) }, modifier = Modifier.weight(1f)) { Text("Storico") }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { navController.navigate(Routes.DASHBOARD) }, modifier = Modifier.weight(1f)) { Text("Dashboard mensile") }
                        }
                    }
                }

                Button(
                    onClick = {
                        if (session == null) return@Button
                        scope.launch {
                            try {
                                val lat = depotLatText.trim().toDoubleOrNull()
                                val lon = depotLonText.trim().toDoubleOrNull()
                                if ((depotLatText.isNotBlank() && lat == null) || (depotLonText.isNotBlank() && lon == null)) {
                                    message = "Coordinate della sede non valide (usa il punto come separatore decimale)."
                                    return@launch
                                }
                                val saved = s.copy(depotLat = lat, depotLon = lon)
                                settingsRepository.save(session.teamId, session.uid, saved)
                                // v1.12.0 — 2026-09-29: condivisione spenta → i colleghi non
                                // vedono più il marker (prima restava "Attivo" fermo nel tempo).
                                if (!saved.shareOwnLocationEnabled) TeamRepository().markOffline(session.teamId, session.uid)
                                // v1.8.0 — 2026-09-29: il nuovo orario del recap serale
                                // entra in vigore subito (prima non c'era programmazione).
                                RecapScheduler.schedule(context, saved.recapTimeHour, saved.recapTimeMinute, reschedule = true)
                                message = "Opzioni salvate."
                            } catch (e: Exception) { message = e.message }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                ) { Text("Salva opzioni") }

                OutlinedButton(onClick = {
                    scope.launch {
                        try {
                            // v1.8.0 — 2026-09-29: prima il tracciamento restava attivo dopo il
                            // logout (e al fix successivo il service andava in crash senza
                            // sessione); ora si ferma, e si annulla il recap serale.
                            context.stopService(Intent(context, LocationTrackingService::class.java))
                            // v1.12.0 — 2026-09-29: il service chiude la sosta aperta in
                            // onDestroy con scritture non attese; facendo subito signOut
                            // quelle scritture restavano in coda all'utente uscito e la
                            // sosta risultava "in corso" per sempre. Si attende (max 5 s)
                            // che partano, poi si esce.
                            withTimeoutOrNull(5_000) {
                                delay(500) // lascia arrivare onDestroy del service
                                FirebaseFirestore.getInstance().waitForPendingWrites().await()
                            }
                            RecapScheduler.cancel(context)
                            AuthRepository(context).signOut()
                            SessionStore(context).clear()
                            CurrentSession.clear()
                            navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                        } catch (e: Exception) { message = e.message }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Esci dall'account") }
            }

            message?.let { Text(it) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}
