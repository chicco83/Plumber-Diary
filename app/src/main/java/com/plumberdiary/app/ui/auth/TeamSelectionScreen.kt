// TeamSelectionScreen.kt — v1.12.0 — 2026-09-29 (v1.0.0 — 2026-09-20 00:30 UTC)
package com.plumberdiary.app.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.plumberdiary.app.auth.AuthRepository
import com.plumberdiary.app.data.BackendClient
import com.plumberdiary.app.session.CurrentSession
import com.plumberdiary.app.session.SessionStore
import kotlinx.coroutines.launch

/**
 * Requisito 11: squadra dopo il login — crearne una nuova (diventa
 * `ownerUid`) o unirsi a una esistente con un codice di invito (generato da
 * un membro tramite backend/api/create-invite.js). Entrambe le azioni
 * passano da backend/api/create-team.js / accept-invite.js, mai da una
 * scrittura diretta Firestore (vedi firestore.rules).
 *
 * v1.12.0 — 2026-09-29:
 *  - un solo campo "Codice invito", nel formato completo "<squadra>.<codice>"
 *    che Squadra/Opzioni mostrano e copiano. Prima servivano ID squadra e
 *    codice separati, ma l'ID squadra non compariva in nessuna schermata:
 *    il collega invitato non poteva entrare;
 *  - elenco "Le tue squadre" (backend my-teams): dopo logout, reinstallazione
 *    o cambio telefono si rientra con un tocco. Prima si doveva creare una
 *    squadra nuova o chiedere un altro invito, anche al titolare.
 *
 * Versione precedente (v1.0.0 — 2026-09-20), sostituita il 2026-09-29 — la
 * sezione "unisciti" era:
 *
 *   OutlinedTextField(value = joinTeamId, onValueChange = { joinTeamId = it }, label = { Text("ID squadra") })
 *   OutlinedTextField(value = inviteCode, onValueChange = { inviteCode = it }, label = { Text("Codice invito") })
 *   Button(onClick = { scope.launch {
 *       backendClient.acceptInvite(joinTeamId, inviteCode); onTeamResolved(joinTeamId)
 *   } }) { Text("Unisciti") }
 */
@Composable
fun TeamSelectionScreen(backendBaseUrl: String, onTeamReady: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backendClient = remember {
        BackendClient(backendBaseUrl, AuthRepository(context))
    }
    val sessionStore = remember { SessionStore(context) }

    var teamName by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var myTeams by remember { mutableStateOf<List<BackendClient.TeamSummary>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    suspend fun onTeamResolved(teamId: String) {
        val uid = requireNotNull(com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid)
        sessionStore.saveTeamId(teamId)
        CurrentSession.set(uid, teamId)
        onTeamReady(teamId)
    }

    /** Esegue un'azione verso il backend mostrando l'errore invece di crashare. */
    fun launchAction(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        errorMessage = null
        scope.launch {
            try { action() } catch (e: Exception) { errorMessage = e.message } finally { busy = false }
        }
    }

    // Squadre di cui si è già membri (errore silenzioso: se il backend non
    // risponde restano disponibili creazione e invito).
    LaunchedEffect(Unit) {
        myTeams = try { backendClient.myTeams() } catch (_: Exception) { emptyList() }
    }

    Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
        if (myTeams.isNotEmpty()) {
            Text("Le tue squadre")
            myTeams.forEach { team ->
                OutlinedButton(
                    onClick = { launchAction { onTeamResolved(team.teamId) } },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Rientra in ${team.name.ifBlank { team.teamId }}") }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        Text("Crea una nuova squadra")
        OutlinedTextField(
            value = teamName,
            onValueChange = { teamName = it },
            label = { Text("Nome squadra (es. Idraulici Rossi & Figli)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { launchAction { onTeamResolved(backendClient.createTeam(teamName.trim())) } },
            enabled = !busy && teamName.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Crea squadra") }

        Spacer(modifier = Modifier.height(32.dp))

        Text("Oppure unisciti a una squadra esistente")
        OutlinedTextField(
            value = inviteCode,
            onValueChange = { inviteCode = it },
            label = { Text("Codice invito (ricevuto da un collega)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { launchAction { onTeamResolved(backendClient.acceptInvite(inviteCode)) } },
            enabled = !busy && inviteCode.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Unisciti") }

        errorMessage?.let { Text(it) }
    }
}
