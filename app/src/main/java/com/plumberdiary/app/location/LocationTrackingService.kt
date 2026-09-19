// LocationTrackingService.kt — v1.0.0 — 2026-09-20 00:10 UTC
package com.plumberdiary.app.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.plumberdiary.app.R
import com.plumberdiary.app.data.model.MemberStatus
import com.plumberdiary.app.data.model.LiveLocation
import com.plumberdiary.app.data.model.Stop
import com.plumberdiary.app.data.model.StopKind
import com.plumberdiary.app.data.repository.ClientRepository
import com.plumberdiary.app.data.repository.SettingsRepository
import com.plumberdiary.app.data.repository.StopRepository
import com.plumberdiary.app.data.repository.TeamRepository
import com.plumberdiary.app.notification.RealtimeConfirmNotifier
import com.plumberdiary.app.session.CurrentSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Foreground service di tracciamento (requisito 1). Sampling ADATTIVO invece
 * di un intervallo fisso ad alta frequenza: intervallo lungo (~5 min) di
 * default, ridotto (~1 min) quando [StopClusterer] rileva che ci si è appena
 * spostati oltre il raggio di cluster — stessa strategia già validata per il
 * consumo batteria nel progetto gemello gwatch-child-tracker.
 *
 * Ad ogni sosta che supera la soglia configurata:
 *  1. viene esclusa se ricade su sede/deposito o in una pausa (requisito 13);
 *  2. altrimenti si cerca un cliente noto ([ClientMatcher]) e, se trovato e la
 *     notifica in tempo reale è attiva, si chiede conferma subito (requisito 14);
 *  3. la sosta viene comunque persistita per il recap serale, confermata o no.
 */
class LocationTrackingService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(serviceJob)

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val clusterer = StopClusterer()

    private val stopRepository = StopRepository()
    private val clientRepository = ClientRepository()
    private val settingsRepository = SettingsRepository()
    private val teamRepository = TeamRepository()

    private var lastStopId: String? = null
    private var currentIntervalMillis = LONG_INTERVAL_MILLIS

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            onNewFix(
                LocationFix(
                    lat = location.latitude,
                    lon = location.longitude,
                    timestamp = location.time,
                    accuracyMeters = location.accuracy,
                )
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        serviceScope.launch { resumeOpenStopIfAny() }
        startLocationUpdates(currentIntervalMillis)
        return START_STICKY
    }

    override fun onDestroy() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun resumeOpenStopIfAny() {
        val session = CurrentSession.requireActive()
        val open = stopRepository.getOpenStop(session.teamId, session.uid) ?: return
        lastStopId = open.id
        clusterer.resume(open)
    }

    private fun onNewFix(fix: LocationFix) {
        serviceScope.launch {
            val session = CurrentSession.requireActive()
            val settings = settingsRepository.get(session.teamId, session.uid)

            val closedStop = clusterer.onFix(fix)
            if (closedStop != null) {
                finalizeStop(session.teamId, session.uid, closedStop, settings)
                adjustSamplingInterval(moving = true)
            } else {
                adjustSamplingInterval(moving = false)
            }

            // Sosta ancora in corso: verifica soglia per la notifica in tempo reale
            // e aggiorna la posizione live per la mappa squadra (requisito 11).
            val open = clusterer.currentOpenStop()
            if (open != null) {
                checkThresholdForRealtimeConfirmation(session.teamId, session.uid, open, settings)
                if (settings.seeTeamLocationEnabled) {
                    teamRepository.updateOwnLiveLocation(
                        session.teamId, session.uid,
                        LiveLocation(lat = open.lat, lon = open.lon, updatedAt = fix.timestamp, status = MemberStatus.ACTIVE),
                    )
                }
            }
        }
    }

    private suspend fun finalizeStop(teamId: String, uid: String, stop: Stop, settings: com.plumberdiary.app.data.model.UserSettings) {
        val previousStopEndedAt = stop.startedAt // per il calcolo km reale servirebbe il punto precedente:
        // in questa versione scaffolding il campo distanceFromPreviousMeters viene
        // completato dal recap (RecapViewModel), che ha visibilità sull'intera
        // sequenza ordinata delle soste del giorno.
        val kind = when {
            DepotAndBreakFilter.isAtDepot(stop.lat, stop.lon, settings) -> StopKind.DEPOT
            DepotAndBreakFilter.isDuringBreak(stop.startedAt, settings.breaks) -> StopKind.BREAK
            else -> StopKind.UNRESOLVED
        }
        stopRepository.upsert(teamId, uid, stop.copy(id = lastStopId ?: "", kind = kind))
        lastStopId = null
    }

    private suspend fun checkThresholdForRealtimeConfirmation(
        teamId: String,
        uid: String,
        openStop: Stop,
        settings: com.plumberdiary.app.data.model.UserSettings,
    ) {
        if (!settings.realtimeConfirmationEnabled) return
        if (DepotAndBreakFilter.isAtDepot(openStop.lat, openStop.lon, settings)) return
        if (DepotAndBreakFilter.isDuringBreak(openStop.startedAt, settings.breaks)) return

        val elapsedMinutes = TimeUnit.MILLISECONDS.toMinutes(openStop.endedAt - openStop.startedAt)
        if (elapsedMinutes < settings.clientDetectionThresholdMinutes) return
        if (openStop.realtimeConfirmedAt != null) return // già notificato per questa sosta

        val clients = clientRepository.getAll(teamId)
        val suggested = ClientMatcher.findSuggestedClient(openStop.lat, openStop.lon, clients) ?: return
        RealtimeConfirmNotifier.notify(this, suggested, openStop)
    }

    private fun adjustSamplingInterval(moving: Boolean) {
        val target = if (moving) SHORT_INTERVAL_MILLIS else LONG_INTERVAL_MILLIS
        if (target == currentIntervalMillis) return
        currentIntervalMillis = target
        startLocationUpdates(target)
    }

    private fun startLocationUpdates(intervalMillis: Long) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationClient.removeLocationUpdates(locationCallback)
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, intervalMillis)
            .setMinUpdateIntervalMillis(intervalMillis / 2)
            .build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, mainLooper)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Tracciamento posizione", NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildForegroundNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Tracciamento posizione attivo")
            .setSmallIcon(R.drawable.ic_tracking)
            .setOngoing(true)
            .build()

    companion object {
        private const val CHANNEL_ID = "location_tracking"
        private const val NOTIFICATION_ID = 1001
        private val LONG_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(5)
        private val SHORT_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(1)
    }
}
