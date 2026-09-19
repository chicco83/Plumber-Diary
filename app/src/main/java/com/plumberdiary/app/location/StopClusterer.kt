// StopClusterer.kt — v1.0.0 — 2026-09-20 00:10 UTC
package com.plumberdiary.app.location

import com.plumberdiary.app.data.model.Stop
import com.plumberdiary.app.data.model.StopKind

/** Un fix GPS grezzo in ingresso al clusterer. */
data class LocationFix(
    val lat: Double,
    val lon: Double,
    val timestamp: Long,
    val accuracyMeters: Float,
)

/**
 * Raggruppa un flusso di fix GPS in "soste" (requisito 2 di context.md):
 * finché i fix restano entro [clusterRadiusMeters] dal centroide corrente, la
 * sosta prosegue; un fix fuori raggio chiude la sosta corrente e ne apre una
 * nuova "in movimento" (kind = UNRESOLVED, il tracciato del tragitto non è
 * conservato come sosta a sé: solo il punto di arrivo conta ai fini di
 * permanenza/cliente).
 *
 * Non stateless: l'istanza vive per la durata del [com.plumberdiary.app.location.LocationTrackingService]
 * e viene ricostruita dall'ultima sosta aperta su Firestore al riavvio del
 * servizio (dopo un boot o un kill del processo), non da zero.
 */
class StopClusterer(
    private val clusterRadiusMeters: Double = 100.0,
) {
    private var current: MutableStop? = null

    private data class MutableStop(
        var lat: Double,
        var lon: Double,
        var sampleCount: Int,
        val startedAt: Long,
        var endedAt: Long,
    )

    /** Riprende una sosta già aperta (persistita) invece di ripartire da zero. */
    fun resume(openStop: Stop) {
        current = MutableStop(
            lat = openStop.lat,
            lon = openStop.lon,
            sampleCount = 1,
            startedAt = openStop.startedAt,
            endedAt = openStop.endedAt,
        )
    }

    /**
     * Elabora un nuovo fix. Ritorna:
     * - null se il fix estende semplicemente la sosta corrente;
     * - la sosta appena chiusa se il fix indica che ci si è spostati oltre
     *   [clusterRadiusMeters] (il chiamante la persiste/valuta contro la soglia).
     */
    fun onFix(fix: LocationFix): Stop? {
        val existing = current
        if (existing == null) {
            current = MutableStop(fix.lat, fix.lon, 1, fix.timestamp, fix.timestamp)
            return null
        }

        val distance = GeoUtils.distanceMeters(existing.lat, existing.lon, fix.lat, fix.lon)
        if (distance <= clusterRadiusMeters) {
            // Centroide aggiornato come media incrementale, per non derivare
            // nel tempo per il solo rumore GPS attorno a un punto fermo.
            existing.sampleCount += 1
            existing.lat += (fix.lat - existing.lat) / existing.sampleCount
            existing.lon += (fix.lon - existing.lon) / existing.sampleCount
            existing.endedAt = fix.timestamp
            return null
        }

        // Spostamento oltre soglia: la sosta corrente si chiude qui.
        val closed = Stop(
            startedAt = existing.startedAt,
            endedAt = existing.endedAt,
            lat = existing.lat,
            lon = existing.lon,
            kind = StopKind.UNRESOLVED,
        )
        current = MutableStop(fix.lat, fix.lon, 1, fix.timestamp, fix.timestamp)
        return closed
    }

    /** Stato dell'eventuale sosta ancora in corso (per la UI "Home" live). */
    fun currentOpenStop(): Stop? = current?.let {
        Stop(startedAt = it.startedAt, endedAt = it.endedAt, lat = it.lat, lon = it.lon, kind = StopKind.UNRESOLVED)
    }
}
