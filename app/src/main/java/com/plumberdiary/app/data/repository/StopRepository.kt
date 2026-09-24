// StopRepository.kt — v1.0.0 — 2026-09-20 00:10 UTC
package com.plumberdiary.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import com.plumberdiary.app.data.FirestorePaths
import com.plumberdiary.app.data.model.Stop
import kotlinx.coroutines.tasks.await

/**
 * Accesso alle soste dell'utente corrente. Scritture dirette dal client:
 * qui è corretto (a differenza di membership/inviti squadra) perché ogni
 * utente scrive solo le proprie soste, mai quelle di un collega — le regole
 * di sicurezza (backend/firestore.rules) impongono lo stesso vincolo lato
 * server.
 */
class StopRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    suspend fun upsert(teamId: String, uid: String, stop: Stop): String {
        val collection = firestore.collection(FirestorePaths.stops(teamId, uid))
        val docRef = if (stop.id.isBlank()) collection.document() else collection.document(stop.id)
        docRef.set(stop.copy(id = docRef.id)).await()
        return docRef.id
    }

    suspend fun getOpenStop(teamId: String, uid: String): Stop? {
        val snapshot = firestore.collection(FirestorePaths.stops(teamId, uid))
            .whereEqualTo("endedAt", 0L)
            .limit(1)
            .get()
            .await()
        return snapshot.documents.firstOrNull()?.toObject<Stop>()
    }

    /** v1.6.0 — 2026-09-23: lettura di una singola sosta (schermate Dettaglio/Rapportino). */
    suspend fun getStop(teamId: String, uid: String, stopId: String): Stop? {
        val doc = firestore.document(FirestorePaths.stop(teamId, uid, stopId)).get().await()
        return doc.toObject<Stop>()
    }

    suspend fun getStopsForDay(teamId: String, uid: String, dayStartMillis: Long, dayEndMillis: Long): List<Stop> {
        val snapshot = firestore.collection(FirestorePaths.stops(teamId, uid))
            .whereGreaterThanOrEqualTo("startedAt", dayStartMillis)
            .whereLessThan("startedAt", dayEndMillis)
            .orderBy("startedAt")
            .get()
            .await()
        return snapshot.documents.mapNotNull { it.toObject<Stop>() }
    }
}
