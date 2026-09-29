// LocationTrackingService.kt — v1.8.0 — 2026-09-29 (v1.7.0 — 2026-09-23/24: sosta aperta persistita, classifyKind, realtimeDismissedAt; v1.0.0 — 2026-09-20)
package com.plumberdiary.app.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.plumberdiary.app.R
import com.plumberdiary.app.data.model.LiveLocation
import com.plumberdiary.app.data.model.MemberStatus
import com.plumberdiary.app.data.model.Stop
import com.plumberdiary.app.data.model.StopKind
import com.plumberdiary.app.data.model.UserSettings
import com.plumberdiary.app.data.repository.ClientRepository
import com.plumberdiary.app.data.repository.SettingsRepository
import com.plumberdiary.app.data.repository.StopRepository
import com.plumberdiary.app.data.repository.TeamRepository
import com.plumberdiary.app.notification.RealtimeConfirmNotifier
import com.plumberdiary.app.session.CurrentSession
import com.plumberdiary.app.session.SessionStore
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 *     notifica in tempo reale è attiva, si chiede conferma subito (requisito 14),
 *     UNA SOLA VOLTA per sosta;
 *  3. la sosta viene comunque persistita per il recap serale, confermata o no.
 *
 * v1.8.0 — 2026-09-29, correzioni principali (vedi changelog.md):
 *  - il service scrive SOLO i propri campi (orari/posizione) con merge
 *    ([StopRepository.writeTrackingFields]): prima riscriveva l'intero
 *    documento a ogni fix e cancellava cliente, note, foto, materiali e
 *    conferme inseriti dall'utente;
 *  - soste più brevi di [MIN_STOP_MILLIS] non vengono salvate: sono transiti
 *    (semafori, traffico), prima riempivano recap e timeline;
 *  - i fix sono elaborati uno alla volta (Mutex) e le scritture non attendono
 *    il server: offline prima si accavallavano coroutine parallele che
 *    creavano soste duplicate;
 *  - la notifica in tempo reale parte al massimo una volta per sosta: prima
 *    il controllo leggeva la copia in memoria del clusterer, dove i campi
 *    "confermato"/"non ora" erano sempre vuoti, e ri-notificava a ogni fix
 *    rileggendo ogni volta tutti i clienti (rischio quota Firestore);
 *  - fermando il tracciamento la sosta aperta viene chiusa; una sosta rimasta
 *    aperta da un kill del processo viene chiusa al riavvio se "scaduta";
 *  - nessuna eccezione può più far crashare il processo (prima bastava un
 *    errore di rete o il logout con il service attivo).
 */
class LocationTrackingService : Service() {

    // Versione precedente (v1.0.0 — 2026-09-20), sostituita il 2026-09-29:
    // Job() senza dispatcher esplicito (= Dispatchers.Default, multi-thread) e
    // senza gestore di eccezioni: fix elaborati in parallelo e crash del
    // processo su qualunque eccezione.
    //
    // private val serviceJob = Job()
    // private val serviceScope = CoroutineScope(serviceJob)
    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            Log.e(TAG, "Errore non gestito nel tracciamento (ignorato, il service resta attivo)", e)
        },
    )
    private val fixMutex = Mutex()

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val clusterer = StopClusterer()

    private val stopRepository = StopRepository()
    private val clientRepository = ClientRepository()
    private val settingsRepository = SettingsRepository()
    private val teamRepository = TeamRepository()

    // Sessione risolta all'avvio e tenuta qui: dopo un logout CurrentSession
    // viene svuotata prima che onDestroy venga chiamato, ma la sosta aperta va
    // chiusa comunque sull'utente/squadra corretti.
    @Volatile private var session: CurrentSession.Active? = null

    // Id del documento della sosta aperta; null finché la sosta non ha
    // raggiunto MIN_STOP_MILLIS (transito, non ancora salvato).
    private var openStopId: String? = null

    // Sosta per cui la notifica in tempo reale è già stata valutata/inviata.
    private var realtimeHandledStopId: String? = null

    private var cachedSettings: UserSettings? = null
    private var settingsLoadedAt = 0L

    private var started = false
    private var currentIntervalMillis = LONG_INTERVAL_MILLIS

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            val fix = LocationFix(
                lat = location.latitude,
                lon = location.longitude,
                timestamp = location.time,
                accuracyMeters = location.accuracy,
            )
            serviceScope.launch { fixMutex.withLock { processFix(fix) } }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground va chiamato entro pochi secondi da startForegroundService,
        // quindi PRIMA di qualunque operazione asincrona.
        startForeground(NOTIFICATION_ID, buildForegroundNotification())

        // onStartCommand può arrivare più volte (pulsante in Home, riavvio
        // START_STICKY): l'inizializzazione va fatta una sola volta.
        if (started) return START_STICKY
        started = true
        isRunning = true

        serviceScope.launch {
            fixMutex.withLock {
                val active = resolveSession()
                if (active == null) {
                    // Nessun utente/squadra (logout, primo avvio): niente da tracciare.
                    stopSelf()
                    return@withLock
                }
                session = active
                resumeOrCloseStaleStop(active)
            }
        }
        startLocationUpdates(currentIntervalMillis)
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        fusedLocationClient.removeLocationUpdates(locationCallback)

        // Chiude la sosta aperta all'ultimo fix ricevuto (prima restava "in
        // corso" per sempre dopo "Ferma tracciamento" o logout) e segnala ai
        // colleghi che non si è più tracciati. Scritture non attese: Firestore
        // le salva in cache locale e le sincronizza appena possibile.
        val active = session
        val open = clusterer.currentOpenStop()
        val id = openStopId
        if (active != null) {
            if (id != null && open != null) {
                stopRepository.writeTrackingFields(
                    active.teamId, active.uid, id,
                    startedAt = open.startedAt, endedAt = open.endedAt,
                    lat = open.lat, lon = open.lon, lastFixAt = open.endedAt, kind = null,
                )
            }
            teamRepository.markOffline(active.teamId, active.uid)
        }

        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * La sessione può non essere ancora in memoria (riavvio START_STICKY dopo
     * un kill del processo: il ripristino in PlumberDiaryApp è asincrono).
     * Stesso ripristino di BootRestartReceiver: uid da Firebase Auth, squadra
     * da SessionStore.
     */
    private suspend fun resolveSession(): CurrentSession.Active? {
        CurrentSession.current()?.let { return it }
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return null
        val teamId = SessionStore(this).readTeamId() ?: return null
        CurrentSession.set(uid, teamId)
        return CurrentSession.current()
    }

    // Versione precedente (v1.0.0 — 2026-09-20), sostituita il 2026-09-29:
    // riprendeva qualunque sosta aperta, anche vecchia di giorni, fondendola
    // con la nuova giornata.
    //
    // private suspend fun resumeOpenStopIfAny() {
    //     val session = CurrentSession.requireActive()
    //     val open = stopRepository.getOpenStop(session.teamId, session.uid) ?: return
    //     lastStopId = open.id
    //     clusterer.resume(open)
    // }
    private suspend fun resumeOrCloseStaleStop(active: CurrentSession.Active) {
        val open = try {
            stopRepository.getOpenStop(active.teamId, active.uid)
        } catch (e: Exception) {
            Log.w(TAG, "Lettura sosta aperta non riuscita, si riparte da zero", e)
            null
        } ?: return

        val lastSeen = if (open.lastFixAt > 0L) open.lastFixAt else open.startedAt
        if (System.currentTimeMillis() - lastSeen > STALE_OPEN_STOP_MILLIS) {
            // Tracciamento interrotto a lungo: la sosta si chiude dove era
            // stata vista l'ultima volta, e se ne apre una nuova.
            stopRepository.writeTrackingFields(
                active.teamId, active.uid, open.id,
                startedAt = open.startedAt, endedAt = lastSeen,
                lat = open.lat, lon = open.lon, lastFixAt = lastSeen, kind = null,
            )
            return
        }

        openStopId = open.id
        clusterer.resume(open.copy(endedAt = lastSeen))
        if (open.clientId != null || open.realtimeConfirmedAt != null || open.realtimeDismissedAt != null) {
            realtimeHandledStopId = open.id
        }
    }

    // Versione precedente (v1.7.0 — 2026-09-23), sostituita il 2026-09-29 (vedi
    // commento di classe per i difetti):
    //
    // private fun onNewFix(fix: LocationFix) {
    //     serviceScope.launch {
    //         val session = CurrentSession.requireActive()
    //         val settings = settingsRepository.get(session.teamId, session.uid)
    //         val closedStop = clusterer.onFix(fix)
    //         if (closedStop != null) { finalizeStop(...); adjustSamplingInterval(moving = true) }
    //         else adjustSamplingInterval(moving = false)
    //         val open = clusterer.currentOpenStop()
    //         if (open != null) {
    //             val openToPersist = if (lastStopId == null) open else open.copy(id = lastStopId!!)
    //             lastStopId = stopRepository.upsert(session.teamId, session.uid,
    //                 openToPersist.copy(endedAt = 0L, kind = classifyKind(openToPersist, settings)))
    //             checkThresholdForRealtimeConfirmation(session.teamId, session.uid, open, settings)
    //             if (settings.seeTeamLocationEnabled) teamRepository.updateOwnLiveLocation(...)
    //         }
    //     }
    // }
    private suspend fun processFix(fix: LocationFix) {
        val active = session ?: resolveSession()?.also { session = it } ?: run {
            stopSelf()
            return
        }
        val settings = settings(active)

        val closedStop = clusterer.onFix(fix)
        if (closedStop != null) {
            finalizeStop(active, closedStop, settings)
            adjustSamplingInterval(moving = true)
        } else {
            adjustSamplingInterval(moving = false)
        }

        val open = clusterer.currentOpenStop() ?: return
        persistOpenStop(active, open, settings)

        val openId = openStopId
        if (openId != null) checkThresholdForRealtimeConfirmation(active, openId, open, settings)

        if (settings.seeTeamLocationEnabled) {
            teamRepository.updateOwnLiveLocation(
                active.teamId, active.uid,
                LiveLocation(lat = fix.lat, lon = fix.lon, updatedAt = fix.timestamp, status = MemberStatus.ACTIVE),
            )
        }
    }

    /** La sosta aperta va salvata solo dopo MIN_STOP_MILLIS: prima è un transito. */
    private fun persistOpenStop(active: CurrentSession.Active, open: Stop, settings: UserSettings) {
        val kind = classifyKind(open, settings)
        var id = openStopId
        val isNew = id == null
        if (isNew) {
            if (open.endedAt - open.startedAt < MIN_STOP_MILLIS) return
            id = stopRepository.newStopId(active.teamId, active.uid)
            openStopId = id
        }
        stopRepository.writeTrackingFields(
            active.teamId, active.uid, id!!,
            startedAt = open.startedAt, endedAt = 0L,
            lat = open.lat, lon = open.lon, lastFixAt = open.endedAt,
            // Alla creazione si scrive sempre il tipo; dopo, solo sede/pausa,
            // per non sovrascrivere un CLIENT scelto dall'utente.
            kind = if (isNew) kind else kind.takeIf { it != StopKind.UNRESOLVED },
        )
    }

    /** Sede/deposito o pausa (requisito 13) → kind definitivo; altrimenti UNRESOLVED. */
    private fun classifyKind(stop: Stop, settings: UserSettings): StopKind = when {
        DepotAndBreakFilter.isAtDepot(stop.lat, stop.lon, settings) -> StopKind.DEPOT
        DepotAndBreakFilter.isDuringBreak(stop.startedAt, settings.breaks) -> StopKind.BREAK
        else -> StopKind.UNRESOLVED
    }

    private fun finalizeStop(active: CurrentSession.Active, stop: Stop, settings: UserSettings) {
        // Il campo distanceFromPreviousMeters viene completato dal recap
        // (RecapScreen), che ha visibilità sull'intera sequenza ordinata delle
        // soste del giorno.
        val id = openStopId ?: if (stop.endedAt - stop.startedAt >= MIN_STOP_MILLIS) {
            stopRepository.newStopId(active.teamId, active.uid)
        } else {
            null // transito mai salvato: scartato
        }
        openStopId = null
        realtimeHandledStopId = null
        if (id == null) return

        val kind = classifyKind(stop, settings)
        stopRepository.writeTrackingFields(
            active.teamId, active.uid, id,
            startedAt = stop.startedAt, endedAt = stop.endedAt,
            lat = stop.lat, lon = stop.lon, lastFixAt = stop.endedAt,
            kind = kind.takeIf { it != StopKind.UNRESOLVED },
        )
    }

    private suspend fun checkThresholdForRealtimeConfirmation(
        active: CurrentSession.Active,
        stopId: String,
        openStop: Stop,
        settings: UserSettings,
    ) {
        if (!settings.realtimeConfirmationEnabled) return
        if (realtimeHandledStopId == stopId) return
        if (DepotAndBreakFilter.isAtDepot(openStop.lat, openStop.lon, settings)) return
        if (DepotAndBreakFilter.isDuringBreak(openStop.startedAt, settings.breaks)) return

        val elapsedMinutes = TimeUnit.MILLISECONDS.toMinutes(openStop.endedAt - openStop.startedAt)
        if (elapsedMinutes < settings.clientDetectionThresholdMinutes) return

        try {
            // Stato reale della sosta su Firestore (l'utente può averla già
            // associata dal Dettaglio): una sola lettura, poi mai più per
            // questa sosta qualunque sia l'esito.
            val persisted = stopRepository.getStop(active.teamId, active.uid, stopId)
            val clients = clientRepository.getAll(active.teamId)
            realtimeHandledStopId = stopId
            if (persisted?.clientId != null || persisted?.realtimeConfirmedAt != null || persisted?.realtimeDismissedAt != null) {
                return
            }
            val suggested = ClientMatcher.findSuggestedClient(openStop.lat, openStop.lon, clients) ?: return
            RealtimeConfirmNotifier.notify(this, suggested, openStop.copy(id = stopId))
        } catch (e: Exception) {
            // Letture non riuscite: si riprova al fix successivo.
            Log.w(TAG, "Controllo notifica in tempo reale non riuscito", e)
        }
    }

    /**
     * Opzioni rilette al massimo ogni [SETTINGS_REFRESH_MILLIS] (prima una
     * lettura Firestore a ogni fix). Le modifiche fatte in Opzioni entrano in
     * vigore entro pochi minuti.
     */
    private suspend fun settings(active: CurrentSession.Active): UserSettings {
        val now = System.currentTimeMillis()
        val cached = cachedSettings
        if (cached != null && now - settingsLoadedAt < SETTINGS_REFRESH_MILLIS) return cached
        return try {
            settingsRepository.get(active.teamId, active.uid).also {
                cachedSettings = it
                settingsLoadedAt = now
            }
        } catch (e: Exception) {
            cached ?: UserSettings()
        }
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
        /** Letto dalla Home per mostrare "Inizia"/"Ferma" (processo unico, basta un flag). */
        @Volatile
        var isRunning: Boolean = false
            private set

        private const val TAG = "LocationTracking"
        private const val CHANNEL_ID = "location_tracking"
        private const val NOTIFICATION_ID = 1001
        private val LONG_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(5)
        private val SHORT_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(1)

        /** Sotto questa durata una sosta è un transito e non viene salvata. */
        private val MIN_STOP_MILLIS = TimeUnit.MINUTES.toMillis(5)

        /** Sosta aperta senza fix da più di così = tracciamento interrotto: va chiusa. */
        private val STALE_OPEN_STOP_MILLIS = TimeUnit.MINUTES.toMillis(30)

        private val SETTINGS_REFRESH_MILLIS = TimeUnit.MINUTES.toMillis(10)
    }
}
