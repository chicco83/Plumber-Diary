// GeoUtils.kt — v1.0.0 — 2026-09-20 00:10 UTC
package com.plumberdiary.app.location

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Funzioni geografiche condivise da clustering, matching cliente e calcolo km
 * (requisito 16). Distanza in linea d'aria (formula di Haversine): scelta
 * deliberata invece di un servizio di instradamento stradale (es. OSRM
 * pubblico), per restare senza dipendenze esterne/di rete nel calcolo più
 * frequente — vedi context.md, "Km percorsi", per la valutazione fatta.
 * L'errore rispetto alla distanza stradale reale è accettabile per una stima
 * di rimborso carburante, non per una fatturazione di precisione.
 */
object GeoUtils {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }
}
