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

## 1.7.0 — 2026-09-24 (scritta da Qwen, IA locale)
> Nota aggiunta il 2026-09-29: questa versione è stata sviluppata da Qwen (modello locale `Qwen3.6-35B-A3B-UD-Q4_K_M`). Era stata pubblicata con autore "Claude" nel commit `3122a84`; su richiesta dell'utente l'autore è stato corretto riscrivendo la storia del branch, e il commit ora è `38e8852`, con autore `Qwen3.6-35B-A3B-UD-Q4_K_M` e contenuto invariato. I messaggi dei commit successivi possono ancora citare il vecchio hash `3122a84`. In origine la versione era etichettata anch'essa 1.6.0 e registrata senza titolo proprio dentro la voce precedente: è stata rinumerata qui (e nelle etichette del codice) per distinguerla. Molti punti elencati sotto sono stati corretti nella 1.8.0: il codice di questa versione non compilava.

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

## 1.8.0 — 2026-09-29
Review completa della 1.7.0 e correzione di tutti i problemi trovati. **Non ancora compilato**: in questo ambiente non c'è l'SDK Android, il primo build va fatto in Android Studio (vedi `SETUP.md`).

**Errori di compilazione della 1.7.0 corretti**
- `SquadraScreen`: import di `Marker` dal package sbagliato, `toObject` senza import, `onDispose` dentro `LaunchedEffect`, `Color(Color)`, `setIcon` con un `Bitmap` al posto di un `Drawable`.
- `RecapScreen`: `items(...)` senza import.
- `DettaglioScreen`: `stopPhotoDisplay` chiamata con 3 argomenti invece di 4.
- `NotificaConfermaScreen`: funzione locale passata come `onClick = goHome`.
- `RapportinoScreen`: riscritta (variabili mai dichiarate, funzione `@Composable` chiamata da una coroutine, `onStart` al posto di `onDragStart`, `drawContent` inesistente, funzione usata prima di essere dichiarata).
- `OpzioniScreen`: `await()` fuori da una coroutine, 5 switch con `it` in conflitto.
- `DashboardScreen`: tipo dichiarato `Triple<Long,…>` riempito con una `String`.

**Bug funzionali corretti**
- **Perdita di dati**: il foreground service riscriveva l'intero documento della sosta a ogni fix, cancellando cliente, note, foto, materiali e conferme inseriti dall'utente. Ora service e schermate scrivono ciascuno solo i propri campi, con merge (`StopRepository.writeTrackingFields` / `saveUserEdits`).
- **Notifica "Sei da…" ripetuta a ogni fix**: il controllo leggeva la copia in memoria, dove "confermato"/"non ora" erano sempre vuoti, e rileggeva tutti i clienti ogni volta (rischio di sforare le 50.000 letture gratuite al giorno). Ora parte al massimo una volta per sosta.
- **Soste di transito**: soste più brevi di 5 minuti (semafori, traffico) non vengono più salvate. Prima ogni fix in movimento generava una sosta da 0 minuti.
- **Soste "in corso" per sempre**: fermando il tracciamento o facendo logout la sosta aperta viene chiusa; al riavvio una sosta aperta senza fix da oltre 30 minuti viene chiusa invece di essere ripresa.
- **Offline**: i fix sono elaborati uno alla volta e le scritture del service non attendono il server. Prima, senza rete, le coroutine si accavallavano creando soste duplicate.
- **Crash**: il service non va più in crash per un errore di rete o dopo il logout; il logout ferma il tracciamento.
- **Miniature foto mai visibili** (`PhotoGrid`: mancava `await()` sull'URL).
- **Foto perse**: foto di interventi e clienti ora salvate subito sul documento (arrayUnion), non solo al pulsante "Salva".
- **Foto HD della scheda cliente mai cancellate** (caricate su `display.jpg.orig`): ora la scheda cliente salva solo la copia compressa.
- **`storage.rules` non pubblicabili** (`exists` → `firestore.exists`); aggiunti limite di 25 MB e solo immagini, con content-type impostato negli upload.
- **Scheda Cliente irraggiungibile**: nuova schermata `ClientiScreen` (anagrafica condivisa, ricerca, creazione) dalle Opzioni; link "Apri scheda cliente" nel Dettaglio.
- **Correzione massiva dello Storico**: assegnava sempre il primo cliente della lista; ora il cliente si sceglie.
- **Firma del rapportino**: nel PDF finiva solo la diagonale del riquadro; ora è la firma tracciata.
- **Km totali** in Home, Recap e Storico mille volte più piccoli (km passati dove servivano metri).
- **Colonne senza scorrimento**: in Dettaglio, Cliente, Opzioni, Storico, Dashboard, Home, Squadra e Rapportino i pulsanti in fondo erano fuori schermo. In Recap la lista copriva i pulsanti di conferma.
- Mappa Squadra: rilasciata uscendo dalla schermata e ricentrata sulla sede quando disponibile; i colleghi che fermano il tracciamento compaiono come offline.

**Bug già presenti nello scaffolding 1.4/1.5 (mio), corretti**
- **Tap sulla notifica con l'app già aperta**: `MainActivity` non gestiva `onNewIntent` e il tap non faceva nulla.
- **Permesso notifiche mai richiesto**: `POST_NOTIFICATIONS` (Android 13+), senza il quale nessuna notifica compariva; ora lo chiede la Home.
- **Scritture concorrenti sull'anagrafica condivisa**: si perdevano le modifiche dei colleghi (ora campi mirati, arrayUnion, posizioni note in transazione).
- **Race al primo avvio**: la sessione poteva essere vuota nelle schermate (ora impostata dalla NavHost prima di mostrarle).
- **Foto elaborate sul thread UI**: ora fuori dal main thread.

**Requisiti completati**
- **Recap serale programmato** (requisito 3): notifica "Recap di oggi pronto" all'orario delle Opzioni (WorkManager, `RecapScheduler`/`RecapWorker`); il nuovo orario vale dal salvataggio.
- **Mail di recap all'amministrazione** (requisito 6, default ON): parte automaticamente alla **conferma del recap**, così contiene i dati già corretti; resta anche il pulsante manuale.
- **"Vedi recap dei colleghi"** (requisito 11, default OFF): nello Storico, selettore del collega; mostra in sola lettura i suoi recap confermati.

**Documentazione e versioni**
- Rinumerata come 1.7.0 la versione di Qwen (vedi sopra).
- Aggiornate le intestazioni di versione dei file che la 1.7.0 aveva modificato senza incrementarle.
- Aggiornati `context.md`, `manual.md` e `SETUP.md`.

## 1.9.0 — 2026-09-29
- **Mandatino con le ore dei colleghi**: nella finestra di conferma del mandatino c'è la casella "Includi le ore dei colleghi" (default OFF). Se attiva, vengono aggiunte le ore degli altri membri della squadra presso lo stesso cliente, limitate ai giorni che ciascun collega ha già confermato nel recap. Nella finestra compare il riepilogo per tecnico; il PDF (`MandatinoPdfGenerator`) aggiunge la colonna "Tecnico" e le ore totali per tecnico. Senza la casella il mandatino resta come prima.
- **Autore dei commit di Qwen**: il commit della 1.7.0 riportava "Claude" come autore. Riscritta la storia del branch (push forzato, contenuti invariati) perché risulti `Qwen3.6-35B-A3B-UD-Q4_K_M <qwen@localhost>`; dettagli nella nota della voce 1.7.0.

## 1.10.0 — 2026-09-29
Correzione del mandatino dopo il chiarimento dell'utente: si compila **sul posto a fine intervento** ed è l'accettazione dell'addebito da parte del cliente, quindi non può dipendere dal recap serale.
- Ore dei colleghi incluse **a prescindere dal recap**: nella 1.9.0 erano limitate ai giorni già confermati.
- Le soste **ancora in corso** (proprie e dei colleghi) sono conteggiate fino ad ora, o fino all'ultimo fix se il tracciamento è fermo da oltre 30 minuti. Prima erano escluse, cioè proprio l'intervento da far firmare.
- Incluse anche le soste **senza cliente associato** che si trovano in una posizione nota del cliente: sul posto il collega di solito non ha ancora associato il cliente. La finestra di conferma le conta e le segnala da verificare.
- Periodo **"Oggi"** aggiunto e predefinito; nel PDF compare la data invece di "Oggi".

## 1.11.0 — 2026-09-29
**Mandatino e rapportino unificati.** Sono lo stesso documento: su chiarimento dell'utente, il "rapportino con firma" (introdotto nella 1.3.0 come funzione nuova) coincideva con il mandatino delle ore già richiesto al requisito 9. È stato un errore di progettazione, mio. Ora esiste un solo documento, il **mandatino**:
- **una sola schermata**, `ui/mandatino/MandatinoScreen.kt`, raggiungibile dal Dettaglio della sosta (sul posto), dalla card del Recap e dalla scheda cliente;
- **contenuto**: periodo (Oggi predefinito, mese, 30 giorni), ore dei colleghi a scelta, riepilogo con soste in corso e soste per posizione segnalate, **firma del cliente per accettazione** (sempre saltabile), invio all'email del cliente dopo la conferma del tecnico oppure condivisione del PDF con un'altra app;
- **un solo generatore**, `pdf/MandatinoPdfGenerator.kt`: ore, colonna Tecnico, materiali aggregati, dichiarazione di accettazione e firma, oppure "non firmato";
- **rimossi** `RapportinoScreen`, `RapportinoPdfGenerator` e la route `rapportino/{stopId}`; dalla scheda cliente è stata spostata la vecchia finestra di conferma;
- aggiornati i requisiti 9 e 15 in `context.md`, il manuale, `SETUP.md` e `app/README.md`.

## 1.12.0 — 2026-09-29 11:38 UTC
**Correzioni della review completa della 1.11.0.** Codice non ancora compilato (manca l'SDK Android in questo ambiente); il nuovo raggruppamento GPS è coperto da 5 test JVM, eseguiti con successo.

Bloccanti:
- **Inviti**: il codice mostrato e copiato è ora completo (`<squadra>.<codice>`) e basta da solo per unirsi. Prima servivano anche l'ID squadra, che nessuna schermata mostrava: il collega invitato non poteva entrare. (`create-invite.js`, `accept-invite.js`, `_lib/ids.js`, `TeamSelectionScreen`, `BackendClient`)
- **Mandatino dal Dettaglio**: il pulsante salva la sosta (cliente incluso) prima di aprire il mandatino; prima, con un cliente appena scelto o creato, il mandatino risultava vuoto.
- **Mail di recap**: ora riporta per ogni sosta cliente (o sede/pausa/da associare), orario, durata, note, promemoria, materiali e i totali del giorno; prima mancava il cliente e la colonna Posizione era sempre vuota.
- **Indici Firestore**: rimossi i due indici a campo singolo dichiarati come composti, che facevano fallire `firebase deploy`; aggiunto l'indice collection group su `members.uid`.

Importanti:
- Gli endpoint email spediscono solo a indirizzi salvati sul server (email del cliente in anagrafica, email amministrazione nelle Opzioni), con quote di 50 mandatini e 20 recap al giorno per utente; testi dell'utente escaped nell'HTML; id validati prima di comporre i percorsi Firestore.
- Tracciamento: la precisione del fix GPS è considerata e uno spostamento va confermato da due fix consecutivi, così un fix impreciso non spezza più le soste (`StopClusterer`, test in `app/src/test`).
- Dopo un riavvio del telefono il tracciamento riparte solo se era acceso (`SessionStore.trackingEnabled`); controllo del permesso di posizione al boot.
- La notifica "Sei da…?" porta l'id della sosta: la conferma va sulla sosta notificata, non su quella aperta al momento del tocco.
- Mandatino: alla conferma le proprie soste incluse per posizione vengono associate al cliente; l'email corretta sul posto viene salvata in anagrafica prima dell'invio.
- Nuovo endpoint `my-teams` e sezione "Le tue squadre": rientro nelle proprie squadre dopo logout, reinstallazione o cambio telefono.
- Foto: orientamento EXIF applicato alla copia display e decodifica ridotta (niente più foto ruotate né crash per memoria); nuova dipendenza `androidx.exifinterface`.
- Le copie HD delle foto non vengono più cancellate subito dopo l'invio del recap (il reinvio arrivava senza foto): le elimina il cleanup dopo 7 giorni. Il cleanup cancella anche le foto delle soste scadute.

Minori:
- Dashboard: "Ore presso clienti" e km sommati giorno per giorno.
- Notifica di recap: sede e pause non contano fra le soste da rivedere.
- "Conferma recap" non segna come confermata la sosta ancora in corso.
- Dettaglio: l'inizio di una sosta in corso non è più modificabile (la modifica andava persa).
- Condivisione posizione spenta → i colleghi vedono il tecnico offline (campo rinominato `shareOwnLocationEnabled`).
- Barra delle schede senza duplicati nello storico di navigazione.
- Logout: attesa (max 5 s) delle scritture in coda prima dell'uscita, così la sosta aperta viene chiusa.
- Aggiunto il Gradle wrapper (Gradle 8.9).
- Documentazione: `context.md`, `manual.md`, `SETUP.md`, `backend/README.md`.

Decisioni aperte: Firebase Storage e piano Blaze; licenza AGPL di iText.

## 1.13.0 — 2026-09-29
**Foto su Backblaze B2 invece di Firebase Storage** (decisione dell'utente dopo la review: da fine 2024 Firebase Storage richiede il piano Blaze, con carta, nei progetti nuovi).
- Storage a oggetti **compatibile S3**, servizio consigliato **Backblaze B2** (10 GB gratuiti, nessuna carta, limiti di spesa impostabili a zero). Il codice usa solo l'API S3 standard: per cambiare servizio (R2, Wasabi, MinIO…) bastano le variabili `S3_*` su Vercel.
- Bucket **privato**: l'app non ha credenziali e riceve dal backend URL firmati a scadenza:
  - `photo-upload-urls` (15 minuti): solo membri della squadra, solo sulle proprie soste o su clienti esistenti, solo immagini (il Content-Type fa parte della firma);
  - `photo-view-urls` (1 ora): solo le copie display, mai gli originali HD.
  Sostituiscono le `storage.rules`, rimosse insieme alla sezione `storage` di `firebase.json`. Il limite di 25 MB, non imponibile su un URL PUT firmato, lo applica il cleanup.
- App: `PhotoUploader` carica con un PUT HTTP diretto sugli URL firmati (firma di `upload()` invariata, schermate non toccate); `PhotoGrid` tiene gli URL in memoria ~50 minuti e usa la chiave dell'oggetto come chiave della cache di Coil, così le miniature non si riscaricano; rimossa la dipendenza `firebase-storage-ktx`.
- Backend: `send-recap-email` e `cleanup` leggono, firmano e cancellano sullo storage S3 (`_lib/objectStore.js`, `_lib/photoKeys.js`); nuove dipendenze `@aws-sdk/client-s3` e `@aws-sdk/s3-request-presigner`; rimossa `FIREBASE_STORAGE_BUCKET`.
- Chiavi degli oggetti invariate rispetto ai vecchi percorsi Storage.
- `SETUP.md`: nuovo passo 1-bis (account B2, bucket privato, regola obbligatoria "Keep only the last version", limiti di spesa a zero, chiave limitata al bucket).
- Decisioni dell'utente registrate in `context.md`: licenza iText lasciata così; quote email invariate (nessun effetto sulle prestazioni).
- Verificati la sintassi di tutti gli endpoint e la generazione degli URL firmati (con credenziali fittizie, senza un bucket reale). App non compilata (manca l'SDK Android).
