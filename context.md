# Context — Plumber Diary

Versione: 1.2.0 — 2026-09-19 23:35 UTC (v1.1.0 — 2026-09-19 23:10 UTC, v1.0.0 — 2026-09-19 22:27 UTC: stesure precedenti, vedi changelog.md)

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

## Architettura backend e riuso da progetto gemello (gwatch-child-tracker)

Abbiamo già sviluppato e verificato in produzione un'app Android con Firebase + geolocalizzazione (repo `chicco83/gwatch-child-tracker`, tracciamento di un dispositivo Wear OS per un genitore). Riusiamo lo stesso stack e le stesse lezioni imparate, invece di ripartire da zero:

- **Firestore (piano Spark, gratuito)** come datastore condiviso: documenti per squadra (`teams/{teamId}`), membri, posizioni/soste, recap, clienti, articoli. Multi-utente già validato nel progetto gemello (regole basate su `parents/{uid}` → qui analogo con `teams/{teamId}/members/{uid}`), incluso il vincolo di sicurezza importante: la creazione dell'appartenenza a una squadra **non deve essere auto-approvabile dal client** (altrimenti chiunque abbia un account Google potrebbe autoinvitarsi) — l'accettazione di un invito va validata da un endpoint backend, non da una scrittura diretta Firestore.
- **Vercel Functions (piano Hobby, gratuito)** al posto delle Cloud Functions Firebase: le Cloud Functions richiedono il piano Blaze (pay-as-you-go, carta di credito), vincolo Google non aggirabile restando su Firebase Functions. Stesso codice Node.js/`firebase-admin`, cambia solo il "contenitore" di esecuzione. Verificato che i Cron Job nativi di Vercel bloccano il deploy sul piano Hobby: la pulizia programmata (retention storico, quote) va fatta con un **workflow GitHub Actions** schedulato che chiama un endpoint `/api/cleanup`, non con `vercel.json` → `crons`.
- **Mappa: OpenStreetMap via osmdroid**, non Google Maps SDK — Google Maps Platform richiede fatturazione **attiva ad ogni chiamata** (non basta crearla una volta), incompatibile con l'obiettivo "mai una carta collegata in modo permanente". osmdroid non richiede chiave API né fatturazione; la ricerca indirizzi usa Nominatim (OpenStreetMap), anch'esso gratuito senza chiave.
- **Notifiche push**: FCM. Per contenuti che devono attivare comportamenti anche ad app in background/uccisa (es. sveglia con nuovo recap disponibile, o promemoria squadra) usare messaggi **data-only**, non `notification`-only — lezione dal progetto gemello: un payload `notification`+`data` viene scartato dai gestori custom se non gestito esplicitamente prima del controllo su `message.notification`.
- **Auth**: Firebase Authentication con provider Google, in comune fra i membri della squadra.
- **Foto cliente**: **Firebase Storage** (piano gratuito, non richiede Blaze), path `teams/{teamId}/clients/{clientId}/photos/{photoId}.jpg`; upload solo di immagini già ridimensionate/compresse lato client per restare ampiamente sotto i limiti gratuiti (vedi sotto).

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

## Mockup

Mockup interattivo delle schermate (Home, Recap serale, Dettaglio posizione, Anagrafica cliente con foto e articoli, Conferma invio mandatino PDF, Squadra — mappa live colleghi, Opzioni, Storico recap): https://claude.ai/artifact/4tyABGusg7BPyEaJKwx92U
