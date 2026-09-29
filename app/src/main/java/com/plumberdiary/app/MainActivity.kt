// MainActivity.kt — v1.12.0 — 2026-09-29 (v1.8.0 — 2026-09-29; v1.0.0 — 2026-09-20 00:10 UTC)
package com.plumberdiary.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.plumberdiary.app.notification.RealtimeConfirmNotifier
import com.plumberdiary.app.notification.RecapNotifier
import com.plumberdiary.app.ui.PlumberDiaryNavHost
import com.plumberdiary.app.ui.Routes
import com.plumberdiary.app.ui.theme.PlumberDiaryTheme

// Versione precedente (v1.0.0 — 2026-09-20), sostituita il 2026-09-29: leggeva
// l'intent della notifica solo in onCreate. Con launchMode="singleTop" e l'app
// già aperta, il tap sulla notifica "Sei da…?" arriva invece in onNewIntent,
// che non era gestito: non succedeva nulla.
//
// val openOnConfirm = intent?.action == RealtimeConfirmNotifier.ACTION_CONFIRM_CLIENT
// val clientId = intent?.getStringExtra(RealtimeConfirmNotifier.EXTRA_CLIENT_ID)
// setContent { ... PlumberDiaryNavHost(startOnRealtimeConfirm = openOnConfirm, realtimeConfirmClientId = clientId) }

class MainActivity : ComponentActivity() {

    // Schermata da aprire perché richiesta da una notifica — conferma cliente
    // in tempo reale (requisito 14) o recap serale pronto (requisito 3) — sia
    // ad app chiusa (onCreate) sia ad app già aperta (onNewIntent). La NavHost
    // la consuma navigandoci (solo con sessione attiva) e poi la azzera.
    private val pendingRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingRoute.value = routeFrom(intent)

        setContent {
            PlumberDiaryTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PlumberDiaryNavHost(
                        pendingRoute = pendingRoute.value,
                        onPendingRouteHandled = { pendingRoute.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeFrom(intent)?.let { pendingRoute.value = it }
    }

    // Versione precedente (v1.8.0 — 2026-09-29), sostituita il 2026-09-29: senza
    // l'id della sosta la conferma andava sulla sosta aperta al momento del tocco.
    // RealtimeConfirmNotifier.ACTION_CONFIRM_CLIENT ->
    //     intent.getStringExtra(RealtimeConfirmNotifier.EXTRA_CLIENT_ID)?.let { Routes.notificaConferma(it) }
    // v1.12.0 — 2026-09-29: intent reso non nullo in modo esplicito (lo smart cast
    // da `intent?.action` a `intent` non è garantito dal compilatore Kotlin 1.9).
    private fun routeFrom(intent: Intent?): String? {
        val i = intent ?: return null
        return when (i.action) {
            RealtimeConfirmNotifier.ACTION_CONFIRM_CLIENT ->
                i.getStringExtra(RealtimeConfirmNotifier.EXTRA_CLIENT_ID)?.let {
                    Routes.notificaConferma(it, i.getStringExtra(RealtimeConfirmNotifier.EXTRA_STOP_ID))
                }
            RecapNotifier.ACTION_OPEN_RECAP -> Routes.RECAP
            else -> null
        }
    }
}
