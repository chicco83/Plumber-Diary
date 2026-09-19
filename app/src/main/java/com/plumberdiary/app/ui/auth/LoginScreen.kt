// LoginScreen.kt — v1.0.0 — 2026-09-20 00:30 UTC
package com.plumberdiary.app.ui.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.plumberdiary.app.auth.AuthRepository
import kotlinx.coroutines.launch

/**
 * Primo schermo se non c'è un utente Firebase autenticato (vedi
 * PlumberDiaryNavHost). Dopo un login riuscito il chiamante decide se
 * instradare verso la selezione/creazione squadra (nessun teamId salvato)
 * o direttamente alla Home (teamId già noto, ripristinato da SessionStore).
 */
@Composable
fun LoginScreen(onSignedIn: () -> Unit) {
    val context = LocalContext.current
    val authRepository = remember { AuthRepository(context) }
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { activityResult ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(activityResult.data)
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                authRepository.handleSignInResult(task)
                onSignedIn()
            } catch (e: Exception) {
                errorMessage = "Accesso non riuscito: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Plumber Diary")
        Text("Accedi con il tuo account Google per continuare")

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
        } else {
            Button(
                onClick = { launcher.launch(authRepository.signInIntent()) },
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text("Accedi con Google")
            }
        }

        errorMessage?.let { Text(it) }
    }
}
