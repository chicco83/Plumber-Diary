// SessionStore.kt — v1.0.0 — 2026-09-20 00:30 UTC
package com.plumberdiary.app.session

import android.content.Context
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
 */
class SessionStore(private val context: Context) {
    private val teamIdKey = stringPreferencesKey("team_id")

    suspend fun saveTeamId(teamId: String) {
        context.dataStore.edit { it[teamIdKey] = teamId }
    }

    suspend fun readTeamId(): String? = context.dataStore.data.first()[teamIdKey]

    suspend fun clear() {
        context.dataStore.edit { it.remove(teamIdKey) }
    }
}
