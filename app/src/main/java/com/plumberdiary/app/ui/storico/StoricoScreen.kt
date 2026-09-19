// StoricoScreen.kt — v1.0.0 — 2026-09-20 00:10 UTC
package com.plumberdiary.app.ui.storico

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

/**
 * Mockup "Storico.dc.html": recap dei giorni precedenti, con correzione
 * massiva (selezione di più giorni + riassegnazione cliente in blocco).
 */
@Composable
fun StoricoScreen(navController: NavHostController) {
    Column(modifier = Modifier.padding(20.dp)) {
        Text("Recap giorni precedenti")
        // TODO: elenco giorni + selezione multipla per correzione massiva,
        // vedi mockup Storico.dc.html.
    }
}
