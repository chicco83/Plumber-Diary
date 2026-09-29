# Context — Plumber Diary

Versione: 1.13.0 — 2026-09-29 (v1.12.0 — 2026-09-29 11:38 UTC; v1.11.0 / v1.10.0 / v1.9.0 / v1.8.0 — 2026-09-29; v1.7.0 — 2026-09-24 di Qwen; precedenti, vedi changelog.md)

## Obiettivo

App Android per idraulici (e tecnici sul campo in generale) che traccia automaticamente la posizione durante la giornata lavorativa, misura il tempo di permanenza in ogni luogo, e a fine giornata propone un recap per associare ogni posizione a un cliente, correggere gli orari, aggiungere note, agganciare promemoria su Google Calendar e registrare i materiali usati.

## Analisi di mercato (2026-09-19)

Categorie di app esistenti, nessuna copre esattamente il caso d'uso (permanenza automatica → riconoscimento cliente → recap serale correggibile → mandatino ore):

- **Gestionali per tecnici/field service IT**: FieldTrack, D-TEC, Nios4 App Tracker — tracciano posizione e interventi ma sono gestionali aziendali complessi, pensati per titolare+dipendenti, non per l'autonomo con riconoscimento automatico del cliente da soglia di permanenza.
- **GPS time tracking USA**: Timeero, Workyard, ClockShark, QuickBooks Time, Hubstaff — ottimi su geofencing e clock-in/out automatico legato a "jobsite" predefiniti, ma il jobsite va creato a priori: non "scoprono" da soli un nuovo cliente dopo N minuti di sosta, e non hanno un flusso di recap serale conversazionale con proposta cliente + note + articoli + PDF.
- Nessuno dei prodotti trovati integra nativamente: soglia di permanenza personalizzabile per auto-rilevare un "possibile cliente", suggerimento cliente da posizione già nota, listino articoli per cliente con generazione PDF ore/materiali inviabile via email su conferma.

**Conclusione**: lo spazio è libero per un prodotto verticale, semplice, mono-utente (o piccolo team), centrato sul flusso "traccia → riconosci → recap → fattura/mandatino", diverso dai gestionali enterprise citati sopra.

## Requisiti funzionali (da richiesta utente)

1. Tracciamento automatico della posizione durante la giornata (background, consumo batteria sostenibile).
2. Calcolo automatico delle ore di permanenza per ogni posizione (cluster di soste).
3. Riepilogo serale (recap) con: posizioni, ore di permanenza, cliente associato/suggerito, note libere, possibilità di correggere gli orari.
4. Se la posizione è già nota da un intervento precedente, la app propone automaticamente il cliente associato in passato, con possibilità di cambiarlo.
5. Campo note testuali per ogni posizione, compilabile nel recap serale.
6. Menu Opzioni:
   - orario di ricezione del recap;
   - soglia di permanenza (minuti) oltre la quale una posizione è candidata a "nuovo cliente" — default **15 minuti**;
   - invio del recap giornaliero via email a un indirizzo di amministrazione — **attivo di default**, indirizzo configurabile dalle opzioni;
   - accesso allo storico dei recap dei giorni precedenti per correzioni massive (bulk edit su più giorni: riassegnazione cliente, rinomina posizione ricorrente).
7. Integrazione con Google Calendar: dal recap serale si può creare un promemoria (es. preventivo, ritorno) agganciato alla posizione/cliente.
8. Anagrafica cliente: dati anagrafici + listino articoli associabile (codice, descrizione, prezzo unitario, quantità) per registrare i materiali usati in un intervento.
9. **Mandatino delle ore**: il documento con cui il cliente, sul posto a fine intervento, accetta l'addebito delle ore (e dei materiali). PDF con ore, note e materiali, più la firma del cliente per accettazione (sempre saltabile, vedi requisito 15). Inviato all'email dedicata dell'anagrafica cliente solo dopo la conferma esplicita del tecnico, mai in automatico.
10. Anagrafica cliente: possibilità di allegare foto scelte dalla galleria del telefono (non solo fotocamera).
11. **Multiutente / squadra**: più utenti possono far parte della stessa squadra. Ogni utente vede su una mappa condivisa dove si trovano gli altri membri della squadra durante la giornata. Un'opzione (spenta di default) permette di vedere anche i recap serali già confermati dagli altri membri. **Anagrafica clienti e listino articoli sono condivisi**: un'unica fonte per tutta la squadra, non duplicati per utente.
12. Le foto (allegate a un intervento/posizione o alla scheda cliente) devono arrivare **in alta risoluzione** nella mail di recap giornaliero inviata all'amministrazione — non nella versione compressa usata per l'archiviazione/sync in app.
13. **Sede/deposito e pause escluse dal rilevamento cliente**: una posizione marcata come sede/deposito, o una fascia oraria marcata come pausa (es. pranzo), non deve mai essere proposta come "possibile nuovo cliente", anche se la sosta supera la soglia.
14. **Notifica di conferma cliente in tempo reale**: appena la soglia di permanenza viene superata, l'app può notificare subito "sei da [cliente]?" invece di aspettare solo il recap serale — resta comunque modificabile fino a sera. Attivabile/disattivabile dalle Opzioni.
15. **Firma del cliente sul mandatino**, sempre saltabile dal tecnico (pulsante esplicito; il documento riporta allora "non firmato"). *Nota del 2026-09-29: fino alla v1.10.0 questo requisito era descritto come un "rapportino d'intervento" separato dal mandatino. È stato un errore di progettazione (il rapportino era stato proposto come funzione nuova, mentre coincideva con il mandatino già richiesto al requisito 9): l'utente ha chiarito che sono lo stesso documento e sono stati unificati.*
16. **Calcolo km percorsi**: distanza stimata fra una sosta e la successiva, mostrata per singola posizione (recap/dettaglio) e aggregata per rimborso carburante/nota spese.
17. **Dashboard mensile**: ore totali, km percorsi, valore materiali usati e numero interventi, con ripartizione per cliente; accessibile dalle Opzioni, accanto all'export CSV (che ora include anche i km).

## Decisioni di progetto

- **Piattaforma**: Android nativo (Kotlin, Jetpack Compose) per il miglior controllo su tracciamento in background, geofencing e consumo batteria; backend leggero per sync multi-dispositivo e invio email/PDF (non indispensabile in v1, l'app può funzionare offline-first con sync opzionale).
- **Rilevamento posizione**: `FusedLocationProviderClient` con `LocationRequest` a priorità bilanciata + significant-motion/activity recognition per ridurre consumo; clustering delle posizioni (raggio configurabile, es. 80–120 m) per formare le "soste".
- **Riconoscimento cliente**: matching per posizione nota (raggio + tolleranza GPS) su storico interventi; se il tempo di sosta supera la soglia (default 15 min) e la posizione non è nota, viene proposta come "nuovo possibile cliente" nel recap.
- **Recap serale**: notifica locale all'orario configurato (WorkManager, lavoro periodico giornaliero: può arrivare con qualche minuto di ritardo per risparmio batteria, senza bisogno del permesso per gli allarmi esatti); schermata riepilogo editabile (orari, cliente, note, materiali, promemoria) con conferma finale.
- **Invio email recap** (decisione del 2026-09-29): parte automaticamente **alla conferma del recap** da parte dell'utente, non a un orario fisso, così l'amministrazione riceve dati già corretti (clienti, orari, note) invece di quelli grezzi del tracciamento; inviato all'indirizzo amministrazione impostato nelle opzioni (default ON; dalla 1.12.0 il backend legge l'indirizzo dalle Opzioni salvate, non dalla richiesta dell'app), con cliente, orari, durata, note, promemoria e materiali di ogni sosta e i totali del giorno, allegando in **alta risoluzione** ogni foto presente sulle posizioni/interventi del giorno (vedi "Foto" sotto per come si gestiscono dimensione allegati e cancellazione dell'originale dopo l'invio).
- **Mandatino ore PDF** (unificato il 2026-09-29): schermata unica `ui/mandatino/MandatinoScreen.kt`, raggiungibile dal Dettaglio della sosta (caso tipico, sul posto), dalla card del Recap e dalla scheda cliente. Si scelgono il periodo (Oggi predefinito, mese, 30 giorni) e, a scelta, le ore dei colleghi, si rivede il riepilogo, si fa firmare il cliente sullo schermo (o si salta la firma) e si conferma l'invio all'email del cliente; in alternativa si condivide il PDF con un'altra app. Un solo generatore, `pdf/MandatinoPdfGenerator.kt`.
- **Privacy**: retention storico posizioni configurabile (default 12 mesi), permessi Android per posizione in background richiesti in modo esplicito e progressivo (foreground prima, poi background con spiegazione).
- **Foto (cliente e/o intervento)**: selezione da galleria tramite `ActivityResultContracts.PickMultipleVisualMedia` (Photo Picker di sistema — non richiede il permesso `READ_MEDIA_IMAGES` su Android 13+). **Doppia risoluzione per ogni foto**:
  - una copia **originale in alta risoluzione**, caricata nello storage delle foto (Backblaze B2 dalla 1.13.0, prima Firebase Storage) in un percorso separato (`.../photos/{photoId}/original.jpg`), usata **solo** per l'allegato nella mail di recap giornaliero e cancellata dal job di pulizia 7 giorni dopo il caricamento (dalla 1.12.0 non più subito dopo l'invio: così un reinvio del recap contiene ancora le foto HD) — non resta a occupare spazio a tempo indeterminato;
  - una copia **compressa/ridimensionata** (`.../photos/{photoId}/display.jpg`, ~300–500 KB, lato lungo ~1600px), questa sì conservata stabilmente per la visualizzazione in app (scheda cliente, storico) e per la sincronizzazione fra i membri della squadra (banda/traffico contenuti).
  - L'email di recap allega/incorpora quindi sempre l'originale ad alta risoluzione quando presente; se il totale allegati supera una soglia pratica (i provider email in genere limitano un messaggio a ~20–25 MB), l'invio passa automaticamente da allegati diretti a **link di download sicuro e a scadenza** (URL firmato generato dal backend, valido pochi giorni) elencati nel corpo della mail — mai foto scartate o inviate a risoluzione ridotta senza che l'utente lo sappia.
- **Multiutente/squadra**: un utente crea una squadra e invita colleghi con un **codice invito completo** `<squadra>.<codice>` (dalla 1.12.0: prima serviva anche l'ID squadra, che l'app non mostrava); chi è già membro rientra nelle proprie squadre dopo logout o cambio telefono senza nuovo invito (endpoint `my-teams`); anagrafica clienti e listino articoli sono collezioni condivise a livello di squadra, non per singolo utente. Le posizioni/i recap restano invece per-utente (ognuno traccia se stesso), ma sono leggibili dagli altri membri della squadra secondo le due opzioni indipendenti: "vedi posizione squadra" (mappa live, pensata per essere per-utente ma di default ragionevole ON) e "vedi recap colleghi" (default **OFF**, dato che è un dato più sensibile — orari e clienti visitati da altri — va attivato consapevolmente).
- **Sede/pause**: l'utente marca uno o più punti come "sede/deposito" (raggio configurabile, es. 100 m) e una o più fasce orarie ricorrenti come "pausa"; entrambe vengono escluse a monte dall'algoritmo di rilevamento cliente (non generano mai la proposta "nuovo possibile cliente", né la notifica in tempo reale), ma continuano a comparire nella timeline/nel recap come voce informativa ("Sede", "Pausa pranzo").
- **Notifica di conferma in tempo reale**: worker in background che, al superamento della soglia su una posizione nota, invia subito una notifica locale (non serve andare in rete: il match con lo storico posizioni è già disponibile sul device) con azioni rapide "Sì, confermo" / "Cambia" / "Non ora"; la risposta aggiorna lo stato della sosta corrente, che resta comunque riaperto e correggibile nel recap serale. Per posizioni non note (nessun cliente storico) resta il solo flusso serale, dato che non c'è nulla da confermare finché non si sceglie/crea un'anagrafica.
- **Firma sul mandatino**: `Canvas` di disegno a dito, convertita in bitmap alla stessa scala dell'area di firma e inserita nel PDF (non serve una libreria di firma digitale). Il pulsante "Salta la firma" è sempre visibile e non bloccante.
- **Km percorsi**: calcolati come distanza in linea retta o su rete stradale (valutare in fase di implementazione se usare OSRM pubblico, gratuito, oppure la sola distanza euclidea come stima più semplice e senza dipendenze esterne) tra il punto di fine di una sosta e il punto di inizio della successiva; aggregati per la dashboard mensile e per l'export CSV.
- **Dashboard mensile**: query aggregata su Firestore per squadra/utente/periodo (ore, km, materiali, interventi, raggruppati per cliente); dato il volume contenuto di record (vedi limiti sotto), calcolabile on-demand lato client senza bisogno di un job di aggregazione lato backend in v1.

## Architettura backend e riuso da progetto gemello (gwatch-child-tracker)

Abbiamo già sviluppato e verificato in produzione un'app Android con Firebase + geolocalizzazione (repo `chicco83/gwatch-child-tracker`, tracciamento di un dispositivo Wear OS per un genitore). Riusiamo lo stesso stack e le stesse lezioni imparate, invece di ripartire da zero:

- **Firestore (piano Spark, gratuito)** come datastore condiviso: documenti per squadra (`teams/{teamId}`), membri, posizioni/soste, recap, clienti, articoli. Multi-utente già validato nel progetto gemello (regole basate su `parents/{uid}` → qui analogo con `teams/{teamId}/members/{uid}`), incluso il vincolo di sicurezza importante: la creazione dell'appartenenza a una squadra **non deve essere auto-approvabile dal client** (altrimenti chiunque abbia un account Google potrebbe autoinvitarsi) — l'accettazione di un invito va validata da un endpoint backend, non da una scrittura diretta Firestore.
- **Vercel Functions (piano Hobby, gratuito)** al posto delle Cloud Functions Firebase: le Cloud Functions richiedono il piano Blaze (pay-as-you-go, carta di credito), vincolo Google non aggirabile restando su Firebase Functions. Stesso codice Node.js/`firebase-admin`, cambia solo il "contenitore" di esecuzione. Verificato che i Cron Job nativi di Vercel bloccano il deploy sul piano Hobby: la pulizia programmata (retention storico, quote) va fatta con un **workflow GitHub Actions** schedulato che chiama un endpoint `/api/cleanup`, non con `vercel.json` → `crons`.
- **Mappa: OpenStreetMap via osmdroid**, non Google Maps SDK — Google Maps Platform richiede fatturazione **attiva ad ogni chiamata** (non basta crearla una volta), incompatibile con l'obiettivo "mai una carta collegata in modo permanente". osmdroid non richiede chiave API né fatturazione; la ricerca indirizzi usa Nominatim (OpenStreetMap), anch'esso gratuito senza chiave.
- **Notifiche push**: FCM. Per contenuti che devono attivare comportamenti anche ad app in background/uccisa (es. sveglia con nuovo recap disponibile, o promemoria squadra) usare messaggi **data-only**, non `notification`-only — lezione dal progetto gemello: un payload `notification`+`data` viene scartato dai gestori custom se non gestito esplicitamente prima del controllo su `message.notification`.
- **Auth**: Firebase Authentication con provider Google, in comune fra i membri della squadra.
- **Foto (clienti e interventi)** — decisione dell'utente del 2026-09-29: **non su Firebase Storage** (da fine 2024 nei progetti nuovi richiede il piano Blaze, con carta) ma su uno **storage a oggetti compatibile S3, Backblaze B2** (10 GB gratuiti, nessuna carta, limiti di spesa impostabili a zero). Bucket privato; l'app riceve dal backend URL firmati a scadenza per caricare e vedere le foto, dopo il controllo di appartenenza alla squadra (ruolo che prima avevano le `storage.rules`); il codice usa solo l'API S3 standard, quindi il servizio si cambia con le sole variabili d'ambiente. Chiavi degli oggetti invariate. Scritto in origine: "**Firebase Storage** (piano gratuito, non richiede Blaze), path `teams/{teamId}/clients/{clientId}/photos/{photoId}.jpg`; upload solo di immagini già ridimensionate/compresse lato client per restare ampiamente sotto i limiti gratuiti (vedi sotto).

### Account e isolamento dal progetto gemello (decisione del 2026-09-24)

- **Firebase**: stesso account Google del family tracker, ma **progetto Firebase nuovo e separato**. Le quote Spark (letture/scritture Firestore, storage, ecc.) sono per progetto, quindi ogni app mantiene le proprie quote intere; inoltre regole di sicurezza e utenti Auth restano isolati — un errore nelle regole di Plumber Diary non può esporre le posizioni del family tracker. Il limite di progetti gratuiti per account (circa 5–10) lascia margine.
- **Vercel**: **account nuovo**, separato da quello del family tracker. Motivo principale: se un account venisse sospeso, il backend del family tracker (SOS, geofence, chat) non ne sarebbe coinvolto.
- **Rischio accettato consapevolmente dall'utente**: il piano Vercel Hobby è riservato a uso personale non commerciale, e Plumber Diary, in quanto strumento di lavoro per ore fatturabili, rientra nella definizione di uso commerciale dei termini Vercel. Valutate e scartate per ora le alternative (Firebase Blaze con Cloud Functions, che richiede una carta; Vercel Pro, circa 20 $/mese). Se in futuro l'account venisse sospeso o si volesse mettersi in regola, la migrazione più diretta è verso Cloud Functions su Blaze: gli endpoint in `backend/api/` usano già `firebase-admin` e si spostano quasi uno a uno.

### Limiti del piano gratuito e margini per un piccolo team

Numeri di riferimento del piano Firebase **Spark** (gratuito, nessuna carta) e Vercel **Hobby** (gratuito), verificati come vincoli reali nel progetto gemello:

| Risorsa | Limite piano gratuito | Stima d'uso Plumber Diary (squadra di 6–8 tecnici) | Margine |
|---|---|---|---|
| Firestore — letture | 50.000/giorno | Mappa squadra via listener realtime (non conta come lettura ripetuta per ogni frame, solo su cambiamento) + apertura recap/clienti: stima poche migliaia/giorno anche con uso intenso | ampio |
| Firestore — scritture | 20.000/giorno | Sampling posizione adattivo (come nel progetto gemello: intervallo lungo da fermi, breve in movimento) — stimando ~100–200 scritture/dispositivo/giorno → 800–1.600/giorno totali per 8 utenti | ampio |
| Firestore — storage | 1 GiB | Posizioni + recap + anagrafica testuale: trascurabile (KB per record) anche con retention di 12 mesi | ampio |
| Firestore — rete in uscita | 10 GiB/mese | Trascurabile per soli dati testuali/JSON | ampio |
| Foto su Backblaze B2 — spazio (fino alla 1.12.0: Firebase Storage, 5 GB) | 10 GB | Copia **display** (compressa, ~300–500 KB) conservata stabilmente: ~10.000–15.000 foto prima di avvicinarsi al limite. Copia **original** (alta risoluzione, ~3–8 MB) presente solo temporaneamente, dall'upload fino a mail inviata + periodo di grazia — non si accumula nel tempo | ampio sulla copia stabile; la copia temporanea richiede solo che il job di pulizia post-invio funzioni |
| Foto su Backblaze B2 — download e operazioni | download gratuito fino a 3 volte lo spazio occupato al mese; 2.500 letture/giorno gratuite (ogni miniatura scaricata, ogni foto allegata al recap) — le miniature sono in cache sul telefono per chiave, quindi non si riscaricano (fino alla 1.12.0: Firebase Storage, 1 GB/giorno) | Apertura foto/schede cliente (versione display) + invio mail con originali allegati: una giornata con molte foto ad alta risoluzione (es. 20 foto × 5 MB = 100 MB) resta comunque ampiamente sotto soglia | ampio, da ricontrollare solo con volumi molto alti di foto/giorno |
| Vercel Hobby — funzioni | 100 GB-ore/mese, timeout consigliato ≤30s per funzione | Endpoint leggeri (accetta posizione, genera/invia PDF, gestisci inviti squadra): ben sotto soglia | ampio |
| Vercel Hobby — banda | 100 GB/mese | Solo chiamate API leggere, PDF generati e inviati via email (non serviti come file statici pesanti) | ampio |
| GitHub Actions | 2.000 minuti/mese gratuiti (repo privata) / illimitato su repo pubblica | Un cron giornaliero di pulizia dura secondi | ampissimo |

**Conclusione pratica**: lo stesso stack a costo zero del progetto gemello (Firestore Spark + Vercel Hobby + GitHub Actions + osmdroid, niente Cloud Functions/Google Maps che richiederebbero Blaze/fatturazione) regge comodamente una squadra di qualche decina di tecnici senza avvicinarsi ai limiti gratuiti, **a patto di**: generare sempre la copia compressa per l'uso stabile in app, cancellare l'originale in alta risoluzione dopo l'invio della mail (o dopo il periodo di grazia), mantenere il sampling di posizione adattivo (non un GPS always-on ad alta frequenza), e tenere una guardia di quota giornaliera per dispositivo come già fatto nel progetto gemello (misura di sicurezza contro bug/loop, non perché ci si avvicini davvero al limite). Il collo di bottiglia più probabile a lungo termine resta lo storage foto (10 GB su Backblaze B2 dalla 1.13.0; erano 5 GB su Firebase Storage) se il job di pulizia degli originali dovesse fallire silenziosamente — motivo in più per farlo passare dallo stesso meccanismo GitHub Actions già verificato affidabile nel progetto gemello, non da un fire-and-forget lato client.

## Stato implementazione (aggiornato al 2026-09-29, v1.12.0)

- **`app/`**: tutte le schermate implementate: Login, Selezione squadra, Home, Recap, Dettaglio, Anagrafica clienti, Scheda cliente, Squadra, Opzioni, Storico (con i recap dei colleghi), Dashboard, Mandatino (con firma del cliente), Conferma in tempo reale. Presenti anche il tracciamento in background con le regole di sede/pause/soglia/transiti, la notifica "Sei da…?", il recap serale programmato, le foto a doppia risoluzione, il PDF del mandatino con firma saltabile e i km.
  - **Non ancora compilato né provato su un telefono**: in questo ambiente non c'è l'SDK Android. La 1.7.0 (Qwen) conteneva numerosi errori di compilazione, corretti nella 1.8.0 con una revisione riga per riga; il primo build in Android Studio potrebbe comunque segnalare dettagli residui (versioni delle librerie, API marcate sperimentali).
  - Mancano `app/google-services.json` e l'URL reale del backend in `data/BackendConfig.kt` (vedi `SETUP.md`).
- **`backend/`**: endpoint Vercel (`create-team`, `create-invite`, `accept-invite`, `my-teams`, `photo-upload-urls`, `photo-view-urls`, `send-recap-email`, `send-mandatino`, `cleanup`), Firestore rules e indici, cron GitHub Actions. Le Storage rules sono state rimosse nella 1.13.0 (foto su Backblaze B2). Serve anche l'account Backblaze B2 (`SETUP.md`, passo 1-bis). **Non deployato**: servono il progetto Firebase e l'account Vercel nuovi (decisione del 2026-09-24).
- **Scelte di funzionamento introdotte nella 1.8.0** (modificabili se non corrispondono all'uso reale):
  - una sosta sotto i 5 minuti è considerata un transito e non viene salvata;
  - una sosta rimasta aperta senza fix per oltre 30 minuti (tracciamento interrotto) viene chiusa all'ultima posizione nota;
  - la notifica "Sei da…?" parte al massimo una volta per sosta;
  - le opzioni modificate valgono per il tracciamento entro 10 minuti;
  - la mail di recap parte alla conferma del recap (vedi sopra);
  - i recap dei colleghi sono in sola lettura e mostrano solo i giorni che il collega ha confermato.
- **Natura del mandatino** (chiarimento dell'utente del 2026-09-29): si compila **sul posto a fine intervento** ed è l'**accettazione da parte del cliente dell'addebito** di un certo numero di ore. Quindi non dipende dal recap serale, che i tecnici completano dopo, a casa. Conseguenze nel codice:
  - periodo predefinito "Oggi" (restano anche mese e ultimi 30 giorni); nel PDF compare la data;
  - le soste ancora in corso sono conteggiate fino ad ora (o fino all'ultimo fix, se il tracciamento risulta fermo da oltre 30 minuti);
  - sono incluse anche le soste non ancora associate a un cliente ma che si trovano in una posizione nota di questo cliente; prima dell'invio la finestra le segnala perché il tecnico le verifichi;
  - casella "Includi le ore dei colleghi" (default OFF): aggiunge le ore degli altri membri presso lo stesso cliente con le stesse regole, **a prescindere dal loro recap**. Il PDF aggiunge la colonna "Tecnico" e il riepilogo delle ore per tecnico.
- **Correzioni della review del 2026-09-29 (v1.12.0)**, con effetti sul funzionamento:
  - inviti: il codice mostrato e copiato è completo (`<squadra>.<codice>`) e basta per unirsi; elenco "Le tue squadre" per rientrare senza nuovo invito;
  - dal Dettaglio, "Mandatino" salva prima la sosta (cliente incluso), poi apre il mandatino;
  - alla conferma del mandatino (invio o condivisione) le proprie soste senza cliente incluse per posizione vengono associate al cliente; l'email corretta sul posto viene salvata in anagrafica;
  - gli endpoint email spediscono solo a indirizzi salvati sul server (email del cliente, email amministrazione nelle Opzioni), con quote basse (50 mandatini e 20 recap al giorno per utente);
  - tracciamento: i fix GPS imprecisi non spezzano più le soste e uno spostamento va confermato da due fix consecutivi; dopo un riavvio del telefono il tracciamento riparte solo se era acceso;
  - la notifica "Sei da…?" conferma la sosta a cui si riferiva, non quella aperta al momento del tocco;
  - Dashboard: "Ore presso clienti" (escluse sede, pause e soste senza cliente) e km calcolati giorno per giorno;
  - opzione rinominata `shareOwnLocationEnabled` ("condividi la mia posizione"); spegnendola i colleghi vedono il tecnico offline;
  - aggiunti il Gradle wrapper (Gradle 8.9) e i primi test JVM (`StopClustererTest`, 5 test passati).
- **Decisioni prese dall'utente il 2026-09-29** (dopo la review):
  - **Foto su un altro servizio**, non Firebase Storage: implementato nella 1.13.0 con Backblaze B2 (vedi sopra e `SETUP.md`, passo 1-bis);
  - **Licenza iText (AGPL)**: resta così per ora (uso interno, sorgenti disponibili); se l'app venisse distribuita a terzi, valutare `android.graphics.pdf.PdfDocument` di Android;
  - **Quote giornaliere degli invii email** (50 mandatini, 20 recap per utente): restano; il controllo costa una lettura e una scrittura Firestore per invio, nessun effetto misurabile sulle prestazioni.
- **Non ancora iniziati**:
  - integrazione reale con Google Calendar (oggi il promemoria è solo un testo salvato sulla sosta);
  - export CSV;
  - push FCM lato server (token non registrato).

## Mockup

Mockup interattivo delle schermate (Home, Recap serale, Dettaglio posizione, Anagrafica cliente con foto e articoli, Mandatino — conferma invio e firma del cliente, Squadra — mappa live colleghi, Opzioni, Storico recap, Notifica conferma cliente in tempo reale, Dashboard mensile): https://claude.ai/artifact/4tyABGusg7BPyEaJKwx92U
