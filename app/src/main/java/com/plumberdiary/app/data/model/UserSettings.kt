// UserSettings.kt — v1.0.0 — 2026-09-20 00:10 UTC
package com.plumberdiary.app.data.model

/**
 * Opzioni per-utente: teams/{teamId}/members/{uid}/settings/preferences.
 * Ogni default riflette una decisione esplicita presa in context.md — non
 * cambiarli senza aggiornare anche quel documento.
 */
data class UserSettings(
    val recapTimeHour: Int = 18,
    val recapTimeMinute: Int = 30,
    val clientDetectionThresholdMinutes: Int = 15, // default richiesto esplicitamente
    val realtimeConfirmationEnabled: Boolean = true, // requisito 14
    val recapEmailEnabled: Boolean = true,           // default ON, richiesto esplicitamente
    val recapEmailAddress: String = "",
    val seeTeamLocationEnabled: Boolean = true,
    val seeTeammatesRecapEnabled: Boolean = false,   // default OFF, richiesto esplicitamente: dato sensibile
    val positionRetentionMonths: Int = 12,
    val depotLat: Double? = null,                    // requisito 13
    val depotLon: Double? = null,
    val depotRadiusMeters: Double = 100.0,
    val breaks: List<ScheduledBreak> = emptyList(),  // requisito 13
    val googleCalendarConnected: Boolean = false,
)

data class ScheduledBreak(
    val label: String = "Pausa pranzo",
    val startHour: Int = 12,
    val startMinute: Int = 30,
    val endHour: Int = 13,
    val endMinute: Int = 30,
)
