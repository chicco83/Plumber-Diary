# Context — Plumber Diary

Versione: 1.0.0 — 2026-09-19 22:27 UTC

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

## Decisioni di progetto

- **Piattaforma**: Android nativo (Kotlin, Jetpack Compose) per il miglior controllo su tracciamento in background, geofencing e consumo batteria; backend leggero per sync multi-dispositivo e invio email/PDF (non indispensabile in v1, l'app può funzionare offline-first con sync opzionale).
- **Rilevamento posizione**: `FusedLocationProviderClient` con `LocationRequest` a priorità bilanciata + significant-motion/activity recognition per ridurre consumo; clustering delle posizioni (raggio configurabile, es. 80–120 m) per formare le "soste".
- **Riconoscimento cliente**: matching per posizione nota (raggio + tolleranza GPS) su storico interventi; se il tempo di sosta supera la soglia (default 15 min) e la posizione non è nota, viene proposta come "nuovo possibile cliente" nel recap.
- **Recap serale**: notifica push all'orario configurato; schermata riepilogo editabile (orari, cliente, note, materiali, promemoria) con conferma finale.
- **Invio email recap**: job schedulato che genera il riepilogo testuale/PDF e lo invia all'indirizzo amministrazione impostato nelle opzioni (default ON).
- **Mandatino ore PDF**: generato on-demand dalla scheda cliente, sempre con step di conferma esplicito (dialog con riepilogo periodo/ore/materiali/destinatario) prima dell'invio.
- **Privacy**: retention storico posizioni configurabile (default 12 mesi), permessi Android per posizione in background richiesti in modo esplicito e progressivo (foreground prima, poi background con spiegazione).

## Mockup

Mockup interattivo delle schermate (Home, Recap serale, Dettaglio posizione, Anagrafica cliente + articoli, Conferma invio mandatino PDF, Opzioni, Storico recap): https://claude.ai/artifact/4tyABGusg7BPyEaJKwx92U
