// SquadraScreen.kt — v1.12.0 — 2026-09-29 (v1.8.0 — 2026-09-29; v1.7.0 — 2026-09-23)
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
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.launch
// v1.8.0 — 2026-09-29: il package era sbagliato (org.osmdroid.markers.Marker
// non esiste), errore di compilazione.
import org.osmdroid.views.overlay.Marker
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
    var centerLat by remember { mutableStateOf(DEFAULT_LAT) }
    var centerLon by remember { mutableStateOf(DEFAULT_LON) }

    LaunchedEffect(Unit) {
        if (session == null) return@LaunchedEffect
        try {
            val settings = SettingsRepository().get(session.teamId, session.uid)
            // v1.8.0 — 2026-09-29: prima accesso Firestore diretto con
            // toObject<TeamMember>() senza import (non compilava); ora dal repository.
            // val ownDoc = FirebaseFirestore.getInstance()
            //     .document("teams/${session.teamId}/members/${session.uid}").get().await()
            // val ownLive = try { ownDoc.toObject<TeamMember>()?.liveLocation } catch (_: Exception) { null }
            val ownLive = teamRepository.getMember(session.teamId, session.uid)?.liveLocation
            if (settings.depotLat != null && settings.depotLon != null) {
                centerLat = settings.depotLat; centerLon = settings.depotLon
            } else if (ownLive != null) {
                centerLat = ownLive.lat; centerLon = ownLive.lon
            }
        } catch (_: Exception) { /* centro generico di default */ }
    }

    // Ascolto in tempo reale dei membri (requisito 11).
    // v1.8.0 — 2026-09-29: prima era un LaunchedEffect con onDispose (non
    // compila: onDispose esiste solo in DisposableEffect) e il listener non
    // veniva mai rimosso uscendo dalla schermata.
    // LaunchedEffect(Unit) {
    //     if (session == null) return@LaunchedEffect
    //     val listener = teamRepository.listenMembers(session.teamId) { list -> members = list }
    //     onDispose { listener.remove() }
    // }
    DisposableEffect(session) {
        val listener = session?.let { s -> teamRepository.listenMembers(s.teamId) { list -> members = list } }
        onDispose { listener?.remove() }
    }

    // v1.8.0 — 2026-09-29: la MapView va messa in pausa/staccata quando si
    // lascia la schermata (prima restava attiva: tile e thread di osmdroid in
    // memoria), e ricentrata quando arriva la posizione della sede.
    val mapHolder = remember { MapHolder() }
    DisposableEffect(Unit) {
        onDispose {
            mapHolder.view?.onPause()
            mapHolder.view?.onDetach()
        }
    }

    PlumberScaffold(navController = navController, currentRoute = Routes.SQUADRA) { padding ->
        Column(modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("Squadra oggi")

            AndroidView(
                modifier = Modifier.fillMaxWidth().height(280.dp),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setMultiTouchControls(true)
                        controller.setZoom(14.0)
                        controller.setCenter(GeoPoint(centerLat, centerLon))
                        onResume()
                        mapHolder.view = this
                    }
                },
                update = { mapView ->
                    if (!mapHolder.centeredOnData && (centerLat != DEFAULT_LAT || centerLon != DEFAULT_LON)) {
                        mapView.controller.setCenter(GeoPoint(centerLat, centerLon))
                        mapHolder.centeredOnData = true
                    }
                    // Ricalcola i marker ad ogni aggiornamento dei membri.
                    mapView.overlays.removeAll { it is Marker }
                    for (m in members) {
                        val live = m.liveLocation ?: continue
                        if (live.status == MemberStatus.OFFLINE) continue
                        val marker = Marker(mapView).apply {
                            setPosition(GeoPoint(live.lat, live.lon))
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            // v1.8.0 — 2026-09-29: setIcon vuole un Drawable, non un
                            // Bitmap (prima: setIcon(coloredCircleBitmap(...)), non compilava).
                            icon = BitmapDrawable(mapView.context.resources, coloredCircleBitmap(m.colorHex))
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
                                // v1.8.0 — 2026-09-29: era Color(androidColorSafe(...)),
                                // un Color passato al costruttore Color: non compilava.
                                .background(androidColorSafe(m.colorHex)),
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
                            // v1.12.0 — 2026-09-29: il backend restituisce ora il codice completo
                            // "squadra.codice", l'unico dato che serve al collega per unirsi.
                            // Prima: Text("Codice: $code (valido 7 giorni)", ...)
                            Text("Codice: $code (valido 7 giorni, da incollare in \"Unisciti\")", modifier = Modifier.weight(1f))
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

/** Riferimento alla MapView per il rilascio in onDispose (non è stato della UI). */
private class MapHolder {
    var view: MapView? = null
    var centeredOnData = false
}

private const val DEFAULT_LAT = 43.7696
private const val DEFAULT_LON = 11.2558

/** Cerchietto colorato (icona marker) con punto bianco centrale. */
private fun coloredCircleBitmap(colorHex: String): Bitmap {
    val b = Bitmap.createBitmap(36, 36, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(b)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    // v1.7.0 — 2026-09-24: fix compilazione — androidColorSafe() restituisce un
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
