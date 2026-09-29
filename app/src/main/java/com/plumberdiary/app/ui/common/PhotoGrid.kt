// PhotoGrid.kt — v1.13.0 — 2026-09-29 (v1.8.0 — 2026-09-29; v1.7.0 — 2026-09-24)
//
// Miniature delle foto caricate (copia "display", vedi PhotoUploader): prima
// di questa schermata le foto venivano caricate ma non mostrate da nessuna
// parte. Le copie HD restano riservate alla mail di recap (requisito 12), qui
// si usano solo quelle compresse per app/sync.
//
// v1.13.0 — 2026-09-29: le foto sono su uno storage a oggetti S3 privato
// (non più Firebase Storage). Gli URL firmati per vederle li rilascia il
// backend (photo-view-urls, solo ai membri della squadra) e valgono 1 ora:
//  - [PhotoUrlCache] li tiene in memoria ~50 minuti, così riaprire una scheda
//    non richiama ogni volta il backend;
//  - l'URL firmato cambia a ogni emissione, quindi la cache di Coil usa come
//    chiave la CHIAVE DELL'OGGETTO, non l'URL: una miniatura già vista non
//    viene riscaricata (meno traffico e meno operazioni a pagamento oltre la
//    soglia gratuita del servizio).
//
// Versione precedente (v1.8.0 — 2026-09-29), sostituita il 2026-09-29:
//
//   val storage = FirebaseStorage.getInstance()
//   urls = photoIds.associateWith { id ->
//       try { storage.getReference(displayPathFor(id)).downloadUrl.await().toString() }
//       catch (_: Exception) { "" }
//   }
//   ...
//   AsyncImage(model = url, contentDescription = "Foto $id", contentScale = ContentScale.Crop, modifier = ...)
package com.plumberdiary.app.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.plumberdiary.app.auth.AuthRepository
import com.plumberdiary.app.data.BackendClient
import com.plumberdiary.app.data.BackendConfig
import com.plumberdiary.app.session.CurrentSession

/** URL firmati di visualizzazione, validi 1 ora: riusati per ~50 minuti. */
object PhotoUrlCache {
    private const val REUSE_MILLIS = 50 * 60 * 1000L
    private data class Entry(val url: String, val fetchedAt: Long)
    private val entries = HashMap<String, Entry>()

    suspend fun urlsFor(backend: BackendClient, teamId: String, keys: List<String>): Map<String, String> {
        val now = System.currentTimeMillis()
        val missing = synchronized(entries) {
            keys.filter { k -> entries[k]?.let { now - it.fetchedAt > REUSE_MILLIS } ?: true }
        }
        if (missing.isNotEmpty()) {
            val fresh = backend.photoViewUrls(teamId, missing)
            synchronized(entries) { fresh.forEach { (k, u) -> entries[k] = Entry(u, now) } }
        }
        return synchronized(entries) { keys.mapNotNull { k -> entries[k]?.let { k to it.url } }.toMap() }
    }
}

/**
 * Mostra le miniature delle foto identificate da [photoIds]; per ogni id il
 * chiamante fornisce la chiave della copia display (soste o scheda cliente —
 * vedi [com.plumberdiary.app.data.FirestorePaths]).
 */
@Composable
fun PhotoGrid(photoIds: List<String>, displayPathFor: (String) -> String) {
    val context = LocalContext.current
    val backend = remember { BackendClient(BackendConfig.BASE_URL, AuthRepository(context)) }
    // photoId -> (chiave oggetto, URL firmato)
    var photos by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    LaunchedEffect(photoIds) {
        val teamId = CurrentSession.current()?.teamId ?: return@LaunchedEffect
        if (photoIds.isEmpty()) return@LaunchedEffect
        val keys = photoIds.map(displayPathFor)
        photos = try {
            val urls = PhotoUrlCache.urlsFor(backend, teamId, keys)
            keys.mapNotNull { k -> urls[k]?.let { k to it } }
        } catch (_: Exception) {
            emptyList() // backend non raggiungibile: nessuna miniatura, nessun crash
        }
    }

    if (photos.isEmpty()) return

    // v1.8.0 — 2026-09-29: scorrimento orizzontale, 8 miniature da 72dp non
    // stanno nella larghezza di un telefono (prima venivano tagliate).
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        photos.forEach { (key, url) ->
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .memoryCacheKey(key)
                    .diskCacheKey(key)
                    .crossfade(true)
                    .build(),
                contentDescription = "Foto",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)),
            )
        }
    }
}
