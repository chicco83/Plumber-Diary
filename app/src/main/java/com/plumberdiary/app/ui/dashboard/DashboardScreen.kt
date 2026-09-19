// DashboardScreen.kt — v1.0.0 — 2026-09-20 00:10 UTC
package com.plumberdiary.app.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

/**
 * Mockup "Dashboard.dc.html": ore totali, km, materiali e interventi del
 * mese, ripartiti per cliente (requisito 17). Calcolata on-demand lato
 * client aggregando [com.plumberdiary.app.data.model.Stop] del periodo
 * (volume contenuto, vedi limiti in context.md — nessun job di
 * aggregazione lato backend necessario in questa fase).
 */
@Composable
fun DashboardScreen(navController: NavHostController) {
    Column(modifier = Modifier.padding(20.dp)) {
        Text("Dashboard mensile")
        // TODO: stat tile ore/km/materiali/interventi + ripartizione per
        // cliente, vedi mockup Dashboard.dc.html.
    }
}
