// PlumberDiaryApp.kt — v1.1.0 — 2026-09-20 00:30 UTC
package com.plumberdiary.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.plumberdiary.app.session.CurrentSession
import com.plumberdiary.app.session.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Versione precedente (v1.0.0 — 2026-09-20 00:10 UTC), sostituita il 2026-09-20
// perché il ripristino della sessione era solo un TODO:
//
// class PlumberDiaryApp : Application() {
//     override fun onCreate() {
//         super.onCreate()
//         FirebaseApp.initializeApp(this)
//         // TODO: ripristinare com.plumberdiary.app.session.CurrentSession da
//         // storage persistente (DataStore) se l'utente aveva già fatto login
//         // e scelto una squadra, così un riavvio del telefono può far
//         // ripartire il tracciamento tramite BootRestartReceiver.
//     }
// }

class PlumberDiaryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)

        // Ripristina CurrentSession da DataStore (teamId) + Firebase Auth
        // (uid, già persistito autonomamente dall'SDK): necessario perché
        // BootRestartReceiver e LocationTrackingService leggono
        // CurrentSession.requireActive() e altrimenti fallirebbero dopo un
        // riavvio del telefono o un kill del processo.
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            CoroutineScope(Dispatchers.IO).launch {
                val teamId = SessionStore(this@PlumberDiaryApp).readTeamId()
                if (teamId != null) {
                    CurrentSession.set(uid, teamId)
                }
            }
        }
    }
}
