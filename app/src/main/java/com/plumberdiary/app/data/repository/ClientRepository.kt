// ClientRepository.kt — v1.0.0 — 2026-09-20 00:10 UTC
package com.plumberdiary.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import com.plumberdiary.app.data.FirestorePaths
import com.plumberdiary.app.data.model.ClientRecord
import com.plumberdiary.app.data.model.KnownPosition
import kotlinx.coroutines.tasks.await

/**
 * Anagrafica clienti CONDIVISA a livello di squadra (requisito 11): le regole
 * di sicurezza permettono lettura/scrittura a chiunque sia membro della
 * squadra, non solo a chi ha creato il cliente.
 */
class ClientRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    /** Tutti i clienti della squadra: usata da [com.plumberdiary.app.location.ClientMatcher]. */
    suspend fun getAll(teamId: String): List<ClientRecord> {
        val snapshot = firestore.collection(FirestorePaths.clients(teamId)).get().await()
        return snapshot.documents.mapNotNull { it.toObject<ClientRecord>() }
    }

    /** v1.6.0 — 2026-09-23: lettura di un singolo cliente (schede e conferme). */
    suspend fun getById(teamId: String, clientId: String): ClientRecord? {
        val doc = firestore.document(FirestorePaths.client(teamId, clientId)).get().await()
        return doc.toObject<ClientRecord>()
    }

    suspend fun upsert(teamId: String, client: ClientRecord): String {
        val collection = firestore.collection(FirestorePaths.clients(teamId))
        val docRef = if (client.id.isBlank()) collection.document() else collection.document(client.id)
        docRef.set(client.copy(id = docRef.id)).await()
        return docRef.id
    }

    /**
     * Aggiunge/aggiorna la posizione nota di un cliente dopo che un intervento
     * è stato confermato lì (così la prossima volta [ClientMatcher] lo
     * riconosce). Non crea un nuovo punto se uno già presente è abbastanza
     * vicino: aggiorna solo lastSeenAt.
     */
    suspend fun recordVisit(teamId: String, client: ClientRecord, lat: Double, lon: Double, atMillis: Long) {
        val existing = client.knownPositions.firstOrNull {
            com.plumberdiary.app.location.GeoUtils.distanceMeters(it.lat, it.lon, lat, lon) <= it.radiusMeters
        }
        val updatedPositions = if (existing != null) {
            client.knownPositions.map { if (it === existing) it.copy(lastSeenAt = atMillis) else it }
        } else {
            client.knownPositions + KnownPosition(lat = lat, lon = lon, lastSeenAt = atMillis)
        }
        upsert(teamId, client.copy(knownPositions = updatedPositions))
    }
}
