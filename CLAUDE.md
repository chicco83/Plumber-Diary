# CLAUDE.md — Plumber Diary

Versione: 1.0.0 — 2026-09-19 22:27 UTC

## Istruzioni permanenti per Claude su questo progetto

1. **Versioning obbligatorio.** Ogni file di codice o di documentazione creato o modificato deve riportare in testa un blocco versione con numero (semver) e data/ora UTC dell'ultima revisione. Ad ogni modifica, incrementare il numero di versione e aggiungere una riga in `changelog.md`.
2. **Correzioni al codice.** Quando si corregge una parte di codice esistente, la sezione precedente va mantenuta come commento sopra la nuova, con indicata la data della sostituzione. Non fornire mai solo lo snippet corretto: restituire sempre il file completo.
3. **Commenti.** Le spiegazioni sul funzionamento vanno messe come commenti nelle rispettive sezioni del codice, non nel testo di risposta.
4. **Documenti obbligatori di progetto**, da mantenere aggiornati ad ogni modifica sostanziale:
   - `context.md` — contesto, obiettivi, vincoli, decisioni di progetto.
   - `changelog.md` — cronologia delle versioni.
   - `manual.md` — manuale utente/funzionale dell'app.
   - `CLAUDE.md` — questo file, con le regole operative.
5. **Lingua**: mantenere italiano per la documentazione funzionale e i commenti, salvo diversa indicazione.

## Riferimenti
- Mockup schermate (Artifact): vedi link condiviso in `context.md`.
