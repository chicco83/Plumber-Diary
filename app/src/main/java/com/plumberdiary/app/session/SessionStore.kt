// SessionStore.kt — v1.12.0 — 2026-09-29 (v1.0.0 — 2026-09-20 00:30 UTC)
package com.plumberdiary.app.session

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "session")

/**
 * Persiste la squadra scelta dall'utente (uid arriva da Firebase Auth, che
 * ha già la propria persistenza) tra un riavvio dell'app/del telefono e
 * l'altro, così [CurrentSession] può essere ripristinata in
 * [com.plumberdiary.app.PlumberDiaryApp] prima che
 * [com.plumberdiary.app.location.BootRestartReceiver] provi a far ripartire
 * il tracciamento.
 *
 * v1.12.0 — 2026-09-29: salva anche se l'utente VUOLE il tracciamento attivo
 * ([saveTrackingEnabled], impostato da "Inizia"/"Ferma tracciamento" in Home).
 * Prima, dopo un riavvio del telefono, il tracciamento ripartiva sempre, anche
 * se la sera era stato fermato a mano: il tecnico veniva tracciato a casa.
 */
class SessionStore(private val context: Context) {
    private val teamIdKey = stringPreferencesKey("team_id")
    private val trackingEnabledKey = booleanPreferencesKey("tracking_enabled")

    suspend fun saveTeamId(teamId: String) {
        context.dataStore.edit { it[teamIdKey] = teamId }
    }

    suspend fun readTeamId(): String? = context.dataStore.data.first()[teamIdKey]

    suspend fun saveTrackingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[trackingEnabledKey] = enabled }
    }

    /** Default false: dopo l'installazione il tracciamento parte solo da "Inizia tracciamento". */
    suspend fun readTrackingEnabled(): Boolean = context.dataStore.data.first()[trackingEnabledKey] ?: false

    // Versione precedente (v1.0.0 — 2026-09-20), sostituita il 2026-09-29:
    // suspend fun clear() { context.dataStore.edit { it.remove(teamIdKey) } }
    suspend fun clear() {
        context.dataStore.edit {
            it.remove(teamIdKey)
            it.remove(trackingEnabledKey)
        }
    }
}
