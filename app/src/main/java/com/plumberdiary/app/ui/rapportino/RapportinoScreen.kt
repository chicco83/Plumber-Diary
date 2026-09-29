// RapportinoScreen.kt — v1.8.0 — 2026-09-29 (v1.7.0 — 2026-09-23; v1.0.0 — 2026-09-20 00:10 UTC)
//
// Requisito 15: area di firma su Canvas (disegno a dito), riepilogo
// orari/note/materiali, generazione PDF con [RapportinoPdfGenerator] e
// condivisione via FileProvider. Il pulsante "Salta la firma" è SEMPRE
// presente e non bloccante: genera/invia comunque il rapportino con
// signatureBitmap = null (vincolo esplicito dell'utente).
//
// Versione precedente (v1.7.0 — 2026-09-23), sostituita il 2026-09-29. Non
// compilava e, anche corretta, non avrebbe funzionato:
//  - dichiarava `strokes`/`currentStroke` ma usava `paths`, `currentPath`,
//    `signatureCleared`, mai dichiarati;
//  - detectDragGestures(onStart = ...): il parametro si chiama onDragStart;
//  - signatureToBitmap() era @Composable ma chiamata da una coroutine;
//  - sharePdf() usata prima della sua dichiarazione (funzione locale);
//  - drawContent { } non esiste nello scope del Canvas;
//  - i tratti erano in una lista non osservabile: la firma non si vedeva
//    mentre la si disegnava;
//  - la conversione per il PDF disegnava solo la diagonale del riquadro:
//
//    androidPath.moveTo(bounds.left * scale, bounds.top * scale)
//    androidPath.lineTo(bounds.right * scale, bounds.bottom * scale)
//
// Ora i tratti sono liste di punti in pixel, osservabili, disegnati sia a
// schermo sia nel bitmap del PDF alla stessa scala dell'area di firma.
package com.plumberdiary.app.ui.rapportino

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import com.plumberdiary.app.data.model.ClientRecord
import com.plumberdiary.app.data.model.Stop
import com.plumberdiary.app.data.repository.ClientRepository
import com.plumberdiary.app.data.repository.StopRepository
import com.plumberdiary.app.pdf.RapportinoPdfGenerator
import com.plumberdiary.app.ui.common.Format
import com.plumberdiary.app.ui.common.rememberActiveSession
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RapportinoScreen(navController: NavHostController, stopId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = rememberActiveSession()

    var stop by remember { mutableStateOf<Stop?>(null) }
    var client by remember { mutableStateOf<ClientRecord?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    // Firma: tratti completati + tratto in corso, punti in pixel dell'area di firma.
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var signatureAreaSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(stopId) {
        if (session == null) return@LaunchedEffect
        try {
            stop = StopRepository().getStop(session.teamId, session.uid, stopId)
            val clientId = stop?.clientId
            if (clientId != null) client = ClientRepository().getById(session.teamId, clientId)
        } catch (e: Exception) { message = e.message }
    }

    fun sharePdf(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Rapportino intervento")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Invia rapportino"))
    }

    fun generateAndShare(withSignature: Boolean) {
        val s = stop ?: return
        val c = client
        val strokesSnapshot = strokes.toList()
        val size = signatureAreaSize
        scope.launch {
            message = "Generazione PDF..."
            try {
                val pdfFile = withContext(Dispatchers.IO) {
                    val bitmap = if (withSignature && strokesSnapshot.isNotEmpty() && size != IntSize.Zero) {
                        signatureToBitmap(strokesSnapshot, size)
                    } else {
                        null
                    }
                    File(context.cacheDir, "rapportino-${s.id}.pdf").also { file ->
                        FileOutputStream(file).use { out ->
                            RapportinoPdfGenerator.generate(out, c ?: ClientRecord(name = "—"), s, bitmap)
                        }
                    }
                }
                sharePdf(pdfFile)
                message = if (withSignature && strokesSnapshot.isNotEmpty()) {
                    "Rapportino generato con firma — scegli l'app per inviarlo."
                } else {
                    "Rapportino generato senza firma — scegli l'app per inviarlo."
                }
            } catch (e: Exception) { message = "Errore: ${e.message}" }
        }
    }

    Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, null) }
            Text("Rapportino intervento", modifier = Modifier.weight(1f))
        }

        val s = stop
        if (s == null) {
            Text("Caricamento... o sosta non trovata.")
        } else {
            ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Cliente: ${client?.name ?: "— (nessun cliente associato alla sosta)"}")
                    Text("Intervento: ${Format.dateTime(s.startedAt)} — ${if (s.endedAt > 0L) Format.dateTime(s.endedAt) else "in corso"}")
                    if (s.notes.isNotBlank()) Text("Note: ${s.notes}")
                    if (s.articleLines.isNotEmpty()) {
                        Text("Materiali:")
                        s.articleLines.forEach { line ->
                            Text("  • ${line.code} ${line.description} — ${Format.euros(line.unitPrice)} × ${line.quantity}")
                        }
                    }
                }
            }

            Text("Firma del cliente (opzionale):")
            ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
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
                    OutlinedButton(onClick = {
                        strokes.clear()
                        currentStroke = emptyList()
                    }) { Text("Cancella firma") }
                }
            }

            Button(
                onClick = { generateAndShare(withSignature = true) },
                enabled = strokes.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Genera rapportino con firma e condividi") }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = { generateAndShare(withSignature = false) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Salta la firma — genera comunque il rapportino") }

            message?.let { Text(it) }
        }
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
