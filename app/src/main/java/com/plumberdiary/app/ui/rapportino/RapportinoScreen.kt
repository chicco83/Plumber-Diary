// RapportinoScreen.kt — v1.0.0 — 2026-09-20 00:10 UTC
package com.plumberdiary.app.ui.rapportino

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

/**
 * Mockup "Rapportino.dc.html": firma cliente su schermo, generata con
 * [com.plumberdiary.app.pdf.RapportinoPdfGenerator]. Il pulsante "Salta" è
 * SEMPRE presente e non bloccante (requisito 15, vincolo esplicito
 * dell'utente): passa signatureBitmap = null al generatore invece di
 * impedire l'invio del rapportino.
 */
@Composable
fun RapportinoScreen(navController: NavHostController, stopId: String) {
    Column(modifier = Modifier.padding(20.dp)) {
        Text("Rapportino intervento ($stopId)")
        // TODO: area di firma (Canvas di disegno) + note/materiali riepilogati,
        // vedi mockup Rapportino.dc.html.
        Button(onClick = { /* TODO: generate con signatureBitmap = null */ }) {
            Text("Salta la firma — invia comunque il rapportino")
        }
    }
}
