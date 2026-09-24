// DettaglioScreen.kt — v1.6.0 — 2026-09-23
//
// Versione precedente (v1.0.0 — 2026-09-20 00:10 UTC): stub con solo il titolo,
// sostituita il 2026-09-23 dal form completo di una singola sosta (requisiti
// 3/4/5/7/10/16): correzione orari con stepper, scelta/creazione cliente,
// note libere, promemoria, materiali dal listino condiviso e foto intervento
// da galleria (doppia risoluzione original/display, requisito 12).
package com.plumberdiary.app.ui.dettaglio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
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
import com.plumberdiary.app.data.FirestorePaths
import com.plumberdiary.app.data.model.ArticleLine
import com.plumberdiary.app.data.model.ClientRecord
import com.plumberdiary.app.data.model.Stop
import com.plumberdiary.app.data.model.StopKind
import com.plumberdiary.app.data.repository.ArticleRepository
import com.plumberdiary.app.data.repository.ClientRepository
import com.plumberdiary.app.data.repository.StopRepository
import com.plumberdiary.app.photo.PhotoUploader
import com.plumberdiary.app.ui.Routes
import com.plumberdiary.app.ui.common.Format
import com.plumberdiary.app.ui.common.PhotoGrid
import com.plumberdiary.app.ui.common.rememberActiveSession
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun DettaglioScreen(navController: NavHostController, stopId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = rememberActiveSession()

    var stop by remember { mutableStateOf<Stop?>(null) }
    var clients by remember { mutableStateOf<List<ClientRecord>>(emptyList()) }
    var articles by remember { mutableStateOf<List<com.plumberdiary.app.data.model.Article>>(emptyList()) }
    var showClientMenu by remember { mutableStateOf(false) }
    var showNewClient by remember { mutableStateOf(false) }
    var newClientName by remember { mutableStateOf("") }
    var newClientPhone by remember { mutableStateOf("") }
    var showArticleMenu by remember { mutableStateOf(false) }
    var photoStatus by remember { mutableStateOf<String?>(null) }
    var saveMessage by remember { mutableStateOf<String?>(null) }

    val stopRepository = remember { StopRepository() }
    val clientRepository = remember { ClientRepository() }

    LaunchedEffect(stopId) {
        if (session == null) return@LaunchedEffect
        try {
            stop = stopRepository.getStop(session.teamId, session.uid, stopId)
            clients = clientRepository.getAll(session.teamId)
            articles = ArticleRepository().getAll(session.teamId)
        } catch (e: Exception) { saveMessage = e.message }
    }

    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(4),
    ) { uris ->
        val current = stop ?: return@rememberLauncherForActivityResult
        if (session == null || uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            photoStatus = "Caricamento foto..."
            try {
                val uploader = PhotoUploader(context)
                val newPhotoIds = mutableListOf<String>()
                for (uri in uris) {
                    val photoId = UUID.randomUUID().toString()
                    uploader.upload(
                        uri,
                        FirestorePaths.stopPhotoDisplay(session.teamId, session.uid, current.id, photoId),
                        FirestorePaths.stopPhotoOriginal(session.teamId, session.uid, current.id, photoId),
                        photoId,
                    )
                    newPhotoIds += photoId
                }
                // Gli id salvati sulla sosta sono esattamente quelli usati come
                // nome file in Storage (stesso UUID per original e display).
                stop = current.copy(photoIds = current.photoIds + newPhotoIds)
                photoStatus = "${uris.size} foto caricate (originale + display)."
            } catch (e: Exception) { photoStatus = "Errore caricamento foto: ${e.message}" }
        }
    }

    Column(modifier = Modifier.padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, null) }
            Text("Dettaglio posizione", modifier = Modifier.weight(1f))
        }

        val current = stop
        if (current == null) {
            Text("Caricamento... o sosta non trovata.")
        } else {
            ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Orari", style = MaterialTheme.typography.titleMedium)
                    TimeStepper(
                        label = "Inizio",
                        valueMillis = current.startedAt,
                        onValueChange = { stop = current.copy(startedAt = it) },
                    )
                    if (current.endedAt > 0L) {
                        TimeStepper(
                            label = "Fine",
                            valueMillis = current.endedAt,
                            onValueChange = { stop = current.copy(endedAt = it) },
                        )
                    } else {
                        Text("Sosta ancora in corso")
                    }
                }
            }

            ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Cliente", style = MaterialTheme.typography.titleMedium)
                    val currentClient = clients.firstOrNull { it.id == current.clientId }
                    Box {
                        OutlinedButton(onClick = { showClientMenu = true }) {
                            Text(currentClient?.name ?: "Nessun cliente — scegli")
                        }
                        DropdownMenu(expanded = showClientMenu, onDismissRequest = { showClientMenu = false }) {
                            clients.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c.name) },
                                    onClick = {
                                        stop = current.copy(clientId = c.id, kind = StopKind.CLIENT)
                                        showClientMenu = false
                                    },
                                )
                            }
                            DropdownMenuItem(text = { Text("＋ Nuovo cliente") }, onClick = {
                                showNewClient = true; showClientMenu = false
                            })
                        }
                    }
                    if (current.clientId != null) {
                        TextButton(onClick = { stop = current.copy(clientId = null, kind = StopKind.UNRESOLVED) }) {
                            Text("Rimuovi cliente")
                        }
                    }
                    if (showNewClient) {
                        OutlinedTextField(
                            value = newClientName, onValueChange = { newClientName = it },
                            label = { Text("Nome nuovo cliente") }, modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = newClientPhone, onValueChange = { newClientPhone = it },
                            label = { Text("Telefono (opzionale)") }, modifier = Modifier.fillMaxWidth(),
                        )
                        Button(onClick = {
                            if (session == null || newClientName.isBlank()) return@Button
                            scope.launch {
                                try {
                                    val id = clientRepository.upsert(
                                        session.teamId,
                                        ClientRecord(name = newClientName.trim(), phone = newClientPhone.trim(),
                                            createdBy = session.uid, createdAt = System.currentTimeMillis()),
                                    )
                                    stop = current.copy(clientId = id, kind = StopKind.CLIENT)
                                    clients = clientRepository.getAll(session.teamId)
                                    showNewClient = false; newClientName = ""; newClientPhone = ""
                                } catch (e: Exception) { saveMessage = e.message }
                            }
                        }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Salva e associa") }
                    }
                }
            }

            ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Note e promemoria", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = current.notes, onValueChange = { stop = current.copy(notes = it) },
                        label = { Text("Note intervento") }, minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = current.reminderText, onValueChange = { stop = current.copy(reminderText = it) },
                        label = { Text("Promemoria (es. 'chiamare per preventivo')") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }

            ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Materiali", style = MaterialTheme.typography.titleMedium)
                    current.articleLines.forEachIndexed { index, line ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${line.code} — ${line.description}")
                                Text("${Format.euros(line.unitPrice)} × ${line.quantity}")
                            }
                            OutlinedButton(onClick = {
                                val lines = current.articleLines.toMutableList()
                                if (lines[index].quantity > 1) lines[index] = lines[index].copy(quantity = lines[index].quantity - 1)
                                stop = current.copy(articleLines = lines)
                            }) { Text("−") }
                            OutlinedButton(onClick = {
                                val lines = current.articleLines.toMutableList()
                                lines[index] = lines[index].copy(quantity = lines[index].quantity + 1)
                                stop = current.copy(articleLines = lines)
                            }) { Text("＋") }
                            IconButton(onClick = {
                                stop = current.copy(articleLines = current.articleLines.filterIndexed { i, _ -> i != index })
                            }) { Icon(Icons.Filled.Delete, null) }
                        }
                    }
                    if (current.articleLines.isEmpty()) Text("Nessun materiale registrato.")
                    Box {
                        OutlinedButton(onClick = { showArticleMenu = true }, modifier = Modifier.padding(top = 8.dp)) {
                            Icon(Icons.Filled.Add, null); Spacer(Modifier.height(0.dp)); Text("Aggiungi dal listino")
                        }
                        DropdownMenu(expanded = showArticleMenu, onDismissRequest = { showArticleMenu = false }) {
                            articles.forEach { a ->
                                DropdownMenuItem(
                                    text = { Text("${a.code} — ${a.description} (${Format.euros(a.unitPrice)})") },
                                    onClick = {
                                        stop = current.copy(articleLines = current.articleLines + ArticleLine(
                                            articleId = a.id, code = a.code, description = a.description, unitPrice = a.unitPrice,
                                        ))
                                        showArticleMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
            }

            ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Foto intervento", style = MaterialTheme.typography.titleMedium)
                    Text("${current.photoIds.size} foto allegate (originale HD + display)")
                    // v1.6.0 — 2026-09-24: miniature delle foto già caricate (prima mancavano).
                    current.photoIds.takeLast(8).let { ids ->
                        PhotoGrid(ids) { id -> FirestorePaths.stopPhotoDisplay(session!!.teamId, current.id, id) }
                    }
                    Button(onClick = { photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Aggiungi foto dalla galleria") }
                    photoStatus?.let { Text(it) }
                }
            }

            Button(
                onClick = {
                    val s = stop ?: return@Button
                    if (session == null) return@Button
                    scope.launch {
                        try {
                            stopRepository.upsert(session.teamId, session.uid, s)
                            if (s.clientId != null) {
                                val c = clientRepository.getById(session.teamId, s.clientId!!)
                                if (c != null) clientRepository.recordVisit(session.teamId, c, s.lat, s.lon, System.currentTimeMillis())
                            }
                            saveMessage = "Salvato."
                        } catch (e: Exception) { saveMessage = e.message }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            ) { Text("Salva modifiche") }

            saveMessage?.let { Text(it) }
        }
    }
}

/** Stepper orario: −1h / −5m / +5m / +1h, valore mostrato HH:mm. */
@Composable
private fun TimeStepper(label: String, valueMillis: Long, onValueChange: (Long) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { onValueChange(valueMillis - 3_600_000L) }) { Text("−1h") }
        OutlinedButton(onClick = { onValueChange(valueMillis - 5 * 60_000L) }) { Text("−5m") }
        OutlinedButton(onClick = { onValueChange(valueMillis + 5 * 60_000L) }) { Text("+5m") }
        OutlinedButton(onClick = { onValueChange(valueMillis + 3_600_000L) }) { Text("+1h") }
        Spacer(Modifier.height(8.dp))
        Text(Format.time(valueMillis))
    }
}
