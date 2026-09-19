# app — Plumber Diary (Android)

Versione: 1.0.0 — 2026-09-20 00:10 UTC

Scaffolding Kotlin/Jetpack Compose per l'app Android. Struttura e algoritmi
principali sono già scritti (vedi sotto); l'app **non è ancora compilabile
"out of the box"**: mancano alcuni passaggi manuali legati a un vero progetto
Firebase, esattamente come nel progetto gemello `chicco83/gwatch-child-tracker`
(vedi il suo `phone-app/README.md` per lo stesso tipo di setup).

## Passaggi manuali richiesti prima del primo build

1. **Creare un progetto Firebase** (piano Spark, gratuito — vedi context.md
   per il perché) dalla Console: https://console.firebase.google.com
2. **Registrare l'app Android** con `applicationId` `com.plumberdiary.app`
   (vedi `app/build.gradle.kts`), scaricare il file generato
   **`google-services.json`** e metterlo in `app/google-services.json`
   (non incluso in questo repo: contiene identificatori del progetto).
3. **Abilitare** in Console: Authentication (provider Google), Firestore
   Database (modalità produzione, poi caricare `backend/firestore.rules`),
   Cloud Storage (per le foto), Cloud Messaging (per le push).
4. **Icone launcher**: generare `mipmap-*/ic_launcher.png` (Image Asset
   Studio in Android Studio) — non incluse in questo scaffolding.
5. Vedi `backend/README.md` per il setup delle Vercel Functions/GitHub
   Actions necessarie a email di recap, mandatino PDF e pulizia storico.

## Login Google (in attesa del progetto Firebase reale)

Il codice di login è scritto e pronto (`auth/AuthRepository.kt`,
`ui/auth/LoginScreen.kt`, `ui/auth/TeamSelectionScreen.kt`,
`session/SessionStore.kt`, `data/BackendClient.kt`): NavHost apre su
Login → (se nessuna squadra salvata) Selezione/Creazione squadra → Home.
Non è ancora testabile perché manca un vero progetto Firebase — vedi
`context.md`, "Stato implementazione", per cosa serve dall'utente prima di
poterlo compilare e chi ha fornito cosa in questa fase.

Una volta disponibile `google-services.json`, aggiornare anche
`BACKEND_BASE_URL` in `ui/PlumberDiaryNavHost.kt` con l'URL reale del
deploy Vercel (oggi è un placeholder).

## Cosa è già implementato in questo scaffolding

- **Modelli dati** (`data/model/`): `Stop`, `ClientRecord`, `Article`,
  `Team`/`TeamMember`, `UserSettings` — rispecchiano lo schema Firestore
  documentato in `data/FirestorePaths.kt` e in `context.md`.
- **Tracciamento e riconoscimento cliente** (`location/`):
  - `StopClusterer` — raggruppa i fix GPS in soste (requisito 2);
  - `ClientMatcher` — propone un cliente noto da posizione (requisito 4);
  - `DepotAndBreakFilter` — esclude sede/deposito e pause (requisito 13);
  - `LocationTrackingService` — foreground service che li orchestra con
    sampling adattivo, e dichiara la notifica di conferma in tempo reale
    (requisito 14).
- **Foto a doppia risoluzione** (`photo/PhotoUploader.kt`, requisito 12).
- **PDF**: `pdf/MandatinoPdfGenerator.kt` (requisito 9) e
  `pdf/RapportinoPdfGenerator.kt` (requisito 15, firma sempre saltabile).
- **Km percorsi**: `recap/DailyDistanceCalculator.kt` (requisito 16).
- **Repository Firestore** (`data/repository/`): `StopRepository`,
  `ClientRepository`, `TeamRepository`, `SettingsRepository`.
- **Navigazione e schermate** (`ui/`): una route per ogni schermata del
  mockup (Home, Recap, Dettaglio, Cliente, Squadra, Opzioni, Storico,
  Dashboard, Rapportino, Notifica conferma) — i layout dettagliati sono
  marcati `TODO` con riferimento al relativo file `.dc.html` del mockup, la
  struttura/navigazione/collegamento ai repository è già pronta.

## Cosa manca (prossimi passi, non ancora iniziati)

- ViewModel che colleghino le schermate `TODO` ai repository (oggi i
  repository esistono e sono pronti all'uso, ma le Composable non li
  chiamano ancora).
- Persistenza di `session/CurrentSession` su DataStore, per sopravvivere a
  un riavvio dell'app/del telefono.
- UI dettagliata pixel-per-pixel rispetto al mockup (oggi le schermate sono
  strutturalmente corrette ma minimali).
- Integrazione reale con Google Calendar (dipendenza già in
  `build.gradle.kts`, wiring UI/OAuth non ancora scritto).
