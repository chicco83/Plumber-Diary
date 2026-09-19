// RecapScreen.kt — v1.0.0 — 2026-09-20 00:10 UTC
package com.plumberdiary.app.ui.recap

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
 * Mockup "Recap.dc.html": riepilogo serale, una card per sosta con cliente
 * suggerito/da scegliere, note, articoli, foto, km dalla sosta precedente e
 * pulsante "Genera rapportino con firma". Alimentata da un RecapViewModel su
 * [com.plumberdiary.app.data.repository.StopRepository.getStopsForDay] +
 * [com.plumberdiary.app.recap.DailyDistanceCalculator].
 */
@Composable
fun RecapScreen(navController: NavHostController) {
    PlumberScaffold(navController = navController, currentRoute = Routes.RECAP) { padding ->
        Column(modifier = Modifier.padding(padding).padding(20.dp)) {
            Text("Riepilogo giornata")
            // TODO: lista soste del giorno (vedi mockup Recap.dc.html), ognuna
            // con link a Routes.dettaglio(stopId) e Routes.rapportino(stopId).
        }
    }
}
