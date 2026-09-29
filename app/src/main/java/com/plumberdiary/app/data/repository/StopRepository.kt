// StopRepository.kt — v1.8.0 — 2026-09-29 (v1.7.0 — 2026-09-23: getStop; v1.0.0 — 2026-09-20)
package com.plumberdiary.app.data.repository

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.toObject
import com.plumberdiary.app.data.FirestorePaths
import com.plumberdiary.app.data.model.Stop
import com.plumberdiary.app.data.model.StopKind
import kotlinx.coroutines.tasks.await

/**
 * Accesso alle soste dell'utente corrente. Scritture dirette dal client:
 * qui è corretto (a differenza di membership/inviti squadra) perché ogni
 * utente scrive solo le proprie soste, mai quelle di un collega — le regole
 * di sicurezza (backend/firestore.rules) impongono lo stesso vincolo lato
 * server.
 *
 * v1.8.0 — 2026-09-29: sulla stessa sosta scrivono due attori diversi — il
 * foreground service (orari/posizione, a ogni fix) e l'utente dalle schermate
 * (cliente, note, materiali, foto, conferme). Prima entrambi facevano set()
 * dell'INTERO documento, quindi il service cancellava ogni 1-5 minuti quanto
 * inserito dall'utente. Ora ognuno scrive con merge SOLO i propri campi:
 * [writeTrackingFields] per il service, [saveUserEdits] per la UI.
 */
class StopRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    // Versione precedente (v1.0.0 — 2026-09-20), rimossa il 2026-09-29 perché
    // il set() dell'intero documento era la causa della perdita di dati:
    //
    // suspend fun upsert(teamId: String, uid: String, stop: Stop): String {
    //     val collection = firestore.collection(FirestorePaths.stops(teamId, uid))
    //     val docRef = if (stop.id.isBlank()) collection.document() else collection.document(stop.id)
    //     docRef.set(stop.copy(id = docRef.id)).await()
    //     return docRef.id
    // }

    /** Id di una nuova sosta generato localmente (funziona anche offline). */
    fun newStopId(teamId: String, uid: String): String =
        firestore.collection(FirestorePaths.stops(teamId, uid)).document().id

    /**
     * Campi di competenza del SERVICE di tracciamento. Non attende la conferma
     * del server: Firestore applica subito la scrittura alla cache locale e la
     * sincronizza quando torna la rete. Attenderla (await) offline — scantinati,
     * locali tecnici — bloccava il service e generava soste duplicate.
     *
     * [kind] viene scritto solo se non null: il service lo passa alla creazione
     * della sosta (UNRESOLVED) o quando la classifica come sede/pausa, mai
     * sopra un CLIENT scelto dall'utente.
     */
    fun writeTrackingFields(
        teamId: String,
        uid: String,
        stopId: String,
        startedAt: Long,
        endedAt: Long,
        lat: Double,
        lon: Double,
        lastFixAt: Long,
        kind: StopKind?,
    ) {
        val fields = mutableMapOf<String, Any>(
            "id" to stopId,
            "startedAt" to startedAt,
            "endedAt" to endedAt,
            "lat" to lat,
            "lon" to lon,
            "lastFixAt" to lastFixAt,
        )
        if (kind != null) fields["kind"] = kind.name
        firestore.document(FirestorePaths.stop(teamId, uid, stopId))
            .set(fields, SetOptions.merge())
            .addOnFailureListener { Log.w(TAG, "Scrittura tracciamento fallita per $stopId", it) }
    }

    /**
     * Campi di competenza dell'UTENTE (recap, dettaglio, conferma in tempo
     * reale, storico). Gli orari si scrivono solo se [includeTimes] è true
     * (correzione manuale nel Dettaglio di una sosta già chiusa): per una sosta
     * ancora aperta sono del service.
     */
    suspend fun saveUserEdits(teamId: String, uid: String, stop: Stop, includeTimes: Boolean = false) {
        val fields = mutableMapOf<String, Any?>(
            "id" to stop.id,
            "kind" to stop.kind.name,
            "clientId" to stop.clientId,
            "clientSuggested" to stop.clientSuggested,
            "notes" to stop.notes,
            "reminderText" to stop.reminderText,
            "articleLines" to stop.articleLines,
            "distanceFromPreviousMeters" to stop.distanceFromPreviousMeters,
            "realtimeConfirmedAt" to stop.realtimeConfirmedAt,
            "realtimeDismissedAt" to stop.realtimeDismissedAt,
            "confirmedInRecap" to stop.confirmedInRecap,
        )
        if (includeTimes && stop.endedAt > 0L) {
            fields["startedAt"] = stop.startedAt
            fields["endedAt"] = stop.endedAt
        }
        firestore.document(FirestorePaths.stop(teamId, uid, stop.id))
            .set(fields, SetOptions.merge())
            .await()
    }

    /** Aggiunge foto senza riscrivere la lista intera (niente perdita di foto concorrenti). */
    suspend fun addPhotoIds(teamId: String, uid: String, stopId: String, photoIds: List<String>) {
        if (photoIds.isEmpty()) return
        firestore.document(FirestorePaths.stop(teamId, uid, stopId))
            .update("photoIds", FieldValue.arrayUnion(*photoIds.toTypedArray()))
            .await()
    }

    suspend fun getOpenStop(teamId: String, uid: String): Stop? {
        val snapshot = firestore.collection(FirestorePaths.stops(teamId, uid))
            .whereEqualTo("endedAt", 0L)
            .limit(1)
            .get()
            .await()
        return snapshot.documents.firstOrNull()?.toObject<Stop>()
    }

    /** v1.7.0 — 2026-09-23: lettura di una singola sosta (schermate Dettaglio/Rapportino). */
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

    private companion object {
        const val TAG = "StopRepository"
    }
}
