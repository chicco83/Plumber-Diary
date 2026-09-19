// OpzioniScreen.kt — v1.0.0 — 2026-09-20 00:10 UTC
package com.plumberdiary.app.ui.opzioni

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.plumberdiary.app.ui.Routes
import com.plumberdiary.app.ui.common.PlumberScaffold

/**
 * Mockup "Opzioni.dc.html": tutte le [com.plumberdiary.app.data.model.UserSettings]
 * (orario recap, soglia, notifica in tempo reale, email recap, sede/pause,
 * squadra, retention, ecc.), lette/scritte tramite
 * [com.plumberdiary.app.data.repository.SettingsRepository].
 */
@Composable
fun OpzioniScreen(navController: NavHostController) {
    PlumberScaffold(navController = navController, currentRoute = Routes.OPZIONI) { padding ->
        Column(modifier = Modifier.padding(padding).padding(20.dp)) {
            Text("Opzioni")
            // TODO: sezioni Recap serale / Sede e pause / Integrazioni / Squadra /
            // Cronologia e statistiche / Privacy, vedi mockup Opzioni.dc.html.
        }
    }
}
