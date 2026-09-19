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
