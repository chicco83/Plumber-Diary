// DepotAndBreakFilter.kt — v1.0.0 — 2026-09-20 00:10 UTC
package com.plumberdiary.app.location

import com.plumberdiary.app.data.model.ScheduledBreak
import com.plumberdiary.app.data.model.UserSettings
import java.util.Calendar

/**
 * Requisito 13: una sosta sulla sede/deposito, o dentro una fascia oraria di
 * pausa, non deve mai generare la proposta "nuovo possibile cliente" né la
 * notifica di conferma in tempo reale (requisito 14). Va interrogato PRIMA
 * di [ClientMatcher] e prima di valutare la soglia di permanenza in
 * [com.plumberdiary.app.location.LocationTrackingService].
 */
object DepotAndBreakFilter {

    fun isAtDepot(lat: Double, lon: Double, settings: UserSettings): Boolean {
        val depotLat = settings.depotLat ?: return false
        val depotLon = settings.depotLon ?: return false
        return GeoUtils.distanceMeters(lat, lon, depotLat, depotLon) <= settings.depotRadiusMeters
    }

    fun isDuringBreak(timestampMillis: Long, breaks: List<ScheduledBreak>): Boolean {
        if (breaks.isEmpty()) return false
        val cal = Calendar.getInstance().apply { timeInMillis = timestampMillis }
        val minutesOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return breaks.any { b ->
            val start = b.startHour * 60 + b.startMinute
            val end = b.endHour * 60 + b.endMinute
            minutesOfDay in start..end
        }
    }
}
