// StopClustererTest.kt — v1.12.0 — 2026-09-29
//
// Test JVM (nessun dispositivo) del raggruppamento dei fix GPS in soste,
// scritti con la correzione v1.12.0 (precisione del fix e conferma dello
// spostamento). Eseguiti con successo il 2026-09-29 in un progetto JVM
// separato; nel progetto Android: ./gradlew :app:testDebugUnitTest
package com.plumberdiary.app.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StopClustererTest {
    private val baseLat = 44.4949 // Bologna
    private val baseLon = 11.3426

    /** Fix spostato di [dLatMeters] verso nord, al minuto [minute], con precisione [acc]. */
    private fun fix(dLatMeters: Double, minute: Int, acc: Float = 20f) =
        LocationFix(baseLat + dLatMeters / 111_320.0, baseLon, minute * 60_000L, acc)

    // Un fix da cella molto impreciso non deve spezzare la sosta (scantinato).
    @Test fun singleInaccurateFixDoesNotSplit() {
        val c = StopClusterer()
        assertNull(c.onFix(fix(0.0, 0)))
        assertNull(c.onFix(fix(10.0, 5)))
        assertNull(c.onFix(fix(400.0, 10, acc = 500f)))
        assertFalse(c.hasPendingExit())
        assertNull(c.onFix(fix(5.0, 15)))
        assertEquals(15 * 60_000L, c.currentOpenStop()!!.endedAt)
    }

    // Un solo fix preciso fuori raggio resta in sospeso; se il successivo rientra era rumore.
    @Test fun singleOutlierIsDiscarded() {
        val c = StopClusterer()
        c.onFix(fix(0.0, 0)); c.onFix(fix(0.0, 5))
        assertNull(c.onFix(fix(300.0, 10)))
        assertTrue(c.hasPendingExit())
        assertNull(c.onFix(fix(0.0, 11)))
        assertFalse(c.hasPendingExit())
        assertEquals(0L, c.currentOpenStop()!!.startedAt)
    }

    // Due fix fuori raggio consecutivi: la sosta si chiude all'ultimo fix compatibile.
    @Test fun confirmedMoveClosesStop() {
        val c = StopClusterer()
        c.onFix(fix(0.0, 0)); c.onFix(fix(0.0, 30))
        assertNull(c.onFix(fix(2000.0, 35)))
        val closed = c.onFix(fix(2050.0, 36))
        assertNotNull(closed)
        assertEquals(0L, closed!!.startedAt)
        assertEquals(30 * 60_000L, closed.endedAt)
        val open = c.currentOpenStop()!!
        assertEquals(35 * 60_000L, open.startedAt)
        assertEquals(36 * 60_000L, open.endedAt)
    }

    // In movimento continuo si generano soste-transito brevi (poi scartate dal service, < 5 min).
    @Test fun movingKeepsPending() {
        val c = StopClusterer()
        c.onFix(fix(0.0, 0)); c.onFix(fix(0.0, 20))
        c.onFix(fix(1000.0, 21))
        assertNotNull(c.onFix(fix(2000.0, 22)))
        assertTrue(c.hasPendingExit())
        val transit = c.onFix(fix(3000.0, 23))
        assertNotNull(transit)
        assertEquals(transit!!.startedAt, transit.endedAt)
    }

    // Una sosta nata da un fix impreciso prende il centroide dal primo fix preciso.
    @Test fun inaccurateStartReplacedByAccurateFix() {
        val c = StopClusterer()
        c.onFix(fix(80.0, 0, acc = 300f))
        c.onFix(fix(0.0, 5))
        assertEquals(baseLat, c.currentOpenStop()!!.lat, 1e-9)
    }
}
