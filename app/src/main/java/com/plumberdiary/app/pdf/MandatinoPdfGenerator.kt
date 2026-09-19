// MandatinoPdfGenerator.kt — v1.0.0 — 2026-09-20 00:10 UTC
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

    fun generate(output: OutputStream, client: ClientRecord, stops: List<Stop>, periodLabel: String) {
        val writer = PdfWriter(output)
        val pdfDoc = PdfDocument(writer)
        val document = Document(pdfDoc)
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ITALY)

        val totalMinutes = stops.sumOf { TimeUnit.MILLISECONDS.toMinutes(it.endedAt - it.startedAt) }
        val totalMaterials = stops.sumOf { stop -> stop.articleLines.sumOf { it.unitPrice * it.quantity } }

        document.add(Paragraph("Mandatino delle ore").setBold().setFontSize(18f))
        document.add(Paragraph("Cliente: ${client.name}"))
        document.add(Paragraph("Periodo: $periodLabel"))
        document.add(Paragraph("Ore totali: ${totalMinutes / 60}h ${totalMinutes % 60}m"))
        document.add(Paragraph("Materiali: € ${"%.2f".format(totalMaterials)}"))

        val table = Table(3)
        listOf("Data", "Durata", "Note").forEach { table.addHeaderCell(it) }
        stops.forEach { stop ->
            val minutes = TimeUnit.MILLISECONDS.toMinutes(stop.endedAt - stop.startedAt)
            table.addCell(dateFormat.format(Date(stop.startedAt)))
            table.addCell("${minutes / 60}h ${minutes % 60}m")
            table.addCell(stop.notes.ifBlank { "—" })
        }
        document.add(table)

        document.close()
    }
}
