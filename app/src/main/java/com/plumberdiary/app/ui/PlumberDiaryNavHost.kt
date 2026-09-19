// PlumberDiaryNavHost.kt — v1.0.0 — 2026-09-20 00:10 UTC
package com.plumberdiary.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.plumberdiary.app.ui.cliente.ClienteScreen
import com.plumberdiary.app.ui.dashboard.DashboardScreen
import com.plumberdiary.app.ui.dettaglio.DettaglioScreen
import com.plumberdiary.app.ui.home.HomeScreen
import com.plumberdiary.app.ui.notifica.NotificaConfermaScreen
import com.plumberdiary.app.ui.opzioni.OpzioniScreen
import com.plumberdiary.app.ui.rapportino.RapportinoScreen
import com.plumberdiary.app.ui.recap.RecapScreen
import com.plumberdiary.app.ui.squadra.SquadraScreen
import com.plumberdiary.app.ui.storico.StoricoScreen

/**
 * Un'unica NavHost per tutte le schermate del mockup (Artifact "Plumber
 * Diary — Mockup schermate"): i nomi delle route rispecchiano i nomi degli
 * artboard lì per facilitare il confronto disegno↔implementazione.
 */
object Routes {
    const val HOME = "home"
    const val RECAP = "recap"
    const val DETTAGLIO = "dettaglio/{stopId}"
    const val CLIENTE = "cliente/{clientId}"
    const val SQUADRA = "squadra"
    const val OPZIONI = "opzioni"
    const val STORICO = "storico"
    const val DASHBOARD = "dashboard"
    const val RAPPORTINO = "rapportino/{stopId}"
    const val NOTIFICA_CONFERMA = "notifica_conferma/{clientId}"

    fun dettaglio(stopId: String) = "dettaglio/$stopId"
    fun cliente(clientId: String) = "cliente/$clientId"
    fun rapportino(stopId: String) = "rapportino/$stopId"
}

@Composable
fun PlumberDiaryNavHost(
    startOnRealtimeConfirm: Boolean = false,
    realtimeConfirmClientId: String? = null,
    navController: NavHostController = rememberNavController(),
) {
    val startDestination = if (startOnRealtimeConfirm && realtimeConfirmClientId != null) {
        "notifica_conferma/$realtimeConfirmClientId"
    } else {
        Routes.HOME
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.HOME) { HomeScreen(navController) }
        composable(Routes.RECAP) { RecapScreen(navController) }
        composable(Routes.DETTAGLIO) { backStackEntry ->
            DettaglioScreen(navController, stopId = backStackEntry.arguments?.getString("stopId").orEmpty())
        }
        composable(Routes.CLIENTE) { backStackEntry ->
            ClienteScreen(navController, clientId = backStackEntry.arguments?.getString("clientId").orEmpty())
        }
        composable(Routes.SQUADRA) { SquadraScreen(navController) }
        composable(Routes.OPZIONI) { OpzioniScreen(navController) }
        composable(Routes.STORICO) { StoricoScreen(navController) }
        composable(Routes.DASHBOARD) { DashboardScreen(navController) }
        composable(Routes.RAPPORTINO) { backStackEntry ->
            RapportinoScreen(navController, stopId = backStackEntry.arguments?.getString("stopId").orEmpty())
        }
        composable(Routes.NOTIFICA_CONFERMA) { backStackEntry ->
            NotificaConfermaScreen(navController, clientId = backStackEntry.arguments?.getString("clientId").orEmpty())
        }
    }
}
