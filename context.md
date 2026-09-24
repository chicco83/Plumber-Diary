# Context — Plumber Diary

Versione: 1.6.0 — 2026-09-24 (v1.5.0 — 2026-09-20 00:30 UTC e precedenti, vedi changelog.md)

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
9. Anagrafica cliente: campo email dedicato per l'invio del **"mandatino delle ore"** — un PDF con riepilogo ore e materiali dell'intervento/periodo, inviato solo dopo conferma esplicita dell'utente (mai automatico).
10. Anagrafica cliente: possibilità di allegare foto scelte dalla galleria del telefono (non solo fotocamera).
11. **Multiutente / squadra**: più utenti possono far parte della stessa squadra. Ogni utente vede su una mappa condivisa dove si trovano gli altri membri della squadra durante la giornata. Un'opzione (spenta di default) permette di vedere anche i recap serali già confermati dagli altri membri. **Anagrafica clienti e listino articoli sono condivisi**: un'unica fonte per tutta la squadra, non duplicati per utente.
12. Le foto (allegate a un intervento/posizione o alla scheda cliente) devono arrivare **in alta risoluzione** nella mail di recap giornaliero inviata all'amministrazione — non nella versione compressa usata per l'archiviazione/sync in app.
13. **Sede/deposito e pause escluse dal rilevamento cliente**: una posizione marcata come sede/deposito, o una fascia oraria marcata come pausa (es. pranzo), non deve mai essere proposta come "possibile nuovo cliente", anche se la sosta supera la soglia.
14. **Notifica di conferma cliente in tempo reale**: appena la soglia di permanenza viene superata, l'app può notificare subito "sei da [cliente]?" invece di aspettare solo il recap serale — resta comunque modificabile fino a sera. Attivabile/disattivabile dalle Opzioni.
15. **Rapportino d'intervento con firma cliente**: generabile dal recap, riepiloga automaticamente orari/note/materiali di un intervento in un documento firmabile su schermo dal cliente in loco. **La firma è sempre saltabile** dall'utente (pulsante "Salta" esplicito): il rapportino può essere generato/inviato anche senza firma.
16. **Calcolo km percorsi**: distanza stimata fra una sosta e la successiva, mostrata per singola posizione (recap/dettaglio) e aggregata per rimborso carburante/nota spese.
17. **Dashboard mensile**: ore totali, km percorsi, valore materiali usati e numero interventi, con ripartizione per cliente; accessibile dalle Opzioni, accanto all'export CSV (che ora include anche i km).

## Decisioni di progetto

- **Piattaforma**: Android nativo (Kotlin, Jetpack Compose) per il miglior controllo su tracciamento in background, geofencing e consumo batteria; backend leggero per sync multi-dispositivo e invio email/PDF (non indispensabile in v1, l'app può funzionare offline-first con sync opzionale).
- **Rilevamento posizione**: `FusedLocationProviderClient` con `LocationRequest` a priorità bilanciata + significant-motion/activity recognition per ridurre consumo; clustering delle posizioni (raggio configurabile, es. 80–120 m) per formare le "soste".
- **Riconoscimento cliente**: matching per posizione nota (raggio + tolleranza GPS) su storico interventi; se il tempo di sosta supera la soglia (default 15 min) e la posizione non è nota, viene proposta come "nuovo possibile cliente" nel recap.
- **Recap serale**: notifica push all'orario configurato; schermata riepilogo editabile (orari, cliente, note, materiali, promemoria) con conferma finale.
- **Invio email recap**: job schedulato che genera il riepilogo testuale/PDF e lo invia all'indirizzo amministrazione impostato nelle opzioni (default ON), allegando in **alta risoluzione** ogni foto presente sulle posizioni/interventi del giorno (vedi "Foto" sotto per come si gestiscono dimensione allegati e cancellazione dell'originale dopo l'invio).
- **Mandatino ore PDF**: generato on-demand dalla scheda cliente, sempre con step di conferma esplicito (dialog con riepilogo periodo/ore/materiali/destinatario) prima dell'invio.
- **Privacy**: retention storico posizioni configurabile (default 12 mesi), permessi Android per posizione in background richiesti in modo esplicito e progressivo (foreground prima, poi background con spiegazione).
- **Foto (cliente e/o intervento)**: selezione da galleria tramite `ActivityResultContracts.PickMultipleVisualMedia` (Photo Picker di sistema — non richiede il permesso `READ_MEDIA_IMAGES` su Android 13+). **Doppia risoluzione per ogni foto**:
  - una copia **originale in alta risoluzione**, caricata su Firebase Storage in un percorso separato (`.../photos/{photoId}/original.jpg`), usata **solo** per l'allegato nella mail di recap giornaliero e cancellata dopo l'invio riuscito (o dopo un periodo di grazia configurabile, per permettere un reinvio in caso di errore) — non resta a occupare spazio a tempo indeterminato;
  - una copia **compressa/ridimensionata** (`.../photos/{photoId}/display.jpg`, ~300–500 KB, lato lungo ~1600px), questa sì conservata stabilmente per la visualizzazione in app (scheda cliente, storico) e per la sincronizzazione fra i membri della squadra (banda/traffico contenuti).
  - L'email di recap allega/incorpora quindi sempre l'originale ad alta risoluzione quando presente; se il totale allegati supera una soglia pratica (i provider email in genere limitano un messaggio a ~20–25 MB), l'invio passa automaticamente da allegati diretti a **link di download sicuro e a scadenza** (URL firmato generato dal backend, valido pochi giorni) elencati nel corpo della mail — mai foto scartate o inviate a risoluzione ridotta senza che l'utente lo sappia.
- **Multiutente/squadra**: un utente crea una squadra e invita colleghi (link di invito / codice); anagrafica clienti e listino articoli sono collezioni condivise a livello di squadra, non per singolo utente. Le posizioni/i recap restano invece per-utente (ognuno traccia se stesso), ma sono leggibili dagli altri membri della squadra secondo le due opzioni indipendenti: "vedi posizione squadra" (mappa live, pensata per essere per-utente ma di default ragionevole ON) e "vedi recap colleghi" (default **OFF**, dato che è un dato più sensibile — orari e clienti visitati da altri — va attivato consapevolmente).
- **Sede/pause**: l'utente marca uno o più punti come "sede/deposito" (raggio configurabile, es. 100 m) e una o più fasce orarie ricorrenti come "pausa"; entrambe vengono escluse a monte dall'algoritmo di rilevamento cliente (non generano mai la proposta "nuovo possibile cliente", né la notifica in tempo reale), ma continuano a comparire nella timeline/nel recap come voce informativa ("Sede", "Pausa pranzo").
- **Notifica di conferma in tempo reale**: worker in background che, al superamento della soglia su una posizione nota, invia subito una notifica locale (non serve andare in rete: il match con lo storico posizioni è già disponibile sul device) con azioni rapide "Sì, confermo" / "Cambia" / "Non ora"; la risposta aggiorna lo stato della sosta corrente, che resta comunque riaperto e correggibile nel recap serale. Per posizioni non note (nessun cliente storico) resta il solo flusso serale, dato che non c'è nulla da confermare finché non si sceglie/crea un'anagrafica.
- **Rapportino con firma**: generato lato client a partire dai dati già presenti sulla posizione (orari, note, materiali), reso come PDF; la firma è una `Canvas`/`View` di disegno (bitmap vettoriale salvata come immagine, non serve una libreria di firma digitale complessa) allegata al PDF. Il pulsante "Salta" è sempre visibile e non bloccante: il rapportino risultante indica semplicemente "non firmato" invece di interrompere il flusso.
- **Km percorsi**: calcolati come distanza in linea retta o su rete stradale (valutare in fase di implementazione se usare OSRM pubblico, gratuito, oppure la sola distanza euclidea come stima più semplice e senza dipendenze esterne) tra il punto di fine di una sosta e il punto di inizio della successiva; aggregati per la dashboard mensile e per l'export CSV.
- **Dashboard mensile**: query aggregata su Firestore per squadra/utente/periodo (ore, km, materiali, interventi, raggruppati per cliente); dato il volume contenuto di record (vedi limiti sotto), calcolabile on-demand lato client senza bisogno di un job di aggregazione lato backend in v1.

## Architettura backend e riuso da progetto gemello (gwatch-child-tracker)

Abbiamo già sviluppato e verificato in produzione un'app Android con Firebase + geolocalizzazione (repo `chicco83/gwatch-child-tracker`, tracciamento di un dispositivo Wear OS per un genitore). Riusiamo lo stesso stack e le stesse lezioni imparate, invece di ripartire da zero:

- **Firestore (piano Spark, gratuito)** come datastore condiviso: documenti per squadra (`teams/{teamId}`), membri, posizioni/soste, recap, clienti, articoli. Multi-utente già validato nel progetto gemello (regole basate su `parents/{uid}` → qui analogo con `teams/{teamId}/members/{uid}`), incluso il vincolo di sicurezza importante: la creazione dell'appartenenza a una squadra **non deve essere auto-approvabile dal client** (altrimenti chiunque abbia un account Google potrebbe autoinvitarsi) — l'accettazione di un invito va validata da un endpoint backend, non da una scrittura diretta Firestore.
- **Vercel Functions (piano Hobby, gratuito)** al posto delle Cloud Functions Firebase: le Cloud Functions richiedono il piano Blaze (pay-as-you-go, carta di credito), vincolo Google non aggirabile restando su Firebase Functions. Stesso codice Node.js/`firebase-admin`, cambia solo il "contenitore" di esecuzione. Verificato che i Cron Job nativi di Vercel bloccano il deploy sul piano Hobby: la pulizia programmata (retention storico, quote) va fatta con un **workflow GitHub Actions** schedulato che chiama un endpoint `/api/cleanup`, non con `vercel.json` → `crons`.
- **Mappa: OpenStreetMap via osmdroid**, non Google Maps SDK — Google Maps Platform richiede fatturazione **attiva ad ogni chiamata** (non basta crearla una volta), incompatibile con l'obiettivo "mai una carta collegata in modo permanente". osmdroid non richiede chiave API né fatturazione; la ricerca indirizzi usa Nominatim (OpenStreetMap), anch'esso gratuito senza chiave.
- **Notifiche push**: FCM. Per contenuti che devono attivare comportamenti anche ad app in background/uccisa (es. sveglia con nuovo recap disponibile, o promemoria squadra) usare messaggi **data-only**, non `notification`-only — lezione dal progetto gemello: un payload `notification`+`data` viene scartato dai gestori custom se non gestito esplicitamente prima del controllo su `message.notification`.
- **Auth**: Firebase Authentication con provider Google, in comune fra i membri della squadra.
- **Foto cliente**: **Firebase Storage** (piano gratuito, non richiede Blaze), path `teams/{teamId}/clients/{clientId}/photos/{photoId}.jpg`; upload solo di immagini già ridimensionate/compresse lato client per restare ampiamente sotto i limiti gratuiti (vedi sotto).

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
| Firebase Storage — spazio | 5 GB | Copia **display** (compressa, ~300–500 KB) conservata stabilmente: ~10.000–15.000 foto prima di avvicinarsi al limite. Copia **original** (alta risoluzione, ~3–8 MB) presente solo temporaneamente, dall'upload fino a mail inviata + periodo di grazia — non si accumula nel tempo | ampio sulla copia stabile; la copia temporanea richiede solo che il job di pulizia post-invio funzioni |
| Firebase Storage — download | 1 GB/giorno | Apertura foto/schede cliente (versione display) + invio mail con originali allegati: una giornata con molte foto ad alta risoluzione (es. 20 foto × 5 MB = 100 MB) resta comunque ampiamente sotto soglia | ampio, da ricontrollare solo con volumi molto alti di foto/giorno |
| Vercel Hobby — funzioni | 100 GB-ore/mese, timeout consigliato ≤30s per funzione | Endpoint leggeri (accetta posizione, genera/invia PDF, gestisci inviti squadra): ben sotto soglia | ampio |
| Vercel Hobby — banda | 100 GB/mese | Solo chiamate API leggere, PDF generati e inviati via email (non serviti come file statici pesanti) | ampio |
| GitHub Actions | 2.000 minuti/mese gratuiti (repo privata) / illimitato su repo pubblica | Un cron giornaliero di pulizia dura secondi | ampissimo |

**Conclusione pratica**: lo stesso stack a costo zero del progetto gemello (Firestore Spark + Vercel Hobby + GitHub Actions + osmdroid, niente Cloud Functions/Google Maps che richiederebbero Blaze/fatturazione) regge comodamente una squadra di qualche decina di tecnici senza avvicinarsi ai limiti gratuiti, **a patto di**: generare sempre la copia compressa per l'uso stabile in app, cancellare l'originale in alta risoluzione dopo l'invio della mail (o dopo il periodo di grazia), mantenere il sampling di posizione adattivo (non un GPS always-on ad alta frequenza), e tenere una guardia di quota giornaliera per dispositivo come già fatto nel progetto gemello (misura di sicurezza contro bug/loop, non perché ci si avvicini davvero al limite). Il collo di bottiglia più probabile a lungo termine resta lo storage foto (5 GB) se il job di pulizia degli originali dovesse fallire silenziosamente — motivo in più per farlo passare dallo stesso meccanismo GitHub Actions già verificato affidabile nel progetto gemello, non da un fire-and-forget lato client.

## Stato implementazione (aggiornato al 2026-09-20)

- **`app/`**: scaffolding Kotlin/Jetpack Compose creato. Implementati con
  logica reale: modelli dati (`data/model/`), percorsi Firestore
  (`data/FirestorePaths.kt`), repository (`data/repository/`), clustering
  soste e riconoscimento cliente (`location/StopClusterer.kt`,
  `location/ClientMatcher.kt`), esclusione sede/pause
  (`location/DepotAndBreakFilter.kt`), foreground service di tracciamento
  con sampling adattivo (`location/LocationTrackingService.kt`), notifica di
  conferma in tempo reale (`notification/RealtimeConfirmNotifier.kt`), foto
  a doppia risoluzione (`photo/PhotoUploader.kt`), generatori PDF mandatino
  e rapportino (`pdf/`), calcolo km (`recap/DailyDistanceCalculator.kt`).
  Navigazione Compose con una route per schermata del mockup: i layout
  dettagliati sono ancora placeholder (`TODO` con riferimento al file
  `.dc.html` corrispondente). **Non compilato/testato**: mancano
  `google-services.json` di un vero progetto Firebase e le icone launcher
  (vedi `app/README.md` per i passaggi manuali richiesti — stesso limite
  incontrato all'avvio del progetto gemello gwatch-child-tracker, nessun SDK
  Android disponibile in questo ambiente).
- **`backend/`**: endpoint Vercel Functions scritti (`create-team`,
  `create-invite`, `accept-invite`, `send-recap-email`, `send-mandatino`,
  `cleanup`), Firestore rules e indici, workflow GitHub Actions per il cron
  di pulizia. **Non deployato**: richiede un vero progetto Firebase e le
  variabili d'ambiente elencate in `backend/README.md`.
- **Login Google e selezione/creazione squadra**: scritti
  (`auth/AuthRepository.kt`, `ui/auth/LoginScreen.kt`,
  `ui/auth/TeamSelectionScreen.kt`, `session/SessionStore.kt`,
  `data/BackendClient.kt`); `PlumberDiaryNavHost` apre su Login → (se
  nessuna squadra salvata) Selezione/Creazione squadra → Home.
  **In attesa del progetto Firebase reale** per essere testati: l'utente ha
  scelto di fornire una chiave service account (generata dalla Console
  Firebase) per farmi creare/configurare il progetto via API — stesso
  procedimento già usato nel progetto gemello — invece di fare i passaggi a
  mano in Console. Fino a quel momento restano scritti ma non verificabili.
- Non ancora iniziati: ViewModel che colleghino le altre schermate ai
  repository, UI dettagliata pixel-per-pixel rispetto al mockup,
  integrazione reale Google Calendar (dipendenza dichiarata, wiring OAuth
  non scritto).

## Mockup

Mockup interattivo delle schermate (Home, Recap serale, Dettaglio posizione, Anagrafica cliente con foto e articoli, Conferma invio mandatino PDF, Squadra — mappa live colleghi, Opzioni, Storico recap, Notifica conferma cliente in tempo reale, Rapportino con firma cliente saltabile, Dashboard mensile): https://claude.ai/artifact/4tyABGusg7BPyEaJKwx92U
