// HomeScreen.kt — v1.0.0 — 2026-09-20 00:10 UTC
package com.plumberdiary.app.ui.home

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
 * Mockup "Home.dc.html": giornata in corso, timeline delle soste di oggi,
 * card della sosta corrente con eventuale invito a segnarla come cliente.
 * Dati reali da un HomeViewModel che osserva [com.plumberdiary.app.data.repository.StopRepository]
 * (in tempo reale, via Firestore snapshot listener) — non incluso in questo
 * scaffolding: qui la struttura di navigazione/layout è pronta, il
 * collegamento ai dati è il prossimo passo di implementazione.
 */
@Composable
fun HomeScreen(navController: NavHostController) {
    PlumberScaffold(navController = navController, currentRoute = Routes.HOME) { padding ->
        Column(modifier = Modifier.padding(padding).padding(20.dp)) {
            Text("Giornata di oggi")
            // TODO: card sosta corrente + timeline (vedi mockup Home.dc.html),
            // alimentate da HomeViewModel -> StopRepository.getStopsForDay().
        }
    }
}
