// PhotoUploader.kt — v1.12.0 — 2026-09-29 (v1.8.0 — 2026-09-29; v1.0.0 — 2026-09-20 00:10 UTC)
package com.plumberdiary.app.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import androidx.exifinterface.media.ExifInterface
import com.google.firebase.storage.ktx.storageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.min

/**
 * Requisito 12: ogni foto genera DUE copie su Firebase Storage (vedi
 * context.md, "Foto (cliente e/o intervento)"):
 *  - "display": compressa, conservata stabilmente, per la UI e la sync
 *    di squadra;
 *  - "original": alta risoluzione, SOLO per l'allegato email di recap,
 *    cancellata dal backend trascorsi 7 giorni dal caricamento (v1.12.0 —
 *    2026-09-29: non più subito dopo l'invio, così un reinvio del recap ha
 *    ancora l'HD; vedi backend/api/cleanup.js).
 *
 * L'immagine sorgente arriva dal Photo Picker di sistema
 * (ActivityResultContracts.PickMultipleVisualMedia), quindi come Uri di
 * contenuto, non da un permesso di storage esplicito.
 */
class PhotoUploader(
    private val context: Context,
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
) {
    // Versione precedente (v1.0.0 — 2026-09-20), sostituita il 2026-09-29:
    // originalPath era obbligatorio, così la scheda cliente — che non ha una
    // mail di recap in cui usare l'HD — caricava comunque l'originale su un
    // percorso ("display.jpg.orig") che il cleanup non cancella mai.
    //
    // data class UploadResult(val photoId: String, val displayPath: String, val originalPath: String)
    // suspend fun upload(sourceUri: Uri, displayPath: String, originalPath: String, photoId: String): UploadResult {
    //     val originalBytes = readBytes(sourceUri)
    //     storage.getReference(originalPath).putBytes(originalBytes).await()

    data class UploadResult(val photoId: String, val displayPath: String, val originalPath: String?)

    /**
     * [originalPath] null = solo copia display (foto della scheda cliente).
     * Valorizzato solo per le foto degli interventi, che vanno in HD nella mail
     * di recap (requisito 12) e il cui originale viene poi cancellato dal
     * backend (percorso che termina in /original.jpg, vedi cleanup.js).
     */
    suspend fun upload(sourceUri: Uri, displayPath: String, originalPath: String?, photoId: String): UploadResult {
        // v1.8.0 — 2026-09-29: lettura e ricompressione fuori dal main thread
        // (chiamato dalle schermate: decodificare una foto da 12 MP sul thread
        // UI bloccava l'app per secondi).
        val originalBytes = withContext(Dispatchers.IO) { readBytes(sourceUri) }

        // Copia "original": stessi byte del file scelto, nessuna ricompressione
        // (è quella che finirà in alta risoluzione nella mail di recap).
        // v1.8.0 — 2026-09-29: content-type esplicito, richiesto da
        // backend/storage.rules (solo immagini) e utile al client email che
        // riceve l'allegato.
        if (originalPath != null) {
            val originalType = context.contentResolver.getType(sourceUri) ?: "image/jpeg"
            storage.getReference(originalPath)
                .putBytes(originalBytes, storageMetadata { contentType = originalType })
                .await()
        }

        // Copia "display": ridimensionata sul lato lungo e ricompressa in JPEG,
        // target ~300-500 KB (vedi tabella limiti Firebase Storage in context.md).
        val displayBytes = withContext(Dispatchers.Default) { compress(originalBytes) }
        storage.getReference(displayPath)
            .putBytes(displayBytes, storageMetadata { contentType = "image/jpeg" })
            .await()

        return UploadResult(photoId, displayPath, originalPath)
    }

    private fun readBytes(uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Impossibile leggere la foto selezionata: $uri")

    // Versione precedente (v1.0.0 — 2026-09-20), sostituita il 2026-09-29:
    // 1) decodificava la foto a piena risoluzione (12 MP ≈ 48 MB di bitmap):
    //    OutOfMemoryError sui telefoni economici;
    // 2) ignorava l'orientamento EXIF: le foto scattate in verticale finivano
    //    ruotate di 90° nelle miniature.
    //
    // private fun compress(originalBytes: ByteArray, maxLongSidePx: Int = 1600, quality: Int = 80): ByteArray {
    //     val original = BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size)
    //     val scale = min(1f, maxLongSidePx.toFloat() / maxOf(original.width, original.height))
    //     val resized = if (scale < 1f) Bitmap.createScaledBitmap(original, ...) else original
    //     return ByteArrayOutputStream().use { resized.compress(Bitmap.CompressFormat.JPEG, quality, it); it.toByteArray() }
    // }
    private fun compress(originalBytes: ByteArray, maxLongSidePx: Int = 1600, quality: Int = 80): ByteArray {
        // 1) Solo dimensioni, senza allocare il bitmap.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("Formato immagine non supportato")

        // 2) Decodifica già ridotta di una potenza di 2, restando ≥ del lato richiesto.
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxLongSidePx) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(
            originalBytes, 0, originalBytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: error("Impossibile decodificare la foto")

        // 3) Ridimensionamento fine al lato lungo richiesto.
        val scale = min(1f, maxLongSidePx.toFloat() / maxOf(decoded.width, decoded.height))
        val resized = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
        } else {
            decoded
        }

        // 4) Rotazione secondo l'EXIF (la copia "original" resta intatta, EXIF incluso).
        val rotated = applyExifOrientation(resized, originalBytes)

        return ByteArrayOutputStream().use { stream ->
            rotated.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            stream.toByteArray()
        }
    }

    private fun applyExifOrientation(bitmap: Bitmap, originalBytes: ByteArray): Bitmap {
        val orientation = try {
            ExifInterface(ByteArrayInputStream(originalBytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.preScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.preScale(-1f, 1f) }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
