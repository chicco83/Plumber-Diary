// PhotoUploader.kt — v1.0.0 — 2026-09-20 00:10 UTC
package com.plumberdiary.app.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import kotlin.math.min

/**
 * Requisito 12: ogni foto genera DUE copie su Firebase Storage (vedi
 * context.md, "Foto (cliente e/o intervento)"):
 *  - "display": compressa, conservata stabilmente, per la UI e la sync
 *    di squadra;
 *  - "original": alta risoluzione, SOLO per l'allegato email di recap,
 *    cancellata dal backend dopo l'invio riuscito (vedi backend/api/cleanup.js).
 *
 * L'immagine sorgente arriva dal Photo Picker di sistema
 * (ActivityResultContracts.PickMultipleVisualMedia), quindi come Uri di
 * contenuto, non da un permesso di storage esplicito.
 */
class PhotoUploader(
    private val context: Context,
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
) {
    data class UploadResult(val photoId: String, val displayPath: String, val originalPath: String)

    suspend fun upload(sourceUri: Uri, displayPath: String, originalPath: String, photoId: String): UploadResult {
        val originalBytes = readBytes(sourceUri)

        // Copia "original": stessi byte del file scelto, nessuna ricompressione
        // (è quella che finirà in alta risoluzione nella mail di recap).
        storage.getReference(originalPath).putBytes(originalBytes).await()

        // Copia "display": ridimensionata sul lato lungo e ricompressa in JPEG,
        // target ~300-500 KB (vedi tabella limiti Firebase Storage in context.md).
        val displayBytes = compress(originalBytes)
        storage.getReference(displayPath).putBytes(displayBytes).await()

        return UploadResult(photoId, displayPath, originalPath)
    }

    private fun readBytes(uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Impossibile leggere la foto selezionata: $uri")

    private fun compress(originalBytes: ByteArray, maxLongSidePx: Int = 1600, quality: Int = 80): ByteArray {
        val original = BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size)
        val scale = min(1f, maxLongSidePx.toFloat() / maxOf(original.width, original.height))
        val resized = if (scale < 1f) {
            Bitmap.createScaledBitmap(original, (original.width * scale).toInt(), (original.height * scale).toInt(), true)
        } else {
            original
        }
        return ByteArrayOutputStream().use { stream ->
            resized.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            stream.toByteArray()
        }
    }
}
