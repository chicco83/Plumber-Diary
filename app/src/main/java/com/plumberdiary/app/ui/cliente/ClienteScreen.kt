// ClienteScreen.kt — v1.0.0 — 2026-09-20 00:10 UTC
package com.plumberdiary.app.ui.cliente

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

/**
 * Mockup "Cliente.dc.html": anagrafica (condivisa a livello squadra, vedi
 * [com.plumberdiary.app.data.repository.ClientRepository]), galleria foto
 * (Photo Picker + [com.plumberdiary.app.photo.PhotoUploader]), email per il
 * mandatino ore, pulsante "Genera e invia PDF" (vedi mockup ConfermaPDF),
 * listino articoli con quantità.
 */
@Composable
fun ClienteScreen(navController: NavHostController, clientId: String) {
    Column(modifier = Modifier.padding(20.dp)) {
        Text("Scheda cliente ($clientId)")
        // TODO: form anagrafica + galleria foto + listino articoli + azione
        // "Genera e invia PDF" (mandatino ore), vedi mockup Cliente.dc.html
        // e ConfermaPDF.dc.html.
    }
}
