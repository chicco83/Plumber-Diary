// AuthRepository.kt — v1.0.0 — 2026-09-20 00:30 UTC
package com.plumberdiary.app.auth

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.plumberdiary.app.R
import kotlinx.coroutines.tasks.await

/**
 * Login Google → Firebase Authentication, in comune fra i membri di una
 * squadra (vedi context.md, "Architettura backend"). Il `default_web_client_id`
 * usato qui viene generato automaticamente in res/values/strings.xml da
 * google-services.json al momento del build: non è hardcoded, perché è
 * specifico del progetto Firebase reale (non ancora creato in questo
 * scaffolding — vedi app/README.md).
 */
class AuthRepository(
    private val context: Context,
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    // v1.6.0 — 2026-09-23: il default_web_client_id è generato dal plugin
    // google-services SOLO se app/google-services.json è presente (vedi
    // SETUP.md). Si risolve a runtime con getIdentifier invece di un
    // riferimento R.string compile-time, così il codice compila anche senza la
    // configurazione Firebase e dà un errore chiaro al primo login se manca.
    private val webClientId: String by lazy {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (resId == 0) {
            error(
                "Manca app/google-services.json: crea il progetto Firebase e scarica il file " +
                    "(vedi SETUP.md, sezione Firebase). Il login Google non può funzionare senza."
            )
        }
        context.getString(resId)
    }

    private val googleSignInClient: GoogleSignInClient by lazy {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, options)
    }

    fun currentUser(): FirebaseUser? = firebaseAuth.currentUser

    /** Intent da lanciare con ActivityResultContracts.StartActivityForResult. */
    fun signInIntent() = googleSignInClient.signInIntent

    /** Da chiamare nel callback dell'ActivityResult con il risultato dell'intent sopra. */
    suspend fun handleSignInResult(task: Task<GoogleSignInAccount>): FirebaseUser {
        val account = task.await()
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        val result = firebaseAuth.signInWithCredential(credential).await()
        return result.user ?: error("Login riuscito ma nessun FirebaseUser restituito")
    }

    suspend fun signOut() {
        firebaseAuth.signOut()
        googleSignInClient.signOut().await()
    }

    /** Token da allegare come header Authorization: Bearer <token> verso il backend. */
    suspend fun getIdToken(forceRefresh: Boolean = false): String {
        val user = firebaseAuth.currentUser ?: error("Nessun utente autenticato")
        return user.getIdToken(forceRefresh).await().token
            ?: error("Impossibile ottenere l'ID token")
    }
}
