// Stop.kt — v1.0.0 — 2026-09-20 00:10 UTC
package com.plumberdiary.app.data.model

/**
 * Una sosta rilevata durante la giornata: teams/{teamId}/members/{uid}/stops/{stopId}.
 * Creata/aggiornata da [com.plumberdiary.app.location.StopClusterer] man mano
 * che arrivano nuovi fix GPS, e poi corretta dall'utente nel recap serale.
 */
data class Stop(
    val id: String = "",
    val startedAt: Long = 0L,
    val endedAt: Long = 0L,          // 0L finché la sosta è quella corrente (in corso)
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val addressLabel: String = "",   // reverse geocoding best-effort, mai bloccante
    val kind: StopKind = StopKind.UNRESOLVED,
    val clientId: String? = null,    // valorizzato quando kind = CLIENT
    val clientSuggested: Boolean = false, // true se il cliente è stato proposto automaticamente
    val notes: String = "",
    val articleLines: List<ArticleLine> = emptyList(),
    val photoIds: List<String> = emptyList(),
    val distanceFromPreviousMeters: Double = 0.0, // requisito 16: km percorsi
    val calendarReminderEventId: String? = null,
    val realtimeConfirmedAt: Long? = null, // requisito 14: notifica in tempo reale accettata
    val confirmedInRecap: Boolean = false,
)

enum class StopKind {
    UNRESOLVED,   // sotto soglia, o soglia superata ma non ancora processata
    NEW_CLIENT,   // soglia superata, nessuna posizione nota qui
    CLIENT,       // associata a un cliente (suggerito o scelto manualmente)
    DEPOT,        // sede/deposito (requisito 13): mai proposta come cliente
    BREAK,        // pausa (requisito 13): mai proposta come cliente
}

data class ArticleLine(
    val articleId: String = "",
    val code: String = "",
    val description: String = "",
    val unitPrice: Double = 0.0,
    val quantity: Int = 1,
)
