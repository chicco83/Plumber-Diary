// MandatinoPdfGenerator.kt — v1.11.0 — 2026-09-29 (v1.9.0 — 2026-09-29: colonna Tecnico; v1.0.0 — 2026-09-20 00:10 UTC)
package com.plumberdiary.app.pdf

import android.graphics.Bitmap
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.plumberdiary.app.data.model.ClientRecord
import com.plumberdiary.app.data.model.Stop
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Requisito 9 (con 15): il "mandatino delle ore" è il documento che il
 * cliente accetta sul posto a fine intervento — ore (anche dei colleghi, se
 * scelto), note, materiali e firma del cliente per accettazione.
 *
 * v1.11.0 — 2026-09-29: mandatino e "rapportino con firma" sono lo stesso
 * documento (chiarimento dell'utente). Fino alla v1.10.0 esistevano due
 * generatori distinti — questo (ore/periodo, senza firma né materiali) e
 * RapportinoPdfGenerator (una sola sosta, con materiali e firma) — frutto di
 * un errore di progettazione: il rapportino era stato proposto come funzione
 * nuova mentre coincideva con il mandatino già richiesto. Ora c'è un solo
 * generatore che fa tutto; RapportinoPdfGenerator è stato rimosso. Il suo
 * contenuto (v1.0.0 — 2026-09-20) sopravvive qui: sezione materiali e blocco
 * firma, con la dicitura "non firmato" quando la firma viene saltata.
 *
 * La firma resta SEMPRE SALTABILE (vincolo esplicito dell'utente):
 * [signatureBitmap] null = documento generato comunque, con la dicitura
 * "non firmato", mai un motivo per bloccarne l'invio.
 */
object MandatinoPdfGenerator {

    // Versione precedente (v1.9.0 — 2026-09-29), sostituita il 2026-09-29:
    // stesso contenuto senza materiali né firma.
    //
    // fun generate(output: OutputStream, client: ClientRecord, stops: List<Stop>, periodLabel: String,
    //              technicianByStopId: Map<String, String>? = null)

    /**
     * [technicianByStopId]: null = solo le proprie ore (niente colonna
     * "Tecnico"); valorizzata = ore di più tecnici della squadra.
     * Le soste ancora in corso arrivano già con endedAt = "fino ad ora"
     * (calcolato dalla schermata, mai scritto su Firestore).
     */
    fun generate(
        output: OutputStream,
        client: ClientRecord,
        stops: List<Stop>,
        periodLabel: String,
        technicianByStopId: Map<String, String>?,
        signatureBitmap: Bitmap?,
        signedAtMillis: Long,
    ) {
        val writer = PdfWriter(output)
        val pdfDoc = PdfDocument(writer)
        val document = Document(pdfDoc)
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ITALY)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.ITALY)
        val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY)

        fun minutesOf(stop: Stop) = TimeUnit.MILLISECONDS.toMinutes(stop.endedAt - stop.startedAt)
        fun label(minutes: Long) = "${minutes / 60}h ${minutes % 60}m"
        fun euros(value: Double) = String.format(Locale.ITALY, "€ %.2f", value)

        val totalMinutes = stops.sumOf { minutesOf(it) }

        // --- Intestazione ---
        document.add(Paragraph("Mandatino delle ore").setBold().setFontSize(18f))
        document.add(Paragraph("Cliente: ${client.name}"))
        if (client.defaultAddressLabel.isNotBlank()) document.add(Paragraph("Indirizzo: ${client.defaultAddressLabel}"))
        if (client.fiscalCode.isNotBlank()) document.add(Paragraph("P.IVA / C.F.: ${client.fiscalCode}"))
        document.add(Paragraph("Periodo: $periodLabel"))
        document.add(Paragraph("Ore totali: ${label(totalMinutes)}").setBold())

        if (technicianByStopId != null) {
            stops.groupBy { technicianByStopId[it.id] ?: "—" }
                .map { (name, list) -> name to list.sumOf { minutesOf(it) } }
                .sortedByDescending { it.second }
                .forEach { (name, minutes) -> document.add(Paragraph("  $name: ${label(minutes)}")) }
        }

        // --- Interventi ---
        val headers = buildList {
            add("Data"); add("Orario")
            if (technicianByStopId != null) add("Tecnico")
            add("Durata"); add("Note")
        }
        val table = Table(headers.size)
        headers.forEach { table.addHeaderCell(it) }
        stops.forEach { stop ->
            table.addCell(dateFormat.format(Date(stop.startedAt)))
            table.addCell("${timeFormat.format(Date(stop.startedAt))}–${timeFormat.format(Date(stop.endedAt))}")
            if (technicianByStopId != null) table.addCell(technicianByStopId[stop.id] ?: "—")
            table.addCell(label(minutesOf(stop)))
            table.addCell(stop.notes.ifBlank { "—" })
        }
        document.add(table)

        // --- Materiali (dall'ex rapportino), aggregati per articolo ---
        val lines = stops.flatMap { it.articleLines }
        if (lines.isNotEmpty()) {
            document.add(Paragraph("\nMateriali").setBold())
            val materials = Table(5)
            listOf("Codice", "Descrizione", "Qtà", "Prezzo", "Totale").forEach { materials.addHeaderCell(it) }
            lines.groupBy { Triple(it.code, it.description, it.unitPrice) }.forEach { (key, group) ->
                val quantity = group.sumOf { it.quantity }
                materials.addCell(key.first)
                materials.addCell(key.second)
                materials.addCell(quantity.toString())
                materials.addCell(euros(key.third))
                materials.addCell(euros(key.third * quantity))
            }
            document.add(materials)
            document.add(Paragraph("Totale materiali: ${euros(lines.sumOf { it.unitPrice * it.quantity })}").setBold())
        }

        // --- Accettazione e firma (dall'ex rapportino) ---
        document.add(Paragraph("\nIl cliente dichiara di accettare l'addebito delle ore e dei materiali sopra indicati."))
        document.add(Paragraph("Firma del cliente — ${dateTimeFormat.format(Date(signedAtMillis))}:").setBold())
        if (signatureBitmap != null) {
            val stream = ByteArrayOutputStream()
            signatureBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            document.add(Image(ImageDataFactory.create(stream.toByteArray())).setWidth(200f))
        } else {
            document.add(Paragraph("Non firmato — il tecnico ha scelto di saltare la firma."))
        }

        document.close()
    }
}
