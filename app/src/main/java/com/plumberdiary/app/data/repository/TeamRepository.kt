// TeamRepository.kt — v1.8.0 — 2026-09-29 (v1.0.0 — 2026-09-20 00:10 UTC)
package com.plumberdiary.app.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.toObject
import com.plumberdiary.app.data.FirestorePaths
import com.plumberdiary.app.data.model.LiveLocation
import com.plumberdiary.app.data.model.MemberStatus
import com.plumberdiary.app.data.model.TeamMember
import kotlinx.coroutines.tasks.await

/**
 * Membri della squadra e loro posizione live (requisito 11 — schermata
 * *Squadra*). L'iscrizione a una squadra (creazione del documento
 * teams/{teamId}/members/{uid}) NON passa da questo repository: è compito
 * dell'endpoint backend accept-invite (vedi backend/api/accept-invite.js),
 * mai una scrittura diretta del client — altrimenti chiunque abbia un
 * account Google potrebbe autoiscriversi a una squadra qualsiasi.
 */
class TeamRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    /** Ascolta in tempo reale i membri della squadra per la mappa condivisa. */
    fun listenMembers(teamId: String, onUpdate: (List<TeamMember>) -> Unit): ListenerRegistration {
        return firestore.collection(FirestorePaths.members(teamId))
            .addSnapshotListener { snapshot, _ ->
                val members = snapshot?.documents?.mapNotNull { it.toObject<TeamMember>() } ?: emptyList()
                onUpdate(members)
            }
    }

    /** v1.8.0 — 2026-09-29: lettura una tantum dei membri (Storico, recap colleghi). */
    suspend fun getMembers(teamId: String): List<TeamMember> =
        firestore.collection(FirestorePaths.members(teamId)).get().await()
            .documents.mapNotNull { it.toObject<TeamMember>() }

    /** v1.8.0 — 2026-09-29: sostituisce l'accesso Firestore diretto che c'era in SquadraScreen. */
    suspend fun getMember(teamId: String, uid: String): TeamMember? =
        firestore.document(FirestorePaths.member(teamId, uid)).get().await().toObject<TeamMember>()

    // Versione precedente (v1.0.0 — 2026-09-20), sostituita il 2026-09-29:
    // attendeva la conferma del server. Chiamata dal foreground service a ogni
    // fix, offline restava sospesa e bloccava l'elaborazione dei fix successivi.
    //
    // suspend fun updateOwnLiveLocation(teamId: String, uid: String, location: LiveLocation) {
    //     firestore.document(FirestorePaths.member(teamId, uid))
    //         .set(mapOf("liveLocation" to location), com.google.firebase.firestore.SetOptions.merge())
    //         .await()
    // }

    /**
     * Aggiorna solo il proprio campo liveLocation (mai quello di un collega:
     * le regole di sicurezza lo negano). Chiamata dal foreground service
     * quando "condividi la mia posizione" è attivo. Non attende il server:
     * Firestore la salva in cache e la sincronizza appena c'è rete.
     */
    fun updateOwnLiveLocation(teamId: String, uid: String, location: LiveLocation) {
        firestore.document(FirestorePaths.member(teamId, uid))
            .set(mapOf("liveLocation" to location), SetOptions.merge())
            .addOnFailureListener { Log.w(TAG, "Aggiornamento posizione live fallito", it) }
    }

    /** Tracciamento fermato: i colleghi non vedono più un marker "attivo" fermo nel tempo. */
    fun markOffline(teamId: String, uid: String) {
        firestore.document(FirestorePaths.member(teamId, uid))
            .set(mapOf("liveLocation" to mapOf("status" to MemberStatus.OFFLINE.name)), SetOptions.merge())
            .addOnFailureListener { Log.w(TAG, "Segnalazione offline fallita", it) }
    }

    private companion object {
        const val TAG = "TeamRepository"
    }
}
