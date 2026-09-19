// MainActivity.kt — v1.0.0 — 2026-09-20 00:10 UTC
package com.plumberdiary.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.plumberdiary.app.notification.RealtimeConfirmNotifier
import com.plumberdiary.app.ui.PlumberDiaryNavHost
import com.plumberdiary.app.ui.theme.PlumberDiaryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Se l'activity è stata aperta dall'azione rapida della notifica di
        // conferma in tempo reale (requisito 14), la nav host la instrada
        // subito sulla schermata "Notifica" con il cliente pre-selezionato.
        val openOnConfirm = intent?.action == RealtimeConfirmNotifier.ACTION_CONFIRM_CLIENT
        val clientId = intent?.getStringExtra(RealtimeConfirmNotifier.EXTRA_CLIENT_ID)

        setContent {
            PlumberDiaryTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PlumberDiaryNavHost(
                        startOnRealtimeConfirm = openOnConfirm,
                        realtimeConfirmClientId = clientId,
                    )
                }
            }
        }
    }
}
