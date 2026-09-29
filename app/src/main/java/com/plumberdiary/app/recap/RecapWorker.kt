// RecapWorker.kt — v1.8.0 — 2026-09-29
package com.plumberdiary.app.recap

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.plumberdiary.app.data.repository.StopRepository
import com.plumberdiary.app.notification.RecapNotifier
import com.plumberdiary.app.session.SessionStore
import java.util.Calendar

/**
 * Eseguito ogni sera all'orario del recap (vedi [RecapScheduler]): conta le
 * soste di oggi non ancora confermate e mostra la notifica "Recap pronto",
 * che apre la schermata Recap.
 *
 * La mail all'amministrazione (default ON) NON parte da qui ma alla conferma
 * del recap (RecapScreen): così contiene clienti, orari e note già corretti
 * dall'utente invece dei dati grezzi del tracciamento.
 */
class RecapWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Gira anche ad app chiusa: la sessione si ricostruisce da Firebase Auth
        // + SessionStore, come in BootRestartReceiver.
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val teamId = SessionStore(applicationContext).readTeamId() ?: return Result.success()

        val dayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val toReview = try {
            StopRepository().getStopsForDay(teamId, uid, dayStart, System.currentTimeMillis())
                .count { !it.confirmedInRecap }
        } catch (e: Exception) {
            return Result.retry()
        }

        if (toReview > 0) RecapNotifier.notify(applicationContext, toReview)
        return Result.success()
    }
}
