// PlumberDiaryApp.kt — v1.0.0 — 2026-09-20 00:10 UTC
package com.plumberdiary.app

import android.app.Application
import com.google.firebase.FirebaseApp

class PlumberDiaryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        // TODO: ripristinare com.plumberdiary.app.session.CurrentSession da
        // storage persistente (DataStore) se l'utente aveva già fatto login
        // e scelto una squadra, così un riavvio del telefono può far
        // ripartire il tracciamento tramite BootRestartReceiver.
    }
}
