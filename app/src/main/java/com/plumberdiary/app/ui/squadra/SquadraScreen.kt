// SquadraScreen.kt — v1.6.0 — 2026-09-23
//
// Versione precedente (v1.0.0 — 2026-09-20 00:10 UTC): stub con mappa fissa su
// Bologna e nessun marker, sostituita il 2026-09-23 dall'implementazione reale
// (requisito 11): mappa osmdroid centrata sulla sede/posizione propria, marker
// colorati per membro da TeamRepository.listenMembers() in tempo reale, elenco
// dei membri con stato e generazione codice invito (endpoint create-invite).
package com.plumberdiary.app.ui.squadra

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.plumberdiary.app.auth.AuthRepository
import com.plumberdiary.app.data.BackendClient
import com.plumberdiary.app.data.BackendConfig
import com.plumberdiary.app.data.model.MemberStatus
import com.plumberdiary.app.data.model.TeamMember
import com.plumberdiary.app.data.repository.SettingsRepository
import com.plumberdiary.app.data.repository.TeamRepository
import com.plumberdiary.app.ui.Routes
import com.plumberdiary.app.ui.common.Format
import com.plumberdiary.app.ui.common.PlumberScaffold
import com.plumberdiary.app.ui.common.rememberActiveSession
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.osmdroid.markers.Marker
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

@Composable
fun SquadraScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = rememberActiveSession()

    var members by remember { mutableStateOf<List<TeamMember>>(emptyList()) }
    var inviteCode by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val teamRepository = remember { TeamRepository() }
    val backendClient = remember { BackendClient(BackendConfig.BASE_URL, AuthRepository(context)) }

    // Centro mappa: sede impostata nelle Opzioni, altrimenti posizione propria,
    // altrimenti coordinate generiche d'Italia.
    var centerLat by remember { mutableStateOf(43.7696) }
    var centerLon by remember { mutableStateOf(11.2558) }

    LaunchedEffect(Unit) {
        if (session == null) return@LaunchedEffect
        try {
            val settings = SettingsRepository().get(session.teamId, session.uid)
            val ownDoc = FirebaseFirestore.getInstance()
                .document("teams/${session.teamId}/members/${session.uid}").get().await()
            val ownLive = try { ownDoc.toObject<TeamMember>()?.liveLocation } catch (_: Exception) { null }
            if (settings.depotLat != null && settings.depotLon != null) {
                centerLat = settings.depotLat; centerLon = settings.depotLon
            } else if (ownLive != null) {
                centerLat = ownLive.lat; centerLon = ownLive.lon
            }
        } catch (_: Exception) { /* centro generico di default */ }
    }

    // Ascolto in tempo reale dei membri (requisito 11).
    LaunchedEffect(Unit) {
        if (session == null) return@LaunchedEffect
        val listener = teamRepository.listenMembers(session.teamId) { list -> members = list }
        onDispose { listener.remove() }
    }

    PlumberScaffold(navController = navController, currentRoute = Routes.SQUADRA) { padding ->
        Column(modifier = Modifier.padding(padding).padding(20.dp)) {
            Text("Squadra oggi")

            AndroidView(
                modifier = Modifier.fillMaxWidth().height(280.dp),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setMultiTouchControls(true)
                        controller.setZoom(14.0)
                        controller.setCenter(GeoPoint(centerLat, centerLon))
                        onResume()
                    }
                },
                update = { mapView ->
                    // Ricalcola i marker ad ogni aggiornamento dei membri.
                    mapView.overlays.removeAll { it is Marker }
                    for (m in members) {
                        val live = m.liveLocation ?: continue
                        if (live.status == MemberStatus.OFFLINE) continue
                        val marker = Marker(mapView).apply {
                            setPosition(GeoPoint(live.lat, live.lon))
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            setIcon(coloredCircleBitmap(m.colorHex))
                            // Il dettaglio (nome/stato) è nell'elenco sotto la mappa:
                            // nessun InfoWindow custom per non dipendere da API
                            // osmdroid soggette a variazioni di versione.
                        }
                        mapView.overlays.add(marker)
                    }
                    mapView.invalidate()
                },
            )

            members.sortedBy { it.displayName.lowercase() }.forEach { m ->
                val live = m.liveLocation
                ElevatedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(14.dp).clip(CircleShape)
                                .background(Color(androidColorSafe(m.colorHex))),
                        )
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(m.displayName.ifBlank { m.email.ifBlank { "Membro" } })
                            Text(
                                when {
                                    live == null -> "Posizione non condivisa"
                                    live.status == MemberStatus.PAUSED -> "In pausa — agg. ${Format.time(live.updatedAt)}"
                                    else -> "Attivo — agg. ${Format.time(live.updatedAt)}"
                                },
                            )
                        }
                    }
                }
            }

            if (members.isEmpty()) Text("Nessun membro visibile al momento.")

            ElevatedCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Invita un collega", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    Button(
                        onClick = {
                            if (session == null) return@Button
                            scope.launch {
                                errorMessage = null
                                try { inviteCode = backendClient.createInvite(session.teamId) }
                                catch (e: Exception) { errorMessage = e.message }
                            }
                        },
                    ) { Text("Genera codice invito") }
                    inviteCode?.let { code ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Codice: $code (valido 7 giorni)", modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = {
                                val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("Invito squadra", code))
                            }) { Text("Copia") }
                        }
                    }
                    errorMessage?.let { Text(it, color = Color.Red) }
                }
            }
        }
    }
}

/** Cerchietto colorato (icona marker) con punto bianco centrale. */
private fun coloredCircleBitmap(colorHex: String): Bitmap {
    val b = Bitmap.createBitmap(36, 36, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(b)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    // v1.6.0 — 2026-09-24: fix compilazione — androidColorSafe() restituisce un
    // Color Compose (senza toArgb()); qui serve il colore Android per il Paint.
    paint.color = try {
        AndroidColor.parseColor(if (colorHex.startsWith("#")) colorHex else "#$colorHex")
    } catch (_: Exception) { 0xFF0F766E.toInt() }
    canvas.drawCircle(18f, 18f, 15f, paint)
    paint.color = AndroidColor.WHITE
    canvas.drawCircle(18f, 18f, 5f, paint)
    return b
}

/** Converte un hex colore in Color Compose, con fallback teal. */
private fun androidColorSafe(hex: String): Color {
    val parsed = try { AndroidColor.parseColor(if (hex.startsWith("#")) hex else "#$hex") } catch (_: Exception) { 0xFF0F766E.toInt() }
    return Color(parsed)
}
