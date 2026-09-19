// DettaglioScreen.kt — v1.0.0 — 2026-09-20 00:10 UTC
package com.plumberdiary.app.ui.dettaglio

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

/**
 * Mockup "Dettaglio.dc.html": correzione orari, scelta/creazione cliente,
 * note, promemoria Google Calendar, foto intervento, articoli usati. Legge
 * e scrive una singola [com.plumberdiary.app.data.model.Stop] tramite
 * [com.plumberdiary.app.data.repository.StopRepository].
 */
@Composable
fun DettaglioScreen(navController: NavHostController, stopId: String) {
    Column(modifier = Modifier.padding(20.dp)) {
        Text("Dettaglio posizione ($stopId)")
        // TODO: form orari/cliente/note/foto/articoli/promemoria, vedi
        // mockup Dettaglio.dc.html.
    }
}
