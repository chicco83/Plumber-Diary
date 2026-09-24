// OpzioniScreen.kt — v1.6.0 — 2026-09-23
//
// Versione precedente (v1.0.0 — 2026-09-20 00:10 UTC): stub con solo il titolo,
// sostituita il 2026-09-23 dalla schermata completa: tutte le UserSettings
// (requisiti 6/13/14) editabili, sede/deposito con "usa posizione attuale",
// pause orarie, sezione squadra con codice invito, link a Storico/Dashboard e
// logout. Scrittura unica su Firestore al pulsante "Salva".
package com.plumberdiary.app.ui.opzioni

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.plumberdiary.app.session.CurrentSession
import com.plumberdiary.app.session.SessionStore
import com.plumberdiary.app.ui.Routes
import com.plumberdiary.app.ui.common.PlumberScaffold
import com.plumberdiary.app.ui.common.rememberActiveSession
import kotlinx.coroutines.launch

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
        try {
            fused.lastLocation.await().let { loc ->
                if (loc != null) {
                    depotLatText = "%.6f".format(loc.latitude)
                    depotLonText = "%.6f".format(loc.longitude)
                    message = "Posizione attuale impostata come sede."
                } else message = "Nessuna posizione recente disponibile."
            }
        } catch (e: Exception) { message = e.message }
    }

    fun update(transform: (UserSettings) -> UserSettings) {
        val current = settings ?: return
        settings = transform(current)
    }

    PlumberScaffold(navController = navController, currentRoute = Routes.OPZIONI) { padding ->
        Column(modifier = Modifier.padding(padding).padding(20.dp)) {
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
                            OutlinedButton(onClick = { update { it.copy(recapTimeHour = (it.recapTimeHour + 1) % 24) } }) { Text("+1h") }
                        }
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("Soglia nuovo cliente: ${s.clientDetectionThresholdMinutes} min", modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = { update { it.copy(clientDetectionThresholdMinutes = (it.clientDetectionThresholdMinutes + 5).coerceAtLeast(5) ) } }) { Text("+5") }
                            OutlinedButton(onClick = { update { it.copy(clientDetectionThresholdMinutes = (it.clientDetectionThresholdMinutes - 5).coerceAtLeast(5) ) } }) { Text("−5") }
                        }
                        SwitchRow("Conferma cliente in tempo reale (notifica)", s.realtimeConfirmationEnabled) { update { it.copy(realtimeConfirmationEnabled = it) } }
                        SwitchRow("Invio recap giornaliero via email", s.recapEmailEnabled) { update { it.copy(recapEmailEnabled = it) } }
                        OutlinedTextField(
                            value = s.recapEmailAddress, onValueChange = { update { it.copy(recapEmailAddress = it) } },
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
                        SwitchRow("Condividi la mia posizione live sulla mappa", s.seeTeamLocationEnabled) { update { it.copy(seeTeamLocationEnabled = it) } }
                        SwitchRow("Vedi i recap confermati dei colleghi (default OFF)", s.seeTeammatesRecapEnabled) { update { it.copy(seeTeammatesRecapEnabled = it) } }
                        Button(onClick = {
                            if (session == null) return@Button
                            scope.launch {
                                try { inviteCode = backendClient.createInvite(session.teamId); message = "Codice invito generato." }
                                catch (e: Exception) { message = e.message }
                            }
                        }) { Text("Genera codice invito per un collega") }
                        inviteCode?.let { code ->
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text("Codice: $code", modifier = Modifier.weight(1f))
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
                                settingsRepository.save(
                                    session.teamId, session.uid,
                                    s.copy(depotLat = lat, depotLon = lon),
                                )
                                message = "Opzioni salvate."
                            } catch (e: Exception) { message = e.message }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                ) { Text("Salva opzioni") }

                OutlinedButton(onClick = {
                    scope.launch {
                        try {
                            AuthRepository(context).signOut()
                            SessionStore(context).clear()
                            CurrentSession.clear()
                            navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                        } catch (e: Exception) { message = e.message }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Esci dal conto") }
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
