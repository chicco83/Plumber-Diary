// SquadraScreen.kt — v1.0.0 — 2026-09-20 00:10 UTC
package com.plumberdiary.app.ui.squadra

import android.widget.FrameLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.plumberdiary.app.ui.Routes
import com.plumberdiary.app.ui.common.PlumberScaffold
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

/**
 * Mockup "Squadra.dc.html": mappa condivisa con i colleghi (osmdroid, niente
 * Google Maps SDK — vedi context.md), alimentata da
 * [com.plumberdiary.app.data.repository.TeamRepository.listenMembers]. Ogni
 * marker rispetta le opzioni di visibilità del collega mostrato, non le
 * proprie: un membro con "vedi posizione della squadra" spento semplicemente
 * non scrive mai liveLocation, quindi non compare a nessuno.
 */
@Composable
fun SquadraScreen(navController: NavHostController) {
    PlumberScaffold(navController = navController, currentRoute = Routes.SQUADRA) { padding ->
        Column(modifier = Modifier.padding(padding).padding(20.dp)) {
            Text("Squadra oggi")
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(280.dp),
                factory = { context ->
                    MapView(context).apply {
                        setMultiTouchControls(true)
                        controller.setZoom(14.0)
                        controller.setCenter(GeoPoint(44.4949, 11.3426)) // Bologna, da sostituire con la sede impostata
                    }
                },
            )
            // TODO: marker per membro da TeamRepository.listenMembers(), elenco
            // membri sotto la mappa (vedi mockup Squadra.dc.html).
        }
    }
}
