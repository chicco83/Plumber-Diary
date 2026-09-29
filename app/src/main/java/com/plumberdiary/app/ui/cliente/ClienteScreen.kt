// ClienteScreen.kt — v1.11.0 — 2026-09-29 (v1.10.0 / v1.9.0 / v1.8.0 — 2026-09-29; v1.7.0 — 2026-09-23)
//
// Versione precedente (v1.0.0 — 2026-09-20 00:10 UTC): stub con solo il titolo,
// sostituita il 2026-09-23 dalla scheda completa (requisiti 8/9/10/11):
// anagrafica condivisa a livello squadra, foto da galleria, listino articoli
// condiviso e accesso al "mandatino delle ore" (dalla v1.11.0 in una
// schermata propria, ui/mandatino/MandatinoScreen.kt).
package com.plumberdiary.app.ui.cliente

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.plumberdiary.app.data.FirestorePaths
import com.plumberdiary.app.data.model.Article
import com.plumberdiary.app.data.model.ClientRecord
import com.plumberdiary.app.data.repository.ArticleRepository
import com.plumberdiary.app.data.repository.ClientRepository
import com.plumberdiary.app.photo.PhotoUploader
import com.plumberdiary.app.ui.Routes
import com.plumberdiary.app.ui.common.Format
import com.plumberdiary.app.ui.common.PhotoGrid
import com.plumberdiary.app.ui.common.rememberActiveSession
import kotlinx.coroutines.launch

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

    val clientRepository = remember { ClientRepository() }
    val articleRepository = remember { ArticleRepository() }

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

    // v1.11.0 — 2026-09-29: loadMandatinoStops() e prepareMandatino() spostate in
    // ui/mandatino/MandatinoScreen.kt (versioni precedenti v1.7.0–v1.10.0 nella
    // storia git di questo file).

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

            // v1.11.0 — 2026-09-29: mandatino e "rapportino con firma" sono lo stesso
            // documento: il flusso (periodo, ore dei colleghi, firma, invio) è ora nella
            // schermata Mandatino. Qui prima c'erano i pulsanti Oggi/Mese/30 gg che
            // aprivano una finestra di conferma senza firma.
            ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Mandatino delle ore", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    Text("Ore, materiali e firma del cliente per accettazione, di norma sul posto a fine intervento. Inviato SOLO dopo la tua conferma.")
                    Button(
                        onClick = { navController.navigate(Routes.mandatino(c.id)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) { Text("Crea mandatino") }
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

    // v1.11.0 — 2026-09-29: la finestra di conferma del mandatino è stata spostata,
    // insieme a tutta la sua logica, in ui/mandatino/MandatinoScreen.kt.
}
