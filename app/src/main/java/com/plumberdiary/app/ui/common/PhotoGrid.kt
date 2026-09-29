// PhotoGrid.kt — v1.8.0 — 2026-09-29 (v1.7.0 — 2026-09-24)
//
// Miniature delle foto caricate (copia "display" su Firebase Storage, vedi
// PhotoUploader): prima di questa schermata le foto venivano caricate ma non
// mostrate da nessuna parte. Le copie HD restano riservate alla mail di recap
// (requisito 12), qui si usano solo quelle compressa per app/sync.
package com.plumberdiary.app.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

/**
 * Mostra le miniature delle foto identificate da [photoIds]; per ogni id il
 * chiamante fornisce il path Storage della copia display (soste o scheda
 * cliente — vedi [com.plumberdiary.app.data.FirestorePaths]).
 */
@Composable
fun PhotoGrid(photoIds: List<String>, displayPathFor: (String) -> String) {
    var urls by remember { mutableStateOf<Map<String, String>?>(null) }

    LaunchedEffect(photoIds) {
        if (photoIds.isEmpty()) return@LaunchedEffect
        val storage = FirebaseStorage.getInstance()
        // Versione precedente (v1.7.0 — 2026-09-24), corretta il 2026-09-29:
        // downloadUrl è un Task, e toString() ne restituiva la descrizione
        // testuale ("com.google.android.gms.tasks...@1a2b"), non l'URL: nessuna
        // miniatura veniva mai mostrata.
        //
        // storage.getReference(displayPathFor(id)).downloadUrl.toString()
        urls = photoIds.associateWith { id ->
            try {
                storage.getReference(displayPathFor(id)).downloadUrl.await().toString()
            } catch (_: Exception) {
                "" // foto non ancora caricata, cancellata o senza permesso: si salta
            }
        }
    }

    val map = urls ?: return
    if (map.values.none { it.isNotBlank() }) return

    // v1.8.0 — 2026-09-29: scorrimento orizzontale, 8 miniature da 72dp non
    // stanno nella larghezza di un telefono (prima venivano tagliate).
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        map.forEach { (id, url) ->
            if (url.isNotBlank()) {
                AsyncImage(
                    model = url,
                    contentDescription = "Foto $id",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)),
                )
            }
        }
    }
}