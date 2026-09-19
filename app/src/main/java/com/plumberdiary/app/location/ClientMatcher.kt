// ClientMatcher.kt — v1.0.0 — 2026-09-20 00:10 UTC
package com.plumberdiary.app.location

import com.plumberdiary.app.data.model.ClientRecord

/**
 * Requisito 4: se una sosta ricade su una posizione già nota da un intervento
 * precedente, propone automaticamente quel cliente (restando modificabile).
 * Il chiamante passa la lista di [ClientRecord] della squadra già caricata
 * in memoria (poche decine/centinaia di clienti: nessun bisogno di una query
 * geospaziale lato server per un MVP, vedi limiti Firestore in context.md).
 */
object ClientMatcher {

    /** Il miglior cliente noto per (lat, lon), o null se nessuna posizione nota è abbastanza vicina. */
    fun findSuggestedClient(lat: Double, lon: Double, candidates: List<ClientRecord>): ClientRecord? {
        var best: ClientRecord? = null
        var bestDistance = Double.MAX_VALUE

        for (client in candidates) {
            for (known in client.knownPositions) {
                val distance = GeoUtils.distanceMeters(lat, lon, known.lat, known.lon)
                if (distance <= known.radiusMeters && distance < bestDistance) {
                    best = client
                    bestDistance = distance
                }
            }
        }
        return best
    }
}
