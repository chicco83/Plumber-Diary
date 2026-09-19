// DailyDistanceCalculator.kt — v1.0.0 — 2026-09-20 00:10 UTC
package com.plumberdiary.app.recap

import com.plumberdiary.app.data.model.Stop
import com.plumberdiary.app.location.GeoUtils

/**
 * Requisito 16: calcola i km percorsi tra una sosta e la successiva per la
 * schermata Recap/Dettaglio e per la Dashboard mensile. Applicato in
 * post-elaborazione sull'intera sequenza ordinata delle soste del giorno
 * (non nel foreground service, che vede una sosta alla volta) perché è lì
 * che si ha visibilità del punto precedente in ordine cronologico, incluso
 * quando l'utente corregge/riordina gli orari nel recap.
 */
object DailyDistanceCalculator {

    /** Ritorna la stessa lista con distanceFromPreviousMeters ricalcolato. */
    fun withDistances(stopsSortedByStart: List<Stop>): List<Stop> {
        var previous: Stop? = null
        return stopsSortedByStart.map { stop ->
            val distance = previous?.let { GeoUtils.distanceMeters(it.lat, it.lon, stop.lat, stop.lon) } ?: 0.0
            previous = stop
            stop.copy(distanceFromPreviousMeters = distance)
        }
    }

    fun totalKm(stops: List<Stop>): Double = stops.sumOf { it.distanceFromPreviousMeters } / 1000.0
}
