// Format.kt — v1.6.0 — 2026-09-23
//
// Formattatori condivisi da tutte le schermate (orari, durate, km, euro) e
// helper per la sessione: prima delle schermate reali dello scaffolding non
// esisteva un punto unico, ogni UI avrebbe duplicato i SimpleDateFormat.
package com.plumberdiary.app.ui.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object Format {
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.ITALY)
    private val dateTimeFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY)
    private val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.ITALY)
    private val dayFmt = SimpleDateFormat("EEE dd/MM/yyyy", Locale.ITALY)

    fun time(millis: Long): String = if (millis <= 0L) "—" else timeFmt.format(Date(millis))
    fun dateTime(millis: Long): String = if (millis <= 0L) "—" else dateTimeFmt.format(Date(millis))
    fun date(millis: Long): String = if (millis <= 0L) "—" else dateFmt.format(Date(millis))
    fun day(millis: Long): String = dayFmt.format(Date(millis))

    /** Durata in minuti tra due istanti (minimo 0). */
    fun durationMinutes(startMillis: Long, endMillis: Long): Long =
        maxOf(0L, TimeUnit.MILLISECONDS.toMinutes(endMillis - startMillis))

    /** "2h 05m". */
    fun durationLabel(minutes: Long): String = "${minutes / 60}h ${"%02d".format(minutes % 60)}m"

    /** "3,4 km". */
    fun km(meters: Double): String = String.format(Locale.ITALY, "%.1f km", meters / 1000.0)

    /** "€ 45,90". */
    fun euros(value: Double): String = String.format(Locale.ITALY, "€ %.2f", value)
}
