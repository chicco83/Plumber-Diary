// ClientRecord.kt — v1.0.0 — 2026-09-20 00:10 UTC
package com.plumberdiary.app.data.model

/**
 * Anagrafica cliente: teams/{teamId}/clients/{clientId}. Condivisa da tutta
 * la squadra (requisito 11), non duplicata per utente.
 */
data class ClientRecord(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val fiscalCode: String = "",     // P.IVA / C.F.
    val defaultAddressLabel: String = "",
    val hoursReportEmail: String = "", // requisito 9: destinatario del mandatino ore
    val knownPositions: List<KnownPosition> = emptyList(),
    val photoIds: List<String> = emptyList(), // requisito 10: foto dalla galleria, copia "display"
    val createdBy: String = "",
    val createdAt: Long = 0L,
)

/**
 * Una posizione geografica già associata a questo cliente in passato, usata
 * da [com.plumberdiary.app.location.ClientMatcher] per proporlo automaticamente
 * quando una nuova sosta ricade nel raggio di tolleranza.
 */
data class KnownPosition(
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val radiusMeters: Double = 60.0,
    val lastSeenAt: Long = 0L,
)
