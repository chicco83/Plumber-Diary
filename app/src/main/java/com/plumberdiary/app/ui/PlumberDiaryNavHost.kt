// PlumberDiaryNavHost.kt — v1.1.0 — 2026-09-20 00:30 UTC
package com.plumberdiary.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.plumberdiary.app.session.SessionStore
import com.plumberdiary.app.ui.auth.LoginScreen
import com.plumberdiary.app.ui.auth.TeamSelectionScreen
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

// Versione precedente (v1.0.0 — 2026-09-20 00:10 UTC), sostituita il
// 2026-09-20 per aggiungere il flusso di login/selezione squadra davanti
// alle schermate dell'app (prima si entrava direttamente in Home, senza
// autenticazione né squadra):
//
// @Composable
// fun PlumberDiaryNavHost(
//     startOnRealtimeConfirm: Boolean = false,
//     realtimeConfirmClientId: String? = null,
//     navController: NavHostController = rememberNavController(),
// ) {
//     val startDestination = if (startOnRealtimeConfirm && realtimeConfirmClientId != null) {
//         "notifica_conferma/$realtimeConfirmClientId"
//     } else {
//         Routes.HOME
//     }
//     NavHost(navController = navController, startDestination = startDestination) {
//         composable(Routes.HOME) { HomeScreen(navController) }
//         /* ... le altre schermate, invariate, vedi sotto ... */
//     }
// }

/**
 * Un'unica NavHost per tutte le schermate del mockup (Artifact "Plumber
 * Diary — Mockup schermate"): i nomi delle route rispecchiano i nomi degli
 * artboard lì per facilitare il confronto disegno↔implementazione.
 */
object Routes {
    const val LOGIN = "login"
    const val TEAM_SELECTION = "team_selection"
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

/**
 * URL del deploy Vercel del backend (vedi backend/README.md). Centralizzato
 * qui in attesa di uno strato di configurazione vero e proprio (BuildConfig
 * per ambiente dev/prod) — TODO una volta che il progetto Firebase/Vercel
 * reale esiste.
 */
private const val BACKEND_BASE_URL = "https://plumber-diary.vercel.app"

@Composable
fun PlumberDiaryNavHost(
    startOnRealtimeConfirm: Boolean = false,
    realtimeConfirmClientId: String? = null,
    navController: NavHostController = rememberNavController(),
) {
    val context = LocalContext.current
    val sessionStore = remember { SessionStore(context) }

    // Determina la destinazione iniziale in base a: utente autenticato?
    // squadra già scelta in precedenza (persistita in SessionStore)? — solo
    // dopo questi due controlli si applica l'eventuale apertura diretta
    // sulla notifica di conferma in tempo reale (richiede comunque sessione
    // attiva, altrimenti torna al login).
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val user = FirebaseAuth.getInstance().currentUser
        startDestination = when {
            user == null -> Routes.LOGIN
            sessionStore.readTeamId() == null -> Routes.TEAM_SELECTION
            startOnRealtimeConfirm && realtimeConfirmClientId != null ->
                "notifica_conferma/$realtimeConfirmClientId"
            else -> Routes.HOME
        }
    }

    val resolvedStart = startDestination ?: return // breve attesa: evita di montare la NavHost due volte

    NavHost(navController = navController, startDestination = resolvedStart) {
        composable(Routes.LOGIN) {
            LoginScreen(onSignedIn = { navController.navigate(Routes.TEAM_SELECTION) { popUpTo(Routes.LOGIN) { inclusive = true } } })
        }
        composable(Routes.TEAM_SELECTION) {
            TeamSelectionScreen(
                backendBaseUrl = BACKEND_BASE_URL,
                onTeamReady = { navController.navigate(Routes.HOME) { popUpTo(Routes.TEAM_SELECTION) { inclusive = true } } },
            )
        }
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
