// TeamSelectionScreen.kt — v1.0.0 — 2026-09-20 00:30 UTC
package com.plumberdiary.app.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * Requisito 11: prima squadra dopo il login — crearne una nuova (diventa
 * `ownerUid`) o unirsi a una esistente con un codice di invito (generato da
 * un membro tramite backend/api/create-invite.js). Entrambe le azioni
 * passano da backend/api/create-team.js / accept-invite.js, mai da una
 * scrittura diretta Firestore (vedi firestore.rules).
 *
 * `backendBaseUrl` va sostituito con l'URL reale del deploy Vercel una volta
 * disponibile — vedi app/README.md.
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
    var joinTeamId by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    suspend fun onTeamResolved(teamId: String) {
        val uid = requireNotNull(com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid)
        sessionStore.saveTeamId(teamId)
        CurrentSession.set(uid, teamId)
        onTeamReady(teamId)
    }

    Column(modifier = Modifier.padding(24.dp)) {
        Text("Crea una nuova squadra")
        OutlinedTextField(
            value = teamName,
            onValueChange = { teamName = it },
            label = { Text("Nome squadra (es. Idraulici Rossi & Figli)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                scope.launch {
                    try {
                        val teamId = backendClient.createTeam(teamName)
                        onTeamResolved(teamId)
                    } catch (e: Exception) {
                        errorMessage = e.message
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Crea squadra") }

        Spacer(modifier = Modifier.height(32.dp))

        Text("Oppure unisciti a una squadra esistente")
        OutlinedTextField(
            value = joinTeamId,
            onValueChange = { joinTeamId = it },
            label = { Text("ID squadra") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = inviteCode,
            onValueChange = { inviteCode = it },
            label = { Text("Codice invito") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                scope.launch {
                    try {
                        backendClient.acceptInvite(joinTeamId, inviteCode)
                        onTeamResolved(joinTeamId)
                    } catch (e: Exception) {
                        errorMessage = e.message
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Unisciti") }

        errorMessage?.let { Text(it) }
    }
}
