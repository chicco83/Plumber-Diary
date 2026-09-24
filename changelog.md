# Changelog — Plumber Diary

## 1.0.0 — 2026-09-19 22:27 UTC
- Analisi di mercato su app concorrenti (field service GPS tracking).
- Definiti requisiti funzionali e decisioni di progetto iniziali.
- Creato mockup delle 7 schermate principali (Artifact): Home, Recap serale, Dettaglio posizione, Anagrafica cliente + articoli, Conferma invio mandatino PDF, Opzioni, Storico recap.
- Aggiunto invio recap giornaliero via email amministrazione (default attivo, configurabile da Opzioni).
- Aggiunto in anagrafica cliente il campo email per l'invio del "mandatino delle ore" in PDF, con conferma utente obbligatoria prima dell'invio.
- Nessun codice applicativo ancora scritto: fase di solo progetto/mockup.

## 1.1.0 — 2026-09-19 23:10 UTC
- Aggiunto requisito: foto cliente allegabili dalla galleria del telefono (Photo Picker di sistema).
- Aggiunto requisito: multiutente/squadra — mappa live condivisa delle posizioni dei colleghi, opzione (default OFF) per vedere i recap serali già confermati dagli altri membri, anagrafica clienti e listino articoli condivisi a livello di squadra.
- Nuova schermata mockup *Squadra* (mappa live + elenco membri) e sezione "Squadra" nelle Opzioni (gestione squadra, toggle visibilità, invito colleghi).
- Aggiornata la scheda cliente nel mockup con la galleria foto.
- Analizzato il repo gemello `chicco83/gwatch-child-tracker` (stessa combinazione Firebase + geolocalizzazione, già in produzione) per riusarne stack e lezioni: Firestore Spark + Vercel Functions Hobby + GitHub Actions per i cron (le Cloud Functions Firebase e i Cron Job Vercel nativi richiedono piani a pagamento/carta), osmdroid al posto di Google Maps SDK (Google Maps richiede fatturazione attiva ad ogni chiamata), FCM data-only per notifiche che devono svegliare l'app in background.
- Calcolati i margini rispetto ai limiti del piano gratuito Firebase/Vercel per una squadra di 6-8 tecnici: ampi su Firestore (letture/scritture/storage), il collo di bottiglia a lungo termine più probabile è lo storage foto Firebase Storage (5 GB gratuiti) — richiede compressione lato client.

## 1.2.0 — 2026-09-19 23:35 UTC
- Requisito: le foto (su un intervento/posizione o sulla scheda cliente) devono arrivare **in alta risoluzione** nella mail di recap giornaliero all'amministrazione, non nella versione compressa usata in app.
- Decisione tecnica: ogni foto genera due copie su Firebase Storage — un **originale alta risoluzione**, temporaneo, usato solo per l'allegato email e cancellato dopo l'invio riuscito (job di pulizia via GitHub Actions, stesso meccanismo affidabile già usato nel progetto gemello); una copia **compressa** (~300–500 KB), questa conservata stabilmente per la visualizzazione in app e la sincronizzazione di squadra.
- Decisione tecnica: se il totale degli allegati in alta risoluzione supera la soglia pratica di un provider email (~20–25 MB), l'invio passa da allegati diretti a link di download sicuro e a scadenza, mai a foto scartate o inviate a risoluzione ridotta senza avviso.
- Aggiunta al mockup la sezione "Foto intervento" nella schermata Dettaglio posizione (prima le foto erano previste solo sulla scheda cliente) e un'indicazione delle foto allegate nella card del Recap serale.
- Rivista la tabella dei limiti del piano gratuito: lo storage Firebase Storage resta ampio sulla copia compressa stabile; la copia originale in alta risoluzione non si accumula nel tempo perché temporanea, a patto che il job di pulizia post-invio funzioni.

## 1.3.0 — 2026-09-19 23:55 UTC
Aggiunte 5 funzioni proposte e approvate dall'utente (una — il rapportino con firma — con richiesta esplicita di renderla sempre saltabile):
- **Sede/deposito e pause escluse dal rilevamento cliente**: una posizione marcata come sede o una fascia oraria marcata come pausa non genera mai la proposta "nuovo possibile cliente", né la notifica in tempo reale. Nuova sezione "Sede e pause" nelle Opzioni.
- **Notifica di conferma cliente in tempo reale**: appena la soglia di permanenza è superata su una posizione nota, notifica locale immediata "sei da [cliente]?" con azioni rapide, invece di aspettare solo il recap serale. Nuovo mockup *Notifica*, nuovo toggle nelle Opzioni.
- **Rapportino d'intervento con firma cliente, sempre saltabile**: PDF generato dal recap con orari/note/materiali dell'intervento, firmabile su schermo dal cliente; pulsante "Salta" sempre disponibile ed esplicito, il rapportino può essere inviato anche senza firma. Nuovo mockup *Rapportino*.
- **Calcolo km percorsi**: distanza stimata tra una sosta e la successiva, mostrata su Recap/Dettaglio posizione e aggregata per rimborso carburante/nota spese.
- **Dashboard mensile**: ore totali, km, valore materiali e interventi per cliente/periodo, accessibile dalle Opzioni. Nuovo mockup *Dashboard*. Export CSV esteso per includere anche i km.
- Non implementata (rifiutata dall'utente): chat di squadra.

## 1.4.0 — 2026-09-20 00:10 UTC
Prima stesura dell'architettura tecnica e dello scaffolding di codice (finora solo progetto/mockup).
- Creato lo scaffolding Android (Kotlin/Jetpack Compose) in `app/`: modelli dati, percorsi Firestore, repository, clustering soste, riconoscimento cliente, esclusione sede/pause, foreground service di tracciamento con sampling adattivo, notifica di conferma in tempo reale, foto a doppia risoluzione, generatori PDF (mandatino e rapportino), calcolo km, navigazione Compose con una route per schermata del mockup.
- Creato il backend Vercel Functions in `backend/`: endpoint `create-team`, `create-invite`, `accept-invite` (unico punto di iscrizione a una squadra, mai una scrittura diretta del client), `send-recap-email` (foto in alta risoluzione con fallback a link firmati, requisito 12), `send-mandatino`, `cleanup` (retention posizioni, foto originali scadute, inviti, quote); Firestore Security Rules e indici; workflow GitHub Actions per il cron di pulizia giornaliera.
- Aggiunta la sezione "Stato implementazione" a `context.md`: scaffolding scritto ma non compilato/deployato (mancano un vero progetto Firebase, `google-services.json`, icone launcher, variabili d'ambiente Vercel — stesso limite incontrato all'avvio del progetto gemello gwatch-child-tracker).
- Aggiunti `.gitignore`, `README.md` di root/app/backend con i passaggi manuali richiesti prima del primo build/deploy.

## 1.5.0 — 2026-09-20 00:30 UTC
- Scritto il login Google lato app: `auth/AuthRepository.kt` (Google Sign-In → Firebase Authentication), `ui/auth/LoginScreen.kt`, `ui/auth/TeamSelectionScreen.kt` (crea una nuova squadra o unisciti con codice invito, tramite `backend/api/create-team.js`/`accept-invite.js`), `session/SessionStore.kt` (persistenza del teamId scelto tra riavvii), `data/BackendClient.kt` (chiamate autenticate al backend Vercel).
- `PlumberDiaryNavHost` ora apre su Login → Selezione/Creazione squadra → Home, invece di entrare direttamente in Home senza autenticazione.
- `PlumberDiaryApp.onCreate()` ripristina `CurrentSession` da Firebase Auth + `SessionStore` all'avvio (necessario perché `BootRestartReceiver`/`LocationTrackingService` la richiedono).
- Non ancora creato un vero progetto Firebase: impossibile farlo da questa sessione (nessun browser/credenziali). Chiesto all'utente come preferisce sbloccarlo; ha scelto di fornire una chiave service account generata dalla Console, da usare via API una tantum (poi cancellata) — stesso procedimento già impiegato nel progetto gemello gwatch-child-tracker.

## 1.6.0 — 2026-09-24
- Decisione sugli account (solo documentazione, nessuna modifica al codice): struttura attuale mantenuta (Firestore Spark + Vercel Hobby + GitHub Actions). Firebase su un **progetto nuovo** nello stesso account Google del family tracker, per avere quote Spark, regole di sicurezza e utenti Auth separati. Vercel su un **account nuovo**, così un'eventuale sospensione non coinvolge il backend del family tracker.
- Registrato in `context.md` il rischio accettato: il piano Vercel Hobby è riservato a uso non commerciale e Plumber Diary rientra nell'uso commerciale; la via di migrazione prevista, se servisse, è Cloud Functions su Firebase Blaze.
Implementazione completa delle schermate (chiuso il punto "schermate ancora stub") e messa in piedi del setup manuale:
- **Correzioni al foreground service** (`LocationTrackingService`): la sosta APERTA viene ora persistita su Firestore a ogni fix (prima esisteva solo in memoria e andava persa al kill del processo); classifica della kind (sede/pausa/da verificare) estratta in `classifyKind()`; il check della notifica in tempo reale salta anche le soste silenziate con "Non ora" (`Stop.realtimeDismissedAt`, nuovo campo).
- **`PlumberDiaryApp.onCreate()`**: init di osmdroid con user-agent personalizzato (senza, il tile server OSM rifiuta le richieste e la mappa Squadra resta vuota).
- **`BootRestartReceiver`**: risolta la race condition — il restore di `CurrentSession` in Application era asincrono e al BOOT_COMPLETED poteva non essere ancora avvenuto; ora il receiver ripristina la sessione in modo bloccante da DataStore prima di riavviare il tracciamento.
- **Repository estesi**: `StopRepository.getStop`, `ClientRepository.getById`, nuovo `ArticleRepository` (listino condiviso, mancava); `BackendClient` raggiunge gli endpoint `send-recap-email` e `send-mandatino`.
- **Tutte e 10 le schermate implementate** (prima stub): Home (timeline + start/stop tracciamento + permessi progressivi), Recap (suggerimento cliente con conferma inline, km, conferma recap, invio email), Dettaglio (orari/cliente/note/promemoria/materiali/foto), Cliente (anagrafica condivisa, foto, mandatino ore con conferma esplicita, listino CRUD), Squadra (mappa osmdroid live + marker colorati + codice invito), Opzioni (tutte le UserSettings + logout), Storico (riassegnazione cliente in blocco su più giorni), Dashboard mensile, Rapportino (firma su canvas, sempre saltabile), NotificaConferma ("Sì, confermo" / "Cambia" / "Non ora").
- **Fix compilazione** `SquadraScreen`: `androidColorSafe(...).toArgb()` non esisteva (`Color` Compose); sostituito con `AndroidColor.parseColor`.
- **Foto visibili in app**: aggiunta dipendenza `coil-compose` e composable `PhotoGrid` (minature delle copie "display" da Storage) integrato in Dettaglio e Cliente; prima le foto venivano caricate ma non mostrate.
- **Launcher icon** vettoriale (`mipmap-anydpi-v26` + `ic_launcher_foreground`), rimossa la dipendenza dai PNG da generare a mano.
- **URL backend centralizzato** in `data/BackendConfig.kt` (prima duplicato in 5 file).
- **`backend/storage.rules`** (nuovo) + section storage in `firebase.json`: un solo `firebase deploy` pubblica regole Firestore, indici e regole Storage.
- **`SETUP.md`** nella radice: guida passo-passo delle configurazioni manuali (Firebase, regole, Vercel + env var, URL backend, build, checklist di test end-to-end, cron pulizia, troubleshooting).
