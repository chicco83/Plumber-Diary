# Manuale funzionale — Plumber Diary

Versione: 1.0.0 — 2026-09-19 22:27 UTC

## Cos'è

Plumber Diary è un'app Android che traccia automaticamente dove sei stato durante la giornata di lavoro, calcola quanto tempo sei rimasto in ogni posizione, e la sera ti propone un riepilogo da confermare e correggere, associando ogni sosta a un cliente.

## Flusso d'uso

1. **Durante il giorno** — schermata *Home*: l'app registra automaticamente le soste (posizione + orario inizio/fine). Se resti in un posto più della soglia impostata (default 15 minuti) e la posizione non è già associata a un cliente noto, viene segnalata come "possibile nuovo cliente".
2. **Recap serale** — all'orario impostato nelle Opzioni ricevi una notifica con il riepilogo del giorno: per ogni posizione vedi orario, durata, cliente suggerito (se la posizione era già nota) e puoi:
   - confermare o cambiare il cliente proposto;
   - correggere manualmente gli orari di inizio/fine;
   - scrivere una nota libera sull'intervento;
   - aggiungere gli articoli/materiali usati;
   - creare un promemoria collegato su Google Calendar.
3. **Anagrafica cliente**: dati anagrafici (nome/ragione sociale, telefono, P.IVA/C.F., indirizzo), elenco delle posizioni note collegate, listino articoli (codice, descrizione, prezzo, quantità) usato negli interventi, ed email dedicata per l'invio del mandatino ore.
4. **Mandatino delle ore (PDF)**: dalla scheda cliente puoi generare un PDF con ore e materiali del periodo e inviarlo all'email del cliente. L'invio richiede sempre una conferma esplicita: l'app mostra un riepilogo (periodo, ore, materiali, destinatario) e invia solo dopo che premi "Conferma e invia".
5. **Storico**: nel menu Opzioni puoi rivedere i recap dei giorni precedenti ed effettuare correzioni massive (es. riassegnare un cliente a più giornate, correggere il nome di una posizione ricorrente).

## Opzioni disponibili

- **Orario invio recap**: a che ora ricevere la notifica di riepilogo serale.
- **Soglia rilevamento cliente**: minuti di permanenza oltre i quali una posizione è proposta come nuovo possibile cliente (default 15 minuti).
- **Invio recap via email**: attivo di default, inoltra il riepilogo giornaliero a un indirizzo di amministrazione configurabile.
- **Google Calendar**: collegamento dell'account per creare promemoria dal recap.
- **Tracciamento posizione**: attivazione/disattivazione del tracciamento in background.
- **Recap giorni precedenti**: accesso allo storico per correzioni massive.
- **Esporta dati (CSV)**: esportazione ore per cliente/periodo.
- **Conserva storico posizioni**: periodo di conservazione dati (default 12 mesi).

## Note privacy

Il tracciamento posizione richiede il permesso Android di localizzazione (anche in background). I dati di posizione sono usati solo per calcolare le soste e riconoscere i clienti; lo storico è conservato per il periodo impostato nelle Opzioni e può essere cancellato in qualsiasi momento.
