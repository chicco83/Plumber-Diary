// ClienteScreen.kt — v1.9.0 — 2026-09-29 (v1.8.0 — 2026-09-29; v1.7.0 — 2026-09-23)
//
// Versione precedente (v1.0.0 — 2026-09-20 00:10 UTC): stub con solo il titolo,
// sostituita il 2026-09-23 dalla scheda completa (requisiti 8/9/10/11):
// anagrafica condivisa a livello squadra, foto da galleria, listino articoli
// condiviso e flusso "mandatino delle ore" PDF con CONFERMA ESPLICITA prima
// dell'invio (mai automatico) tramite l'endpoint Vercel send-mandatino.
package com.plumberdiary.app.ui.cliente

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.plumberdiary.app.auth.AuthRepository
import com.plumberdiary.app.data.BackendClient
import com.plumberdiary.app.data.BackendConfig
import com.plumberdiary.app.data.FirestorePaths
import com.plumberdiary.app.data.model.Article
import com.plumberdiary.app.data.model.ClientRecord
import com.plumberdiary.app.data.model.Stop
import com.plumberdiary.app.data.repository.ArticleRepository
import com.plumberdiary.app.data.repository.ClientRepository
import com.plumberdiary.app.data.repository.StopRepository
import com.plumberdiary.app.data.repository.TeamRepository
import com.plumberdiary.app.pdf.MandatinoPdfGenerator
import com.plumberdiary.app.photo.PhotoUploader
import com.plumberdiary.app.ui.common.Format
import com.plumberdiary.app.ui.common.PhotoGrid
import com.plumberdiary.app.ui.common.rememberActiveSession
import java.io.File
import java.util.Base64
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ClienteScreen(navController: NavHostController, clientId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = rememberActiveSession()

    var client by remember { mutableStateOf<ClientRecord?>(null) }
    var articles by remember { mutableStateOf<List<Article>>(emptyList()) }
    var newArticleCode by remember { mutableStateOf("") }
    var newArticleDesc by remember { mutableStateOf("") }
    var newArticlePrice by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    // Stato del flusso mandatino (requisito 9: mai invio automatico).
    var showMandatinoConfirm by remember { mutableStateOf(false) }
    var mandatinoPeriod by remember { mutableStateOf("Questo mese") }
    var mandatinoRecipient by remember { mutableStateOf("") }
    var mandatinoStops by remember { mutableStateOf<List<Stop>>(emptyList()) }
    var mandatinoSending by remember { mutableStateOf(false) }
    // v1.9.0 — 2026-09-29: opzione "includi le ore dei colleghi" scelta al
    // momento della creazione (default OFF: solo le proprie ore, come prima).
    var mandatinoIncludeTeam by remember { mutableStateOf(false) }
    var mandatinoTechnicians by remember { mutableStateOf<Map<String, String>>(emptyMap()) } // stopId -> tecnico
    var mandatinoLoading by remember { mutableStateOf(false) }

    val clientRepository = remember { ClientRepository() }
    val articleRepository = remember { ArticleRepository() }
    val stopRepository = remember { StopRepository() }
    val backendClient = remember { BackendClient(BackendConfig.BASE_URL, AuthRepository(context)) }

    LaunchedEffect(clientId) {
        if (session == null) return@LaunchedEffect
        try {
            client = clientRepository.getById(session.teamId, clientId)
            articles = articleRepository.getAll(session.teamId)
        } catch (e: Exception) { message = e.message }
    }

    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(4),
    ) { uris ->
        val c = client ?: return@rememberLauncherForActivityResult
        if (session == null || uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            message = "Caricamento foto..."
            try {
                val uploader = PhotoUploader(context)
                val newIds = mutableListOf<String>()
                for (uri in uris) {
                    val photoId = java.util.UUID.randomUUID().toString()
                    // Le foto della scheda cliente usano solo la copia display
                    // (l'alta risoluzione serve alla mail di recap degli interventi).
                    // v1.8.0 — 2026-09-29: il codice contraddiceva questo commento e
                    // caricava anche l'originale HD su "...display.jpg.orig", un
                    // percorso che il cleanup non cancella mai: i 5 GB gratuiti di
                    // Storage si sarebbero riempiti di copie HD inutili.
                    // FirestorePaths.clientPhotoDisplay(session.teamId, c.id, photoId) + ".orig",
                    uploader.upload(
                        uri,
                        FirestorePaths.clientPhotoDisplay(session.teamId, c.id, photoId),
                        null,
                        photoId,
                    )
                    newIds += photoId
                }
                // v1.8.0 — 2026-09-29: salvate subito sul cliente (arrayUnion);
                // prima restavano solo nello stato della schermata fino a "Salva
                // anagrafica", altrimenti andavano perse.
                clientRepository.addPhotoIds(session.teamId, c.id, newIds)
                client = (client ?: c).let { it.copy(photoIds = it.photoIds + newIds) }
                message = "${uris.size} foto allegate alla scheda."
            } catch (e: Exception) { message = "Errore foto: ${e.message}" }
        }
    }

    // Versione precedente (v1.7.0 — 2026-09-23), sostituita il 2026-09-29:
    // leggeva solo le soste di chi genera il mandatino.
    //
    // fun prepareMandatino() {
    //     ...
    //     val all = stopRepository.getStopsForDay(session.teamId, session.uid, fromMillis, System.currentTimeMillis())
    //     mandatinoStops = all.filter { it.clientId == c.id && it.endedAt > 0L }.sortedBy { it.startedAt }
    //     mandatinoRecipient = c.hoursReportEmail.ifBlank { "" }
    //     showMandatinoConfirm = true
    // }

    /**
     * Carica gli interventi del periodo presso il cliente. Con [includeTeam]
     * legge anche le soste dei colleghi (le regole Firestore lo consentono ai
     * membri della squadra), ma solo i giorni che ciascun collega ha già
     * confermato nel proprio recap: orari e note non ancora rivisti non
     * finiscono in un documento per il cliente. Le proprie soste sono incluse
     * anche se non confermate, come prima.
     */
    suspend fun loadMandatinoStops(c: ClientRecord, includeTeam: Boolean) {
        val s = session ?: return
        val cal = Calendar.getInstance()
        val fromMillis = if (mandatinoPeriod == "Questo mese") {
            cal.set(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        } else {
            System.currentTimeMillis() - 30L * 24 * 3_600_000
        }
        val now = System.currentTimeMillis()
        fun List<Stop>.forClient() = filter { it.clientId == c.id && it.endedAt > 0L }

        if (!includeTeam) {
            mandatinoStops = stopRepository.getStopsForDay(s.teamId, s.uid, fromMillis, now).forClient().sortedBy { it.startedAt }
            mandatinoTechnicians = emptyMap()
            return
        }

        val technicians = mutableMapOf<String, String>()
        val collected = mutableListOf<Stop>()
        for (member in TeamRepository().getMembers(s.teamId)) {
            val name = member.displayName.ifBlank { member.email.ifBlank { "Tecnico" } }
            val stops = stopRepository.getStopsForDay(s.teamId, member.uid, fromMillis, now).forClient()
                .filter { member.uid == s.uid || it.confirmedInRecap }
            stops.forEach { technicians[it.id] = name }
            collected += stops
        }
        mandatinoStops = collected.sortedBy { it.startedAt }
        mandatinoTechnicians = technicians
    }

    fun prepareMandatino() {
        val c = client ?: return
        if (session == null) return
        scope.launch {
            try {
                mandatinoIncludeTeam = false
                loadMandatinoStops(c, includeTeam = false)
                mandatinoRecipient = c.hoursReportEmail.ifBlank { "" }
                showMandatinoConfirm = true
            } catch (e: Exception) { message = e.message }
        }
    }

    // v1.8.0 — 2026-09-29: verticalScroll, prima listino e pulsanti in fondo erano fuori schermo.
    Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, null) }
            Text("Scheda cliente", modifier = Modifier.weight(1f))
        }

        val c = client
        if (c == null) {
            Text("Caricamento... o cliente non trovato.")
        } else {
            ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Anagrafica (condivisa con la squadra)", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value = c.name, onValueChange = { client = c.copy(name = it) }, label = { Text("Nome") })
                    OutlinedTextField(value = c.phone, onValueChange = { client = c.copy(phone = it) }, label = { Text("Telefono") })
                    OutlinedTextField(value = c.fiscalCode, onValueChange = { client = c.copy(fiscalCode = it) }, label = { Text("P.IVA / C.F.") })
                    OutlinedTextField(value = c.defaultAddressLabel, onValueChange = { client = c.copy(defaultAddressLabel = it) }, label = { Text("Indirizzo abituale") })
                    OutlinedTextField(value = c.hoursReportEmail, onValueChange = { client = c.copy(hoursReportEmail = it) }, label = { Text("Email per il mandatino ore") })
                    Button(onClick = {
                        if (session == null) return@Button
                        scope.launch {
                            // v1.8.0 — 2026-09-29: solo i campi anagrafici (prima set dell'intero
                            // documento condiviso: cancellava foto/posizioni aggiunte nel frattempo
                            // da un collega).
                            try { clientRepository.updateAnagrafica(session.teamId, c); message = "Anagrafica salvata." }
                            catch (e: Exception) { message = e.message }
                        }
                    }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Salva anagrafica") }
                }
            }

            ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Foto della scheda", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    Text("${c.photoIds.size} foto allegate")
                    // v1.7.0 — 2026-09-24: miniature delle foto già caricate (prima mancavano).
                    c.photoIds.takeLast(8).let { ids ->
                        PhotoGrid(ids) { id -> FirestorePaths.clientPhotoDisplay(session!!.teamId, c.id, id) }
                    }
                    Button(onClick = { photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Aggiungi foto dalla galleria") }
                }
            }

            ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Mandatino delle ore", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    Text("PDF di riepilogo ore/materiali, inviato SOLO dopo conferma esplicita (requisito 9).")
                    Row {
                        OutlinedButton(onClick = { mandatinoPeriod = "Questo mese"; prepareMandatino() }) { Text("Questo mese") }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { mandatinoPeriod = "Ultimi 30 giorni"; prepareMandatino() }) { Text("Ultimi 30 gg") }
                    }
                }
            }

            ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Listino articoli (condiviso)", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    articles.forEach { a ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${a.code} — ${a.description}")
                                Text(Format.euros(a.unitPrice))
                            }
                            IconButton(onClick = {
                                if (session == null) return@IconButton
                                scope.launch {
                                    try { articleRepository.delete(session.teamId, a.id); articles = articleRepository.getAll(session.teamId) }
                                    catch (e: Exception) { message = e.message }
                                }
                            }) { Icon(Icons.Filled.Delete, null) }
                        }
                    }
                    if (articles.isEmpty()) Text("Listino vuoto.")
                    OutlinedTextField(value = newArticleCode, onValueChange = { newArticleCode = it }, label = { Text("Codice") })
                    OutlinedTextField(value = newArticleDesc, onValueChange = { newArticleDesc = it }, label = { Text("Descrizione") })
                    OutlinedTextField(value = newArticlePrice, onValueChange = { newArticlePrice = it }, label = { Text("Prezzo unitario (€)") })
                    OutlinedButton(onClick = {
                        if (session == null) return@OutlinedButton
                        val price = newArticlePrice.replace(',', '.').toDoubleOrNull() ?: 0.0
                        scope.launch {
                            try {
                                articleRepository.upsert(session.teamId, Article(
                                    code = newArticleCode.trim(), description = newArticleDesc.trim(), unitPrice = price,
                                ))
                                articles = articleRepository.getAll(session.teamId)
                                newArticleCode = ""; newArticleDesc = ""; newArticlePrice = ""
                            } catch (e: Exception) { message = e.message }
                        }
                    }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Aggiungi al listino") }
                }
            }

            message?.let { Text(it) }
        }
    }

    if (showMandatinoConfirm && client != null) {
        val totalMinutes = mandatinoStops.sumOf { Format.durationMinutes(it.startedAt, it.endedAt) }
        val totalMaterials = mandatinoStops.sumOf { s -> s.articleLines.sumOf { it.unitPrice * it.quantity } }
        AlertDialog(
            onDismissRequest = { showMandatinoConfirm = false },
            title = { Text("Conferma invio mandatino") },
            text = {
                Column {
                    Text("Cliente: ${client!!.name}")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = mandatinoIncludeTeam,
                            enabled = !mandatinoLoading && !mandatinoSending,
                            onCheckedChange = { checked ->
                                val cc = client ?: return@Checkbox
                                mandatinoIncludeTeam = checked
                                mandatinoLoading = true
                                scope.launch {
                                    try { loadMandatinoStops(cc, checked) }
                                    catch (e: Exception) { message = e.message }
                                    finally { mandatinoLoading = false }
                                }
                            },
                        )
                        Text("Includi le ore dei colleghi (solo giorni già confermati)")
                    }
                    Text(
                        if (mandatinoLoading) "Caricamento interventi..."
                        else "Periodo: $mandatinoPeriod — ${mandatinoStops.size} interventi, ${Format.durationLabel(totalMinutes)}",
                    )
                    if (mandatinoIncludeTeam && !mandatinoLoading) {
                        mandatinoStops.groupBy { mandatinoTechnicians[it.id] ?: "—" }.forEach { (name, list) ->
                            Text("  $name: ${list.size} interventi, ${Format.durationLabel(list.sumOf { Format.durationMinutes(it.startedAt, it.endedAt) })}")
                        }
                    }
                    Text("Materiali: ${Format.euros(totalMaterials)}")
                    OutlinedTextField(
                        value = mandatinoRecipient, onValueChange = { mandatinoRecipient = it },
                        label = { Text("Destinatario email") }, singleLine = true,
                    )
                    if (mandatinoStops.isEmpty()) Text("Attenzione: nessun intervento trovato nel periodo.", color = androidx.compose.ui.graphics.Color.Red)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val s = session; val cc = client!!
                    if (s == null || mandatinoLoading || mandatinoRecipient.isBlank() || mandatinoStops.isEmpty()) return@Button
                    mandatinoSending = true
                    scope.launch {
                        try {
                            // v1.8.0 — 2026-09-29: generazione PDF fuori dal main thread.
                            val base64 = withContext(Dispatchers.IO) {
                                val pdfFile = File(context.cacheDir, "mandatino-${cc.id}.pdf")
                                java.io.FileOutputStream(pdfFile).use { out ->
                                    MandatinoPdfGenerator.generate(
                                        out, cc, mandatinoStops, mandatinoPeriod,
                                        technicianByStopId = if (mandatinoIncludeTeam) mandatinoTechnicians else null,
                                    )
                                }
                                Base64.getEncoder().encodeToString(pdfFile.readBytes())
                            }
                            backendClient.sendMandatino(s.teamId, cc.id, mandatinoRecipient.trim(), mandatinoPeriod, base64)
                            message = "Mandatino inviato a ${mandatinoRecipient.trim()}"
                        } catch (e: Exception) { message = "Invio mandatino fallito: ${e.message}" }
                        finally { mandatinoSending = false; showMandatinoConfirm = false }
                    }
                }) {
                    Text(if (mandatinoSending) "Invio..." else "Conferma e invia")
                }
            },
            dismissButton = { TextButton(onClick = { showMandatinoConfirm = false }) { Text("Annulla") } },
        )
    }
}
