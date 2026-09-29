// ClientRepository.kt — v1.8.0 — 2026-09-29 (v1.7.0 — 2026-09-23: getById; v1.0.0 — 2026-09-20)
package com.plumberdiary.app.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.toObject
import com.plumberdiary.app.data.FirestorePaths
import com.plumberdiary.app.data.model.ClientRecord
import com.plumberdiary.app.data.model.KnownPosition
import com.plumberdiary.app.location.GeoUtils
import kotlinx.coroutines.tasks.await

/**
 * Anagrafica clienti CONDIVISA a livello di squadra (requisito 11): le regole
 * di sicurezza permettono lettura/scrittura a chiunque sia membro della
 * squadra, non solo a chi ha creato il cliente.
 *
 * v1.8.0 — 2026-09-29: essendo un documento condiviso, più colleghi possono
 * scriverlo nello stesso momento. Prima ogni scrittura era un set()
 * dell'intero documento (vince l'ultimo, le modifiche dell'altro spariscono).
 * Ora: anagrafica aggiornata solo nei suoi campi, foto con arrayUnion,
 * posizioni note in transazione.
 */
class ClientRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    /** Tutti i clienti della squadra: usata da [com.plumberdiary.app.location.ClientMatcher]. */
    suspend fun getAll(teamId: String): List<ClientRecord> {
        val snapshot = firestore.collection(FirestorePaths.clients(teamId)).get().await()
        return snapshot.documents.mapNotNull { it.toObject<ClientRecord>() }
    }

    /** v1.7.0 — 2026-09-23: lettura di un singolo cliente (schede e conferme). */
    suspend fun getById(teamId: String, clientId: String): ClientRecord? {
        val doc = firestore.document(FirestorePaths.client(teamId, clientId)).get().await()
        return doc.toObject<ClientRecord>()
    }

    // Versione precedente (v1.0.0 — 2026-09-20), sostituita il 2026-09-29: usata
    // anche per gli aggiornamenti, sovrascriveva l'intero documento condiviso.
    //
    // suspend fun upsert(teamId: String, client: ClientRecord): String {
    //     val collection = firestore.collection(FirestorePaths.clients(teamId))
    //     val docRef = if (client.id.isBlank()) collection.document() else collection.document(client.id)
    //     docRef.set(client.copy(id = docRef.id)).await()
    //     return docRef.id
    // }

    /** Crea un nuovo cliente (documento nuovo: il set completo qui è sicuro). */
    suspend fun create(teamId: String, client: ClientRecord): String {
        val docRef = firestore.collection(FirestorePaths.clients(teamId)).document()
        docRef.set(client.copy(id = docRef.id)).await()
        return docRef.id
    }

    /** Aggiorna solo i campi anagrafici, senza toccare foto e posizioni note. */
    suspend fun updateAnagrafica(teamId: String, client: ClientRecord) {
        firestore.document(FirestorePaths.client(teamId, client.id)).set(
            mapOf(
                "name" to client.name,
                "phone" to client.phone,
                "fiscalCode" to client.fiscalCode,
                "defaultAddressLabel" to client.defaultAddressLabel,
                "hoursReportEmail" to client.hoursReportEmail,
            ),
            SetOptions.merge(),
        ).await()
    }

    suspend fun addPhotoIds(teamId: String, clientId: String, photoIds: List<String>) {
        if (photoIds.isEmpty()) return
        firestore.document(FirestorePaths.client(teamId, clientId))
            .update("photoIds", FieldValue.arrayUnion(*photoIds.toTypedArray()))
            .await()
    }

    /**
     * Aggiunge/aggiorna la posizione nota di un cliente dopo che un intervento
     * è stato confermato lì (così la prossima volta [ClientMatcher] lo
     * riconosce). Non crea un nuovo punto se uno già presente è abbastanza
     * vicino: aggiorna solo lastSeenAt.
     *
     * v1.8.0 — 2026-09-29: in transazione sul documento letto da server, non
     * sulla copia [ClientRecord] che il chiamante aveva in memoria (magari
     * vecchia di minuti): due colleghi che confermano lo stesso cliente in
     * contemporanea non si cancellano più le posizioni a vicenda.
     */
    suspend fun recordVisit(teamId: String, clientId: String, lat: Double, lon: Double, atMillis: Long) {
        val ref = firestore.document(FirestorePaths.client(teamId, clientId))
        firestore.runTransaction { tx ->
            val current = tx.get(ref).toObject<ClientRecord>()
            if (current != null) {
                val existing = current.knownPositions.firstOrNull {
                    GeoUtils.distanceMeters(it.lat, it.lon, lat, lon) <= it.radiusMeters
                }
                val updated = if (existing != null) {
                    current.knownPositions.map { if (it === existing) it.copy(lastSeenAt = atMillis) else it }
                } else {
                    current.knownPositions + KnownPosition(lat = lat, lon = lon, lastSeenAt = atMillis)
                }
                tx.update(ref, "knownPositions", updated)
            }
            null
        }.await()
    }
}
