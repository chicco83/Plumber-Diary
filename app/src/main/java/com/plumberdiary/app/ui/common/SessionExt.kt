// SessionExt.kt — v1.6.0 — 2026-09-23
//
// Helper per le schermate: restituisce la sessione attiva o null (invece di
// lanciare) così ogni UI può mostrare un messaggio chiaro se l'utente arriva
// a una schermata senza login/squadra (es. deep link da notifica).
package com.plumberdiary.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.plumberdiary.app.session.CurrentSession

/** Sessione attiva letta una volta al compose, o null se non c'è. */
@Composable
fun rememberActiveSession(): CurrentSession.Active? = remember {
    try {
        CurrentSession.requireActive()
    } catch (e: IllegalStateException) {
        null
    }
}
