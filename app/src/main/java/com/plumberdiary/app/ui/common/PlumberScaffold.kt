// PlumberScaffold.kt — v1.12.0 — 2026-09-29 (v1.0.0 — 2026-09-20 00:10 UTC)
package com.plumberdiary.app.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.plumberdiary.app.ui.Routes

/**
 * Tab bar comune a Home/Recap/Squadra/Opzioni, come nel mockup (le altre
 * schermate — Dettaglio, Cliente, Storico, Dashboard, Mandatino, Notifica —
 * si aprono sopra e usano una AppBar con la sola freccia indietro, non
 * questa tab bar).
 */
// v1.12.0 — 2026-09-29: ogni tocco su una scheda aggiungeva una schermata allo
// storico di navigazione (navigate(route) semplice): il tasto indietro
// ripercorreva tutte le schede toccate. Ora le schede sono "radici" sopra la
// Home, senza duplicati. Prima: onClick = { navController.navigate(Routes.X) }
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME)
        launchSingleTop = true
    }
}

@Composable
fun PlumberScaffold(
    navController: NavHostController,
    currentRoute: String,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute == Routes.HOME,
                    onClick = { navController.navigateToTab(Routes.HOME) },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("Oggi") },
                )
                NavigationBarItem(
                    selected = currentRoute == Routes.RECAP,
                    onClick = { navController.navigateToTab(Routes.RECAP) },
                    icon = { Icon(Icons.Filled.Summarize, contentDescription = null) },
                    label = { Text("Recap") },
                )
                NavigationBarItem(
                    selected = currentRoute == Routes.SQUADRA,
                    onClick = { navController.navigateToTab(Routes.SQUADRA) },
                    icon = { Icon(Icons.Filled.Groups, contentDescription = null) },
                    label = { Text("Squadra") },
                )
                NavigationBarItem(
                    selected = currentRoute == Routes.OPZIONI,
                    onClick = { navController.navigateToTab(Routes.OPZIONI) },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Opzioni") },
                )
            }
        },
    ) { padding -> content(padding) }
}
