// RapportinoPdfGenerator.kt — v1.0.0 — 2026-09-20 00:10 UTC
package com.plumberdiary.app.pdf

import android.graphics.Bitmap
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.io.image.ImageDataFactory
import com.plumberdiary.app.data.model.ClientRecord
import com.plumberdiary.app.data.model.Stop
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Requisito 15: rapportino d'intervento con firma cliente SEMPRE SALTABILE.
 * [signatureBitmap] è null quando l'utente ha premuto "Salta": il documento
 * viene generato comunque, con la dicitura "non firmato" al posto
 * dell'immagine — non è mai un motivo per bloccare l'invio del rapportino.
 */
object RapportinoPdfGenerator {

    fun generate(
        output: OutputStream,
        client: ClientRecord,
        stop: Stop,
        signatureBitmap: Bitmap?,
    ) {
        val writer = PdfWriter(output)
        val pdfDoc = PdfDocument(writer)
        val document = Document(pdfDoc)

        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY)

        document.add(Paragraph("Rapportino intervento").setBold().setFontSize(18f))
        document.add(Paragraph("Cliente: ${client.name}"))
        document.add(Paragraph("Indirizzo: ${stop.addressLabel.ifBlank { client.defaultAddressLabel }}"))
        document.add(
            Paragraph(
                "Intervento: ${dateFormat.format(Date(stop.startedAt))} — ${dateFormat.format(Date(stop.endedAt))}",
            ),
        )
        document.add(Paragraph("Note: ${stop.notes.ifBlank { "—" }}"))

        if (stop.articleLines.isNotEmpty()) {
            document.add(Paragraph("Materiali usati").setBold())
            val table = Table(4)
            listOf("Codice", "Descrizione", "Prezzo", "Qtà").forEach { table.addHeaderCell(it) }
            stop.articleLines.forEach { line ->
                table.addCell(line.code)
                table.addCell(line.description)
                table.addCell("€ ${"%.2f".format(line.unitPrice)}")
                table.addCell(line.quantity.toString())
            }
            document.add(table)
        }

        document.add(Paragraph("\nFirma cliente:").setBold())
        if (signatureBitmap != null) {
            val stream = ByteArrayOutputStream()
            signatureBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            document.add(Image(ImageDataFactory.create(stream.toByteArray())).setWidth(200f))
        } else {
            document.add(Paragraph("Non firmato — l'utente ha scelto di saltare la firma."))
        }

        document.close()
    }
}
