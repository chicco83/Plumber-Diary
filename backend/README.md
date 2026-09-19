# backend — Plumber Diary (Vercel Functions)

Versione: 1.0.0 — 2026-09-20 00:10 UTC

Stack a costo zero, identico nell'impostazione a quello già in produzione nel
progetto gemello `chicco83/gwatch-child-tracker` (vedi `context.md` nella
radice del repo per il ragionamento completo):

- **Firestore** (Firebase piano Spark, gratuito) come datastore.
- **Vercel Functions** (piano Hobby, gratuito) al posto delle Cloud Functions
  Firebase, che richiederebbero il piano Blaze (carta di credito).
- **GitHub Actions** per il cron di pulizia giornaliera, perché i Cron Job
  nativi di Vercel bloccano il deploy sul piano Hobby.
- **SMTP generico** (nodemailer) per l'invio email — funziona anche con un
  account Gmail e una App Password dedicata, nessun servizio a pagamento.

## Endpoint

| Endpoint | Autenticazione | Scopo |
|---|---|---|
| `POST /api/create-team` | ID token Firebase | Crea una squadra e vi iscrive il creatore |
| `POST /api/create-invite` | ID token Firebase (membro) | Genera un codice di invito a scadenza |
| `POST /api/accept-invite` | ID token Firebase | Unico punto in cui un utente entra in una squadra |
| `POST /api/send-recap-email` | ID token Firebase (membro) | Requisito 6/12: recap giornaliero con foto in alta risoluzione |
| `POST /api/send-mandatino` | ID token Firebase (membro) | Requisito 9: invia il mandatino ore PDF già generato lato client |
| `POST /api/cleanup` | Token statico `x-cleanup-token` | Retention posizioni, foto originali scadute, inviti, quote |

## Variabili d'ambiente richieste (Vercel → Settings → Environment Variables)

```
FIREBASE_PROJECT_ID=
FIREBASE_CLIENT_EMAIL=
FIREBASE_PRIVATE_KEY=
FIREBASE_STORAGE_BUCKET=
SMTP_HOST=
SMTP_PORT=
SMTP_USER=
SMTP_PASS=
SMTP_FROM=
CLEANUP_TOKEN=            # stringa casuale lunga, condivisa col secret GitHub Actions
```

## Setup

1. `npm install` in questa cartella.
2. Creare il progetto Firebase e generare una chiave service account
   (Console → Impostazioni progetto → Account di servizio), da cui prendere
   `FIREBASE_PROJECT_ID` / `FIREBASE_CLIENT_EMAIL` / `FIREBASE_PRIVATE_KEY`.
3. `firebase deploy --only firestore:rules,firestore:indexes` per pubblicare
   `firestore.rules` e `firestore.indexes.json`.
4. Importare questa cartella (`backend/` come root directory) su Vercel per
   il deploy automatico ad ogni push, come nel progetto gemello.
5. Su GitHub: Settings → Secrets and variables → Actions, aggiungere
   `BACKEND_URL` (es. `https://plumber-diary.vercel.app`) e `CLEANUP_TOKEN`
   (stesso valore impostato su Vercel) per far funzionare
   `.github/workflows/cleanup-cron.yml`.

## Nota su `vercel.json`

Un solo pattern (`api/*.js`) in `functions`: nel progetto gemello, avere sia
un pattern generico che uno specifico per lo stesso file faceva fallire il
deploy silenziosamente (Vercel assegna ogni file al primo pattern che lo
matcha, lasciando quello specifico "senza nulla da matchare"). Se si
aggiungono funzioni con configurazioni diverse in futuro, evitare pattern
sovrapposti.
