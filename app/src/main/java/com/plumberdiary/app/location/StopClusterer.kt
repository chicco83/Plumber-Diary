// StopClusterer.kt — v1.12.0 — 2026-09-29 (v1.0.0 — 2026-09-20 00:10 UTC)
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
 * sosta prosegue; uno spostamento confermato chiude la sosta corrente e ne
 * apre una nuova (kind = UNRESOLVED, il tracciato del tragitto non è
 * conservato come sosta a sé: solo il punto di arrivo conta ai fini di
 * permanenza/cliente).
 *
 * Non stateless: l'istanza vive per la durata del [LocationTrackingService]
 * e viene ricostruita dall'ultima sosta aperta su Firestore al riavvio del
 * servizio (dopo un boot o un kill del processo), non da zero.
 *
 * v1.12.0 — 2026-09-29: la precisione del fix ([LocationFix.accuracyMeters])
 * era ignorata. In modalità a basso consumo, in uno scantinato o in un locale
 * tecnico arrivano fix da cella con 300 m e più di errore: uno solo bastava a
 * "spostare" il tecnico e a spezzare un intervento di ore in più pezzi.
 * Ora:
 *  1. un fix è considerato FUORI solo se lo è anche tenendo conto del suo
 *     errore (distanza − precisione > raggio); altrimenti è compatibile con
 *     la sosta e ne prolunga la durata;
 *  2. lo spostamento va CONFERMATO da due fix fuori raggio consecutivi: il
 *     primo resta "in sospeso" ([hasPendingExit], il service accorcia il
 *     campionamento per confermarlo presto); se il successivo torna dentro,
 *     era rumore e viene scartato;
 *  3. solo i fix precisi (≤ [maxAccurateMeters]) spostano il centroide; se la
 *     sosta è nata da un fix impreciso, il primo fix preciso lo sostituisce.
 */
class StopClusterer(
    private val clusterRadiusMeters: Double = 100.0,
    private val maxAccurateMeters: Float = 100f,
) {
    private var current: MutableStop? = null

    // Primo fix fuori raggio, in attesa di conferma (punto 2 sopra).
    private var pendingExit: LocationFix? = null

    private data class MutableStop(
        var lat: Double,
        var lon: Double,
        var sampleCount: Int,       // fix precisi usati per il centroide (0 = centroide da fix impreciso)
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
        pendingExit = null
    }

    /** True se un fix fuori raggio attende conferma: conviene campionare più spesso. */
    fun hasPendingExit(): Boolean = pendingExit != null

    // Versione precedente (v1.0.0 — 2026-09-20), sostituita il 2026-09-29:
    //
    // fun onFix(fix: LocationFix): Stop? {
    //     val existing = current
    //     if (existing == null) { current = MutableStop(fix.lat, fix.lon, 1, fix.timestamp, fix.timestamp); return null }
    //     val distance = GeoUtils.distanceMeters(existing.lat, existing.lon, fix.lat, fix.lon)
    //     if (distance <= clusterRadiusMeters) {
    //         existing.sampleCount += 1
    //         existing.lat += (fix.lat - existing.lat) / existing.sampleCount
    //         existing.lon += (fix.lon - existing.lon) / existing.sampleCount
    //         existing.endedAt = fix.timestamp
    //         return null
    //     }
    //     val closed = Stop(startedAt = existing.startedAt, endedAt = existing.endedAt,
    //         lat = existing.lat, lon = existing.lon, kind = StopKind.UNRESOLVED)
    //     current = MutableStop(fix.lat, fix.lon, 1, fix.timestamp, fix.timestamp)
    //     return closed
    // }

    /**
     * Elabora un nuovo fix. Ritorna:
     * - null se il fix estende la sosta corrente (o è un'uscita da confermare);
     * - la sosta appena chiusa se lo spostamento è confermato (il chiamante la
     *   persiste/valuta contro la soglia).
     */
    fun onFix(fix: LocationFix): Stop? {
        val existing = current ?: run {
            current = newStop(fix)
            return null
        }

        if (!isDefinitelyOutside(existing, fix)) {
            // Fix compatibile con la sosta: eventuale uscita in sospeso era rumore.
            pendingExit = null
            extend(existing, fix)
            return null
        }

        val pending = pendingExit
        if (pending == null) {
            // Prima uscita: si attende conferma, la sosta resta aperta così com'è.
            pendingExit = fix
            return null
        }

        // Seconda uscita consecutiva: spostamento confermato. La sosta si chiude
        // all'ultimo fix compatibile, la nuova parte dal primo fix fuori raggio.
        pendingExit = null
        val closed = Stop(
            startedAt = existing.startedAt,
            endedAt = existing.endedAt,
            lat = existing.lat,
            lon = existing.lon,
            kind = StopKind.UNRESOLVED,
        )
        val next = newStop(pending)
        current = next
        // Il fix attuale va rivalutato rispetto alla nuova sosta: se è ancora
        // lontano (in movimento) diventa a sua volta un'uscita in sospeso.
        if (isDefinitelyOutside(next, fix)) pendingExit = fix else extend(next, fix)
        return closed
    }

    /** Stato dell'eventuale sosta ancora in corso (per la UI "Home" live). */
    fun currentOpenStop(): Stop? = current?.let {
        Stop(startedAt = it.startedAt, endedAt = it.endedAt, lat = it.lat, lon = it.lon, kind = StopKind.UNRESOLVED)
    }

    private fun newStop(fix: LocationFix) = MutableStop(
        lat = fix.lat,
        lon = fix.lon,
        sampleCount = if (fix.accuracyMeters <= maxAccurateMeters) 1 else 0,
        startedAt = fix.timestamp,
        endedAt = fix.timestamp,
    )

    private fun isDefinitelyOutside(stop: MutableStop, fix: LocationFix): Boolean {
        val distance = GeoUtils.distanceMeters(stop.lat, stop.lon, fix.lat, fix.lon)
        return distance - fix.accuracyMeters > clusterRadiusMeters
    }

    private fun extend(stop: MutableStop, fix: LocationFix) {
        stop.endedAt = maxOf(stop.endedAt, fix.timestamp)
        if (fix.accuracyMeters > maxAccurateMeters) return
        if (stop.sampleCount == 0) {
            // Centroide nato da un fix impreciso: sostituito dal primo preciso.
            stop.lat = fix.lat
            stop.lon = fix.lon
            stop.sampleCount = 1
            return
        }
        // Centroide aggiornato come media incrementale, per non derivare nel
        // tempo per il solo rumore GPS attorno a un punto fermo.
        stop.sampleCount += 1
        stop.lat += (fix.lat - stop.lat) / stop.sampleCount
        stop.lon += (fix.lon - stop.lon) / stop.sampleCount
    }
}
