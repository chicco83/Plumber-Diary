// TeamRepository.kt — v1.0.0 — 2026-09-20 00:10 UTC
package com.plumberdiary.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.toObject
import com.plumberdiary.app.data.FirestorePaths
import com.plumberdiary.app.data.model.LiveLocation
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

    /**
     * Aggiorna solo il proprio campo liveLocation (mai quello di un collega:
     * le regole di sicurezza lo negano). Chiamata dal worker di sampling
     * quando "vedi posizione della squadra" è attivo nelle proprie
     * [com.plumberdiary.app.data.model.UserSettings].
     */
    suspend fun updateOwnLiveLocation(teamId: String, uid: String, location: LiveLocation) {
        firestore.document(FirestorePaths.member(teamId, uid))
            .set(mapOf("liveLocation" to location), com.google.firebase.firestore.SetOptions.merge())
            .await()
    }
}
