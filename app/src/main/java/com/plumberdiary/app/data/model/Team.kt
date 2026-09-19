// Team.kt — v1.0.0 — 2026-09-20 00:10 UTC
package com.plumberdiary.app.data.model

/**
 * Una squadra di tecnici. Anagrafica clienti e listino articoli vivono sotto
 * teams/{teamId}/clients e teams/{teamId}/articles: un'unica fonte condivisa
 * da tutti i membri (requisito 11 di context.md), mai duplicata per utente.
 */
data class Team(
    val id: String = "",
    val name: String = "",
    val ownerUid: String = "",
    val createdAt: Long = 0L,
)

/**
 * Appartenenza di un utente a una squadra: teams/{teamId}/members/{uid}.
 * La creazione di questo documento NON è mai una scrittura diretta del client
 * (vedi context.md, sezione architettura): un invito va accettato tramite un
 * endpoint backend che verifica il codice/link, altrimenti chiunque abbia un
 * account Google potrebbe autoiscriversi a una squadra qualsiasi.
 */
data class TeamMember(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val colorHex: String = "#0F766E",
    val joinedAt: Long = 0L,
    // Opzioni di visibilità individuali (requisito 11): ognuno decide se farsi
    // vedere in tempo reale sulla mappa squadra e se i propri recap confermati
    // sono consultabili dai colleghi. Il default "vedi recap colleghi" è OFF
    // sull'opzione di CHI GUARDA, non su chi è guardato: vedi UserSettings.
    val liveLocation: LiveLocation? = null,
)

data class LiveLocation(
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val updatedAt: Long = 0L,
    val status: MemberStatus = MemberStatus.ACTIVE,
    val currentAddressLabel: String = "",
)

enum class MemberStatus { ACTIVE, PAUSED, OFFLINE }
