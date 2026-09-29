// MandatinoScreen.kt — v1.11.0 — 2026-09-29
//
// Requisiti 9 e 15, unificati: il "mandatino delle ore" è il documento che il
// cliente accetta SUL POSTO a fine intervento — ore (tue e, a scelta, dei
// colleghi), note, materiali, firma del cliente per accettazione (sempre
// saltabile) — inviato alla sua email solo dopo la conferma esplicita del
// tecnico, oppure condiviso con un'altra app.
//
// Nasce il 2026-09-29 dalla fusione di due schermate che descrivevano lo
// stesso documento (errore di progettazione, chiarito dall'utente):
//  - il flusso mandatino dentro ClienteScreen (v1.10.0: periodo, ore dei
//    colleghi, soste in corso, soste senza cliente nella posizione nota,
//    finestra di conferma e invio email), qui ripreso invariato nella logica;
//  - RapportinoScreen (v1.8.0: firma su canvas, PDF di una sola sosta,
//    condivisione), rimossa: la firma e la condivisione sono ora qui.
package com.plumberdiary.app.ui.mandatino

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import com.plumberdiary.app.auth.AuthRepository
import com.plumberdiary.app.data.BackendClient
import com.plumberdiary.app.data.BackendConfig
import com.plumberdiary.app.data.model.ClientRecord
import com.plumberdiary.app.data.model.Stop
import com.plumberdiary.app.data.model.StopKind
import com.plumberdiary.app.data.repository.ClientRepository
import com.plumberdiary.app.data.repository.StopRepository
import com.plumberdiary.app.data.repository.TeamRepository
import com.plumberdiary.app.location.ClientMatcher
import com.plumberdiary.app.pdf.MandatinoPdfGenerator
import com.plumberdiary.app.ui.common.Format
import com.plumberdiary.app.ui.common.rememberActiveSession
import java.io.File
import java.io.FileOutputStream
import java.util.Base64
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Period(val label: String) { TODAY("Oggi"), MONTH("Questo mese"), LAST_30("Ultimi 30 giorni") }

@Composable
fun MandatinoScreen(navController: NavHostController, clientId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = rememberActiveSession()

    val stopRepository = remember { StopRepository() }
    val backendClient = remember { BackendClient(BackendConfig.BASE_URL, AuthRepository(context)) }

    var client by remember { mutableStateOf<ClientRecord?>(null) }
    var period by remember { mutableStateOf(Period.TODAY) } // caso tipico: sul posto, a fine intervento
    var includeTeam by remember { mutableStateOf(false) }
    var stops by remember { mutableStateOf<List<Stop>>(emptyList()) }
    var technicians by remember { mutableStateOf<Map<String, String>>(emptyMap()) } // stopId -> tecnico
    var matchedByPosition by remember { mutableStateOf(0) }
    var inProgress by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var recipient by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    // Firma: tratti completati + tratto in corso, in pixel dell'area di firma.
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var signatureAreaSize by remember { mutableStateOf(IntSize.Zero) }

    /**
     * Interventi del periodo presso il cliente, indipendenti dal recap serale
     * (non ancora compilato quando il mandatino si fa sul posto). Inclusa una
     * sosta se associata al cliente, oppure se senza cliente (e non sede/pausa)
     * ma in una sua posizione nota — contate a parte e segnalate. Soste aperte
     * conteggiate fino ad ora (fino all'ultimo fix se il tracciamento è fermo
     * da oltre 30 minuti). Con i colleghi: stesse regole per ogni membro.
     */
    suspend fun load(c: ClientRecord) {
        val s = session ?: return
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val fromMillis = when (period) {
            Period.TODAY -> cal.timeInMillis
            Period.MONTH -> cal.apply { set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
            Period.LAST_30 -> System.currentTimeMillis() - 30L * 24 * 3_600_000
        }
        val now = System.currentTimeMillis()
        val staleMillis = 30L * 60_000
        var byPosition = 0
        var open = 0

        suspend fun stopsAtClient(uid: String): List<Stop> =
            stopRepository.getStopsForDay(s.teamId, uid, fromMillis, now).mapNotNull { stop ->
                val associated = stop.clientId == c.id
                val unassignedHere = stop.clientId == null &&
                    stop.kind != StopKind.DEPOT && stop.kind != StopKind.BREAK &&
                    ClientMatcher.findSuggestedClient(stop.lat, stop.lon, listOf(c)) != null
                if (!associated && !unassignedHere) return@mapNotNull null
                if (unassignedHere) byPosition++
                if (stop.endedAt > 0L) {
                    stop
                } else {
                    open++
                    val lastSeen = if (stop.lastFixAt > 0L) stop.lastFixAt else stop.startedAt
                    // Solo per il documento: nessuna scrittura su Firestore.
                    stop.copy(endedAt = if (now - lastSeen <= staleMillis) now else lastSeen)
                }
            }

        val names = mutableMapOf<String, String>()
        val collected = mutableListOf<Stop>()
        if (includeTeam) {
            for (member in TeamRepository().getMembers(s.teamId)) {
                val name = member.displayName.ifBlank { member.email.ifBlank { "Tecnico" } }
                val memberStops = stopsAtClient(member.uid)
                memberStops.forEach { names[it.id] = name }
                collected += memberStops
            }
        } else {
            collected += stopsAtClient(s.uid)
        }
        stops = collected.sortedBy { it.startedAt }
        technicians = if (includeTeam) names else emptyMap()
        matchedByPosition = byPosition
        inProgress = open
    }

    LaunchedEffect(clientId) {
        val s = session ?: return@LaunchedEffect
        try {
            val c = ClientRepository().getById(s.teamId, clientId)
            client = c
            recipient = c?.hoursReportEmail.orEmpty()
        } catch (e: Exception) { message = e.message }
    }

    // Ricarica a ogni cambio di cliente, periodo o opzione colleghi.
    LaunchedEffect(client, period, includeTeam) {
        val c = client ?: return@LaunchedEffect
        loading = true
        try { load(c) } catch (e: Exception) { message = e.message } finally { loading = false }
    }

    /** Genera il PDF (con o senza firma) fuori dal main thread. */
    suspend fun buildPdf(c: ClientRecord, withSignature: Boolean): File = withContext(Dispatchers.IO) {
        val bitmap = if (withSignature && strokes.isNotEmpty() && signatureAreaSize != IntSize.Zero) {
            signatureToBitmap(strokes.toList(), signatureAreaSize)
        } else {
            null
        }
        // Nel documento "Oggi" diventa la data (riletto in seguito sarebbe ambiguo).
        val periodLabel = if (period == Period.TODAY) Format.date(System.currentTimeMillis()) else period.label
        File(context.cacheDir, "mandatino-${c.id}.pdf").also { file ->
            FileOutputStream(file).use { out ->
                MandatinoPdfGenerator.generate(
                    out, c, stops, periodLabel,
                    technicianByStopId = if (includeTeam) technicians else null,
                    signatureBitmap = bitmap,
                    signedAtMillis = System.currentTimeMillis(),
                )
            }
        }
    }

    /** Invio all'email del cliente: parte SOLO da qui, dopo la conferma del tecnico (requisito 9). */
    fun send(withSignature: Boolean) {
        val s = session ?: return
        val c = client ?: return
        val to = recipient.trim()
        if (to.isEmpty() || stops.isEmpty()) return
        busy = true
        scope.launch {
            try {
                val file = buildPdf(c, withSignature)
                val base64 = withContext(Dispatchers.IO) { Base64.getEncoder().encodeToString(file.readBytes()) }
                val periodLabel = if (period == Period.TODAY) Format.date(System.currentTimeMillis()) else period.label
                backendClient.sendMandatino(s.teamId, c.id, to, periodLabel, base64)
                message = if (withSignature) "Mandatino firmato inviato a $to." else "Mandatino non firmato inviato a $to."
            } catch (e: Exception) {
                message = "Invio mandatino fallito: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    /** In alternativa all'email (o in aggiunta): WhatsApp, stampa, ecc. */
    fun share(withSignature: Boolean) {
        val c = client ?: return
        scope.launch {
            try {
                val file = buildPdf(c, withSignature)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Mandatino delle ore — ${c.name}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Condividi mandatino"))
            } catch (e: Exception) { message = "Errore: ${e.message}" }
        }
    }

    Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, null) }
            Text("Mandatino delle ore", modifier = Modifier.weight(1f))
        }

        val c = client
        if (c == null) {
            Text(message ?: "Caricamento cliente...")
            return@Column
        }

        Text(c.name, style = MaterialTheme.typography.titleLarge)

        // --- Periodo e colleghi ---
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Period.values().forEach { p ->
                FilterChip(selected = period == p, onClick = { period = p }, label = { Text(p.label) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = includeTeam, enabled = !loading && !busy, onCheckedChange = { includeTeam = it })
            Text("Includi le ore dei colleghi presso questo cliente")
        }

        // --- Riepilogo da far accettare ---
        ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Column(Modifier.padding(12.dp)) {
                if (loading) {
                    Text("Caricamento interventi...")
                } else {
                    val totalMinutes = stops.sumOf { Format.durationMinutes(it.startedAt, it.endedAt) }
                    val totalMaterials = stops.sumOf { s -> s.articleLines.sumOf { it.unitPrice * it.quantity } }
                    Text("${stops.size} interventi · ${Format.durationLabel(totalMinutes)}", style = MaterialTheme.typography.titleMedium)
                    if (includeTeam) {
                        stops.groupBy { technicians[it.id] ?: "—" }.forEach { (name, list) ->
                            Text("  $name: ${Format.durationLabel(list.sumOf { Format.durationMinutes(it.startedAt, it.endedAt) })}")
                        }
                    }
                    stops.forEach { stop ->
                        val who = if (includeTeam) " · ${technicians[stop.id] ?: "—"}" else ""
                        Text("${Format.date(stop.startedAt)} ${Format.time(stop.startedAt)}–${Format.time(stop.endedAt)}$who")
                        if (stop.notes.isNotBlank()) Text("   ${stop.notes}")
                    }
                    if (totalMaterials > 0) Text("Materiali: ${Format.euros(totalMaterials)}")
                    if (inProgress > 0) Text("$inProgress interventi ancora in corso, conteggiati fino ad ora.")
                    if (matchedByPosition > 0) {
                        Text(
                            "$matchedByPosition soste senza cliente incluse perché nella posizione di questo cliente: verifica che siano corrette.",
                            color = Color(0xFFD97706),
                        )
                    }
                    if (stops.isEmpty()) Text("Nessun intervento trovato nel periodo.", color = Color.Red)
                }
            }
        }

        // --- Firma del cliente (sempre saltabile) ---
        Text("Firma del cliente per accettazione (facoltativa):")
        ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Column {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .onSizeChanged { signatureAreaSize = it }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset -> currentStroke = listOf(offset) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentStroke = currentStroke + change.position
                                },
                                onDragEnd = {
                                    if (currentStroke.size > 1) strokes.add(currentStroke)
                                    currentStroke = emptyList()
                                },
                                onDragCancel = { currentStroke = emptyList() },
                            )
                        },
                ) {
                    (strokes + listOf(currentStroke)).forEach { points ->
                        if (points.size > 1) {
                            val path = Path().apply {
                                moveTo(points.first().x, points.first().y)
                                points.drop(1).forEach { lineTo(it.x, it.y) }
                            }
                            drawPath(path, Color.Black, style = Stroke(width = 4f))
                        }
                    }
                }
                OutlinedButton(onClick = { strokes.clear(); currentStroke = emptyList() }) { Text("Cancella firma") }
            }
        }

        // --- Invio / condivisione ---
        OutlinedTextField(
            value = recipient, onValueChange = { recipient = it },
            label = { Text("Email del cliente") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        val canSend = !loading && !busy && stops.isNotEmpty() && recipient.isNotBlank()
        Button(
            onClick = { send(withSignature = true) },
            enabled = canSend && strokes.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text(if (busy) "Invio..." else "Conferma e invia firmato") }
        OutlinedButton(
            onClick = { send(withSignature = false) },
            enabled = canSend,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Salta la firma — conferma e invia comunque") }
        OutlinedButton(
            onClick = { share(withSignature = strokes.isNotEmpty()) },
            enabled = !loading && !busy && stops.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Condividi PDF con un'altra app") }

        message?.let { Text(it) }
    }
}

/**
 * Ridisegna i tratti su un bitmap delle stesse dimensioni (in pixel)
 * dell'area di firma: i punti arrivano dai gesti già in pixel, quindi la
 * firma nel PDF è identica a quella tracciata a schermo.
 */
private fun signatureToBitmap(strokes: List<List<Offset>>, size: IntSize): Bitmap {
    val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        strokeWidth = 4f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    for (points in strokes) {
        if (points.size < 2) continue
        val path = android.graphics.Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        canvas.drawPath(path, paint)
    }
    return bitmap
}
