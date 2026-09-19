// CurrentSession.kt — v1.0.0 — 2026-09-20 00:10 UTC
package com.plumberdiary.app.session

/**
 * Identità dell'utente autenticato e squadra attiva, popolata dopo il login
 * Google (Firebase Auth) e la selezione/creazione della squadra. Tenuta in
 * memoria di processo: un Service o un Worker che parte dopo un riavvio la
 * ricostruisce da SharedPreferences/DataStore prima di usarla (dettaglio da
 * implementare insieme allo schermo di login, fuori scope di questo
 * scaffolding).
 */
object CurrentSession {
    data class Active(val uid: String, val teamId: String)

    @Volatile
    private var active: Active? = null

    fun set(uid: String, teamId: String) {
        active = Active(uid, teamId)
    }

    fun clear() {
        active = null
    }

    fun current(): Active? = active

    fun requireActive(): Active =
        active ?: error("Nessuna sessione attiva: login o selezione squadra mancanti")
}
