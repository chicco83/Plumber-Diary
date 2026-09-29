// PlumberDiaryNavHost.kt — v1.11.0 — 2026-09-29 (v1.8.0 — 2026-09-29; v1.7.0 — 2026-09-24: BackendConfig; v1.1.0 — 2026-09-20 00:30 UTC)
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
import com.plumberdiary.app.data.BackendConfig
import com.plumberdiary.app.data.model.UserSettings
import com.plumberdiary.app.data.repository.SettingsRepository
import com.plumberdiary.app.recap.RecapScheduler
import com.plumberdiary.app.session.CurrentSession
import com.plumberdiary.app.session.SessionStore
import com.plumberdiary.app.ui.auth.LoginScreen
import com.plumberdiary.app.ui.auth.TeamSelectionScreen
import com.plumberdiary.app.ui.cliente.ClienteScreen
import com.plumberdiary.app.ui.cliente.ClientiScreen
import com.plumberdiary.app.ui.dashboard.DashboardScreen
import com.plumberdiary.app.ui.dettaglio.DettaglioScreen
import com.plumberdiary.app.ui.home.HomeScreen
import com.plumberdiary.app.ui.notifica.NotificaConfermaScreen
import com.plumberdiary.app.ui.opzioni.OpzioniScreen
import com.plumberdiary.app.ui.mandatino.MandatinoScreen
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
    const val CLIENTI = "clienti" // v1.8.0 — 2026-09-29: elenco anagrafica (prima la scheda Cliente era irraggiungibile)
    const val CLIENTE = "cliente/{clientId}"
    const val SQUADRA = "squadra"
    const val OPZIONI = "opzioni"
    const val STORICO = "storico"
    const val DASHBOARD = "dashboard"
    // v1.11.0 — 2026-09-29: il rapportino è lo stesso documento del mandatino.
    // Prima: const val RAPPORTINO = "rapportino/{stopId}"
    const val MANDATINO = "mandatino/{clientId}"
    const val NOTIFICA_CONFERMA = "notifica_conferma/{clientId}"

    fun dettaglio(stopId: String) = "dettaglio/$stopId"
    fun cliente(clientId: String) = "cliente/$clientId"
    fun mandatino(clientId: String) = "mandatino/$clientId"
    fun notificaConferma(clientId: String) = "notifica_conferma/$clientId"
}

// v1.7.0 — 2026-09-24: l'URL del deploy Vercel vive ora in
// com.plumberdiary.app.data.BackendConfig (prima era duplicato anche nelle
// schermate Recap/Cliente/Squadra/Opzioni): un unico punto da aggiornare,
// vedi SETUP.md passo 4.

// Versione precedente (v1.1.0 — 2026-09-20 00:30 UTC), sostituita il 2026-09-29:
// 1) leggeva il teamId da SessionStore ma NON impostava CurrentSession, che
//    veniva ripristinata in parallelo e in modo asincrono da PlumberDiaryApp:
//    al primo avvio le schermate potevano trovarla vuota (rememberActiveSession
//    la legge una sola volta) e restare senza dati;
// 2) la notifica di conferma era gestita solo come destinazione iniziale, non
//    ad app già aperta.
//
// @Composable
// fun PlumberDiaryNavHost(startOnRealtimeConfirm: Boolean = false, realtimeConfirmClientId: String? = null, ...) {
//     LaunchedEffect(Unit) {
//         val user = FirebaseAuth.getInstance().currentUser
//         startDestination = when {
//             user == null -> Routes.LOGIN
//             sessionStore.readTeamId() == null -> Routes.TEAM_SELECTION
//             startOnRealtimeConfirm && realtimeConfirmClientId != null -> "notifica_conferma/$realtimeConfirmClientId"
//             else -> Routes.HOME
//         }
//     }
//     ...
// }

@Composable
fun PlumberDiaryNavHost(
    pendingRoute: String? = null,
    onPendingRouteHandled: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
) {
    val context = LocalContext.current
    val sessionStore = remember { SessionStore(context) }

    // Destinazione iniziale: utente autenticato? squadra già scelta? La
    // sessione viene impostata QUI, prima di montare qualunque schermata.
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val user = FirebaseAuth.getInstance().currentUser
        val teamId = if (user != null) sessionStore.readTeamId() else null
        if (user != null && teamId != null) CurrentSession.set(user.uid, teamId)
        startDestination = when {
            user == null -> Routes.LOGIN
            teamId == null -> Routes.TEAM_SELECTION
            else -> Routes.HOME
        }

        // Programma il recap serale (requisito 3) se non lo è già: dopo aver
        // mostrato la Home, per non ritardarla con una lettura Firestore.
        if (user != null && teamId != null) {
            val settings = runCatching { SettingsRepository().get(teamId, user.uid) }.getOrNull() ?: UserSettings()
            RecapScheduler.schedule(context, settings.recapTimeHour, settings.recapTimeMinute, reschedule = false)
        }
    }

    val resolvedStart = startDestination ?: return // breve attesa: evita di montare la NavHost due volte

    // Navigazione richiesta da una notifica (conferma cliente o recap pronto),
    // anche ad app già aperta. Solo con sessione attiva: altrimenti resta
    // sulla schermata di login/squadra.
    LaunchedEffect(pendingRoute, resolvedStart) {
        val route = pendingRoute ?: return@LaunchedEffect
        if (CurrentSession.current() == null) return@LaunchedEffect
        navController.navigate(route) { launchSingleTop = true }
        onPendingRouteHandled()
    }

    NavHost(navController = navController, startDestination = resolvedStart) {
        composable(Routes.LOGIN) {
            LoginScreen(onSignedIn = { navController.navigate(Routes.TEAM_SELECTION) { popUpTo(Routes.LOGIN) { inclusive = true } } })
        }
        composable(Routes.TEAM_SELECTION) {
            TeamSelectionScreen(
                backendBaseUrl = BackendConfig.BASE_URL,
                onTeamReady = {
                    // Nuova squadra/utente: opzioni ancora ai valori predefiniti.
                    val defaults = UserSettings()
                    RecapScheduler.schedule(context, defaults.recapTimeHour, defaults.recapTimeMinute, reschedule = false)
                    navController.navigate(Routes.HOME) { popUpTo(Routes.TEAM_SELECTION) { inclusive = true } }
                },
            )
        }
        composable(Routes.HOME) { HomeScreen(navController) }
        composable(Routes.RECAP) { RecapScreen(navController) }
        composable(Routes.DETTAGLIO) { backStackEntry ->
            DettaglioScreen(navController, stopId = backStackEntry.arguments?.getString("stopId").orEmpty())
        }
        composable(Routes.CLIENTI) { ClientiScreen(navController) }
        composable(Routes.CLIENTE) { backStackEntry ->
            ClienteScreen(navController, clientId = backStackEntry.arguments?.getString("clientId").orEmpty())
        }
        composable(Routes.SQUADRA) { SquadraScreen(navController) }
        composable(Routes.OPZIONI) { OpzioniScreen(navController) }
        composable(Routes.STORICO) { StoricoScreen(navController) }
        composable(Routes.DASHBOARD) { DashboardScreen(navController) }
        composable(Routes.MANDATINO) { backStackEntry ->
            MandatinoScreen(navController, clientId = backStackEntry.arguments?.getString("clientId").orEmpty())
        }
        composable(Routes.NOTIFICA_CONFERMA) { backStackEntry ->
            NotificaConfermaScreen(navController, clientId = backStackEntry.arguments?.getString("clientId").orEmpty())
        }
    }
}
