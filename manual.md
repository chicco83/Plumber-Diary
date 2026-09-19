# Manuale funzionale — Plumber Diary

Versione: 1.4.0 — 2026-09-20 00:10 UTC (v1.3.0 — 2026-09-19 23:55 UTC, v1.2.0 — 2026-09-19 23:35 UTC, v1.1.0 — 2026-09-19 23:10 UTC, v1.0.0 — 2026-09-19 22:27 UTC: stesure precedenti, vedi changelog.md)

> Nota: questo manuale descrive il comportamento previsto dell'app a
> progetto completo. Lo stato di avanzamento reale dell'implementazione è
> tracciato in `context.md`, sezione "Stato implementazione".

## Cos'è

Plumber Diary è un'app Android che traccia automaticamente dove sei stato durante la giornata di lavoro, calcola quanto tempo sei rimasto in ogni posizione, e la sera ti propone un riepilogo da confermare e correggere, associando ogni sosta a un cliente.

## Flusso d'uso

1. **Durante il giorno** — schermata *Home*: l'app registra automaticamente le soste (posizione + orario inizio/fine) e calcola i km percorsi tra una sosta e l'altra. Se resti in un posto più della soglia impostata (default 15 minuti):
   - se la posizione è **già nota** da un intervento precedente, e la notifica in tempo reale è attiva, ricevi subito una notifica "sei da [cliente]?" da confermare con un tocco (resta comunque modificabile fino a sera);
   - se la posizione **non è nota**, o è la sede/deposito o rientra in una pausa impostata, non ricevi nulla in tempo reale: la trovi eventualmente nel recap serale.
2. **Recap serale** — all'orario impostato nelle Opzioni ricevi una notifica con il riepilogo del giorno: per ogni posizione vedi orario, durata, km dalla sosta precedente, cliente suggerito (se la posizione era già nota) e puoi:
   - confermare o cambiare il cliente proposto;
   - correggere manualmente gli orari di inizio/fine;
   - scrivere una nota libera sull'intervento;
   - aggiungere gli articoli/materiali usati;
   - allegare foto dell'intervento dalla galleria del telefono;
   - generare un **rapportino** con firma del cliente su schermo (la firma è sempre facoltativa: puoi saltarla e inviare comunque il rapportino);
   - creare un promemoria collegato su Google Calendar.
   Se un intervento ha foto allegate, queste vengono incluse **in alta risoluzione** nella mail di recap che va all'amministrazione — non nella versione compressa usata per la visualizzazione in app.
3. **Anagrafica cliente**: dati anagrafici (nome/ragione sociale, telefono, P.IVA/C.F., indirizzo), foto allegate dalla galleria del telefono (es. contatore, impianto, prima/dopo intervento), elenco delle posizioni note collegate, listino articoli (codice, descrizione, prezzo, quantità) usato negli interventi, ed email dedicata per l'invio del mandatino ore.
4. **Mandatino delle ore (PDF)**: dalla scheda cliente puoi generare un PDF con ore e materiali del periodo e inviarlo all'email del cliente. L'invio richiede sempre una conferma esplicita: l'app mostra un riepilogo (periodo, ore, materiali, destinatario) e invia solo dopo che premi "Conferma e invia".
5. **Storico**: nel menu Opzioni puoi rivedere i recap dei giorni precedenti ed effettuare correzioni massive (es. riassegnare un cliente a più giornate, correggere il nome di una posizione ricorrente).
6. **Squadra**: se fai parte di una squadra, nella scheda *Squadra* vedi su una mappa condivisa dove si trovano in questo momento gli altri membri (se hanno l'opzione di visibilità attiva) e il loro stato (attivo/in pausa). Anagrafica clienti e listino articoli sono condivisi da tutta la squadra: un cliente o un articolo aggiunto da un collega è visibile a tutti.
7. **Dashboard mensile**: dalle Opzioni, riepilogo del mese con ore totali, km percorsi, valore materiali e numero interventi, ripartiti per cliente.

## Opzioni disponibili

- **Orario invio recap**: a che ora ricevere la notifica di riepilogo serale.
- **Soglia rilevamento cliente**: minuti di permanenza oltre i quali una posizione è proposta come nuovo possibile cliente (default 15 minuti).
- **Notifica conferma in tempo reale**: attiva/disattiva la notifica immediata "sei da [cliente]?" appena la soglia viene superata su una posizione nota.
- **Invio recap via email**: attivo di default, inoltra il riepilogo giornaliero a un indirizzo di amministrazione configurabile, con eventuali foto degli interventi allegate in alta risoluzione (se troppe/pesanti per un unico allegato, arrivano come link di download sicuro nel corpo della mail).
- **Google Calendar**: collegamento dell'account per creare promemoria dal recap.
- **Tracciamento posizione**: attivazione/disattivazione del tracciamento in background.
- **Dashboard mensile**: ore, km, materiali e interventi per cliente.
- **Recap giorni precedenti**: accesso allo storico per correzioni massive.
- **Esporta dati (CSV)**: esportazione ore, km e materiali per cliente/periodo.
- **Conserva storico posizioni**: periodo di conservazione dati (default 12 mesi).
- **Squadra**: nome/gestione della squadra, "vedi posizione della squadra" (mappa live dei colleghi), "vedi recap dei colleghi" (spento di default: consultazione dei riepiloghi serali già confermati da altri membri), invito di un nuovo collega tramite link.
- **Sede/deposito e pause**: imposta una posizione come sede/deposito e una o più fasce orarie come pausa; entrambe non vengono mai proposte come "nuovo cliente".

## Note privacy

Il tracciamento posizione richiede il permesso Android di localizzazione (anche in background). I dati di posizione sono usati solo per calcolare le soste e riconoscere i clienti; lo storico è conservato per il periodo impostato nelle Opzioni e può essere cancellato in qualsiasi momento. In una squadra, la propria posizione live e (se attivato) i propri recap sono visibili agli altri membri della stessa squadra secondo le opzioni sopra; anagrafica clienti e articoli sono invece sempre condivisi con tutta la squadra, per definizione (non è un dato personale del singolo tecnico).
