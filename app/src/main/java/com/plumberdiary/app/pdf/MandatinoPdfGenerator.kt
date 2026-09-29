// MandatinoPdfGenerator.kt — v1.9.0 — 2026-09-29 (v1.0.0 — 2026-09-20 00:10 UTC)
package com.plumberdiary.app.pdf

import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.plumberdiary.app.data.model.ClientRecord
import com.plumberdiary.app.data.model.Stop
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Requisito 9: "mandatino delle ore" — PDF di riepilogo ore/materiali di un
 * periodo per un cliente, generato SOLO su richiesta esplicita dell'utente
 * e inviato solo dopo conferma (vedi mockup "ConfermaPDF"); questa classe si
 * occupa solo del rendering del documento, mai dell'invio.
 */
object MandatinoPdfGenerator {

    // Versione precedente (v1.0.0 — 2026-09-20), sostituita il 2026-09-29: solo
    // le ore di chi genera il mandatino, senza indicazione del tecnico.
    //
    // fun generate(output: OutputStream, client: ClientRecord, stops: List<Stop>, periodLabel: String) {
    //     ...
    //     val table = Table(3)
    //     listOf("Data", "Durata", "Note").forEach { table.addHeaderCell(it) }
    //     ...
    // }

    /**
     * [technicianByStopId]: null = mandatino con le sole proprie ore (tabella
     * come prima). Valorizzata = ore di più tecnici della squadra (opzione
     * "includi le ore dei colleghi"): la tabella aggiunge la colonna "Tecnico"
     * e il riepilogo le ore per tecnico, così il cliente vede chi ha lavorato.
     */
    fun generate(
        output: OutputStream,
        client: ClientRecord,
        stops: List<Stop>,
        periodLabel: String,
        technicianByStopId: Map<String, String>? = null,
    ) {
        val writer = PdfWriter(output)
        val pdfDoc = PdfDocument(writer)
        val document = Document(pdfDoc)
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ITALY)

        fun minutesOf(stop: Stop) = TimeUnit.MILLISECONDS.toMinutes(stop.endedAt - stop.startedAt)
        fun label(minutes: Long) = "${minutes / 60}h ${minutes % 60}m"

        val totalMinutes = stops.sumOf { minutesOf(it) }
        val totalMaterials = stops.sumOf { stop -> stop.articleLines.sumOf { it.unitPrice * it.quantity } }

        document.add(Paragraph("Mandatino delle ore").setBold().setFontSize(18f))
        document.add(Paragraph("Cliente: ${client.name}"))
        document.add(Paragraph("Periodo: $periodLabel"))
        document.add(Paragraph("Ore totali: ${label(totalMinutes)}"))
        document.add(Paragraph("Materiali: € ${"%.2f".format(totalMaterials)}"))

        if (technicianByStopId != null) {
            // Riepilogo ore per tecnico, in ordine di ore decrescenti.
            stops.groupBy { technicianByStopId[it.id] ?: "—" }
                .map { (name, list) -> name to list.sumOf { minutesOf(it) } }
                .sortedByDescending { it.second }
                .forEach { (name, minutes) -> document.add(Paragraph("  $name: ${label(minutes)}")) }
        }

        val headers = if (technicianByStopId != null) listOf("Data", "Tecnico", "Durata", "Note") else listOf("Data", "Durata", "Note")
        val table = Table(headers.size)
        headers.forEach { table.addHeaderCell(it) }
        stops.forEach { stop ->
            table.addCell(dateFormat.format(Date(stop.startedAt)))
            if (technicianByStopId != null) table.addCell(technicianByStopId[stop.id] ?: "—")
            table.addCell(label(minutesOf(stop)))
            table.addCell(stop.notes.ifBlank { "—" })
        }
        document.add(table)

        document.close()
    }
}
