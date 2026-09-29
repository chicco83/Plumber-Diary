# Manuale funzionale — Plumber Diary

Versione: 1.12.0 — 2026-09-29 11:38 UTC (v1.11.0 — 2026-09-29 e precedenti, vedi changelog.md)

> Nota: questo manuale descrive il comportamento previsto dell'app a
> progetto completo. Lo stato di avanzamento reale dell'implementazione è
> tracciato in `context.md`, sezione "Stato implementazione".

## Cos'è

Plumber Diary è un'app Android che traccia automaticamente dove sei stato durante la giornata di lavoro, calcola quanto tempo sei rimasto in ogni posizione, e la sera ti propone un riepilogo da confermare e correggere, associando ogni sosta a un cliente. L'accesso avviene con il proprio account Google; al primo accesso si crea una nuova squadra o ci si unisce a una esistente con un codice di invito.

## Flusso d'uso

0. **Primo avvio** — accedi con Google, poi crea una squadra oppure unisciti a quella di un collega incollando il **codice invito** che ti ha mandato (una sola stringa, del tipo `squadra.codice`, generata da *Squadra* o *Opzioni* → *Genera codice invito*, valida 7 giorni). Se eri già in una squadra (dopo un logout, una reinstallazione o su un telefono nuovo) la trovi sotto **Le tue squadre** e ci rientri con un tocco. Poi dalla *Home* concedi i permessi richiesti uno alla volta: posizione, posizione in background (per il tracciamento continuo) e notifiche (Android 13+, senza le quali non ricevi né la conferma "Sei da…?" né il recap serale). Premi **Inizia tracciamento**: una notifica fissa indica che il tracciamento è attivo, e **Ferma tracciamento** lo interrompe chiudendo la sosta in corso. La scelta viene ricordata: dopo un riavvio del telefono il tracciamento riparte da solo solo se lo avevi lasciato acceso.
1. **Durante il giorno** — schermata *Home*: l'app registra automaticamente le soste (posizione + orario inizio/fine) e calcola i km percorsi tra una sosta e l'altra. Le fermate più brevi di 5 minuti (semafori, traffico) sono considerate spostamenti e non compaiono. Una posizione imprecisa isolata (tipica in scantinati e locali tecnici) non interrompe la sosta: lo spostamento viene registrato solo quando è confermato. Se resti in un posto più della soglia impostata (default 15 minuti):
   - se la posizione è **già nota** da un intervento precedente, e la notifica in tempo reale è attiva, ricevi subito una notifica "sei da [cliente]?" da confermare con un tocco (resta comunque modificabile fino a sera); la conferma vale per la sosta a cui si riferiva la notifica, anche se la tocchi più tardi, dopo esserti spostato;
   - se la posizione **non è nota**, o è la sede/deposito o rientra in una pausa impostata, non ricevi nulla in tempo reale: la trovi eventualmente nel recap serale.
2. **Recap serale** — all'orario impostato nelle Opzioni ricevi la notifica "Recap di oggi pronto" (può arrivare con qualche minuto di ritardo); toccandola apri il riepilogo del giorno: per ogni posizione vedi orario, durata, km dalla sosta precedente, cliente suggerito (se la posizione era già nota) e puoi:
   - confermare o cambiare il cliente proposto;
   - correggere manualmente gli orari di inizio/fine;
   - scrivere una nota libera sull'intervento;
   - aggiungere gli articoli/materiali usati;
   - allegare foto dell'intervento dalla galleria del telefono;
   - aprire il **mandatino** del cliente associato alla sosta (vedi punto 4);
   - creare un promemoria collegato su Google Calendar.
   Premendo **Conferma recap** le modifiche vengono salvate e, se l'invio via email è attivo (lo è di default), il riepilogo parte subito verso l'indirizzo dell'amministrazione impostato nelle Opzioni: per ogni sosta orario, durata, cliente (o sede/pausa), note, promemoria e materiali, più i totali del giorno. La sosta eventualmente ancora in corso viene salvata ma non segnata come confermata. Se un intervento ha foto allegate, queste vengono incluse **in alta risoluzione** — non nella versione compressa usata per la visualizzazione in app. C'è anche un pulsante per inviarlo di nuovo a mano; le foto in alta risoluzione restano disponibili per i reinvii per 7 giorni.
3. **Anagrafica cliente** (Opzioni → *Anagrafica clienti e listino articoli*, oppure *Apri scheda cliente* dal dettaglio di una sosta): dati anagrafici (nome/ragione sociale, telefono, P.IVA/C.F., indirizzo), foto allegate dalla galleria del telefono (es. contatore, impianto, prima/dopo intervento), elenco delle posizioni note collegate, listino articoli (codice, descrizione, prezzo, quantità) usato negli interventi, ed email dedicata per l'invio del mandatino ore.
4. **Mandatino delle ore (PDF)**: è il documento con cui il cliente accetta l'addebito delle ore (e dei materiali), di norma **sul posto a fine intervento**. Si apre con **Mandatino** dal dettaglio della sosta in corso (dopo averle associato il cliente: il pulsante salva la sosta prima di aprire il mandatino), dalla card del recap o dalla scheda cliente. Nella schermata scegli il periodo (*Oggi*, predefinito, oppure mese o ultimi 30 giorni): l'intervento ancora in corso viene conteggiato fino al momento attuale, senza bisogno di aver già compilato il recap. Sono incluse anche le soste non ancora associate al cliente ma che si trovano nella sua posizione: la schermata le segnala in arancione, così le verifichi; quando confermi il mandatino (invio o condivisione) le tue vengono associate al cliente, così i dati coincidono con il documento accettato. Spuntando **Includi le ore dei colleghi** vengono aggiunte, con le stesse regole, le ore degli altri membri della squadra presso lo stesso cliente, anche se non hanno ancora fatto il recap, e il PDF indica per ogni intervento il tecnico che lo ha svolto. Fai **firmare il cliente** con il dito nell'apposito riquadro e premi **Conferma e invia firmato**; la firma è facoltativa: con **Salta la firma — conferma e invia comunque** il documento parte con la dicitura "non firmato". L'invio all'email del cliente avviene solo dopo la tua conferma; se correggi l'email nella schermata, viene salvata anche nella scheda del cliente (il mandatino parte sempre e solo verso l'email in anagrafica); in alternativa puoi **condividere il PDF** con un'altra app (WhatsApp, stampa…).
5. **Storico**: nel menu Opzioni puoi rivedere i recap degli ultimi 30 giorni ed effettuare correzioni massive: selezioni i giorni, scegli il cliente e lo assegni a tutte le soste di quei giorni ancora senza cliente. Se hai attivato "Vedi i recap dei colleghi", un selettore in alto ti mostra, in sola lettura, i recap che i colleghi hanno già confermato.
6. **Squadra**: se fai parte di una squadra, nella scheda *Squadra* vedi su una mappa condivisa dove si trovano in questo momento gli altri membri (se hanno l'opzione di visibilità attiva) e il loro stato (attivo/in pausa). Anagrafica clienti e listino articoli sono condivisi da tutta la squadra: un cliente o un articolo aggiunto da un collega è visibile a tutti.
7. **Dashboard mensile**: dalle Opzioni, riepilogo del mese con ore presso clienti (escluse sede, pause e soste senza cliente), km percorsi (sommati giorno per giorno), valore materiali e numero interventi, ripartiti per cliente.

## Opzioni disponibili

- **Orario invio recap**: a che ora ricevere la notifica di riepilogo serale (vale dal salvataggio delle opzioni).
- **Soglia rilevamento cliente**: minuti di permanenza oltre i quali una posizione è proposta come nuovo possibile cliente (default 15 minuti).
- **Notifica conferma in tempo reale**: attiva/disattiva la notifica immediata "sei da [cliente]?" appena la soglia viene superata su una posizione nota.
- **Invio recap via email**: attivo di default, alla conferma del recap inoltra il riepilogo giornaliero a un indirizzo di amministrazione configurabile, con eventuali foto degli interventi allegate in alta risoluzione (se troppe/pesanti per un unico allegato, arrivano come link di download sicuro nel corpo della mail).
- **Google Calendar**: collegamento dell'account per creare promemoria dal recap.
- **Tracciamento posizione**: si avvia e si ferma dalla Home; le modifiche alle opzioni (soglia, sede, pause) valgono per il tracciamento entro una decina di minuti.
- **Anagrafica clienti e listino articoli**: elenco dei clienti condivisi dalla squadra, ricerca, creazione e accesso alla scheda.
- **Dashboard mensile**: ore, km, materiali e interventi per cliente.
- **Recap giorni precedenti**: accesso allo storico per correzioni massive.
- **Esporta dati (CSV)**: esportazione ore, km e materiali per cliente/periodo.
- **Conserva storico posizioni**: periodo di conservazione dati (default 12 mesi).
- **Squadra**: nome/gestione della squadra, "condividi la mia posizione" (se spenta, i colleghi ti vedono offline sulla mappa), "vedi recap dei colleghi" (spento di default: consultazione dei riepiloghi serali già confermati da altri membri), invito di un nuovo collega tramite codice invito (da copiare e mandargli).
- **Sede/deposito e pause**: imposta una posizione come sede/deposito e una o più fasce orarie come pausa; entrambe non vengono mai proposte come "nuovo cliente".

## Note privacy

Il tracciamento posizione richiede il permesso Android di localizzazione (anche in background). I dati di posizione sono usati solo per calcolare le soste e riconoscere i clienti; lo storico è conservato per il periodo impostato nelle Opzioni e può essere cancellato in qualsiasi momento. In una squadra, la propria posizione live e (se attivato) i propri recap sono visibili agli altri membri della stessa squadra secondo le opzioni sopra; anagrafica clienti e articoli sono invece sempre condivisi con tutta la squadra, per definizione (non è un dato personale del singolo tecnico).
