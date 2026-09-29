// RecapScheduler.kt — v1.8.0 — 2026-09-29
package com.plumberdiary.app.recap

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Requisito 3/6: all'orario impostato nelle Opzioni l'app propone il recap
 * della giornata. Fino a v1.7.0 non c'era nessuna programmazione: il recap si
 * vedeva solo aprendo a mano la schermata.
 *
 * Lavoro periodico giornaliero di WorkManager (sopravvive a riavvii e kill
 * del processo) con ritardo iniziale fino al prossimo orario di recap.
 * WorkManager può ritardarlo di qualche minuto per risparmio batteria: per un
 * promemoria serale è accettabile e non richiede il permesso degli allarmi
 * esatti.
 */
object RecapScheduler {
    private const val WORK_NAME = "recap_serale"

    /**
     * [reschedule] = true quando l'orario è cambiato (salvataggio Opzioni):
     * riparte dal nuovo orario. false all'avvio dell'app: se è già programmato
     * non lo tocca (altrimenti ogni apertura sposterebbe il ciclo).
     */
    fun schedule(context: Context, hour: Int, minute: Int, reschedule: Boolean) {
        val request = PeriodicWorkRequestBuilder<RecapWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(millisUntilNext(hour, minute), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            if (reschedule) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private fun millisUntilNext(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return next.timeInMillis - now.timeInMillis
    }
}
