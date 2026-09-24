# SETUP — Plumber Diary (configurazioni manuali)

Versione: 1.6.0 — 2026-09-24

Tutto quello che va fatto **fuori dal codice** per portare l'app in piedi: progetto
Firebase, regole di sicurezza, deploy Vercel del backend e primo build Android.
Il codice lato app e lato backend è completo (vedi `changelog.md`); qui si tratta
solo di collegarlo ai servizi reali. Stima tempo totale: **45–60 minuti**,
dipende quasi solo dai tempi di creazione dei progetti.

> Convenzione: i comandi sono per bash/Git Bash. I percorsi presuppongono di
> lavorare nella radice del repo (`Plumber-Diary/`).

## Prerequisiti

| Cosa | Perché | Dove |
|---|---|---|
| Android Studio (con SDK 35) | build e run dell'app | developer.android.com/studio |
| JDK 17 | Gradle del progetto (`app/build.gradle.kts`) | incluso in Android Studio |
| Node.js 18+ | firebase CLI (solo per il deploy delle regole) | nodejs.org |
| Account Google + account Firebase | piano Spark, gratuito, nessuna carta | console.firebase.google.com |
| Account Vercel | piano Hobby, gratuito, nessuna carta | vercel.com |
| `firebase-tools` installato | `npm install -g firebase-tools` | — |

---

## Passo 1 — Progetto Firebase (Console)

1. **Crea il progetto**: console.firebase.google.com → *Add project* → nome a piacere
   (es. `plumber-diary`). Analytics facoltativa: puoi disattivarla.
2. **Abilita i servizi** (tutti dal menu *Build* / *Sign in with*):
   - **Authentication** → *Sign-in method* → abilita **Google**. Nel campo
     *Support email* metti la tua email (compare nel dialog di Google Sign-In).
   - **Firestore Database** → *Create database* → **Production mode** →
     posizione a piacere. Non pubblicare ancora le regole: lo fai al passo 2.
   - **Storage** → *Get started* → **Production mode**.
   - **Messaging**: non serve abilitarlo manualmente in questa fase (FCM è
     attivo di default nel progetto; le push "recap pronto" sono un prossimo step,
     oggi l'app funziona senza).
3. **Registra l'app Android**: home del progetto → icona **Android** (`</>`):
   - Package name: **`com.plumberdiary.app`** (deve coincidere esattamente con
     `applicationId` in `app/build.gradle.kts`).
   - App nickname: es. `Plumber Diary Android`.
   - **Debug signing certificate**: se non l'hai ancora generato, da Git Bash:
     ```bash
     keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android | findstr SHA1
     ```
     (su Linux/macOS: `~/.android/debug.keystore`). Copia il valore **SHA-1** nel
     campo *Debug signing certificate*. Il certificato debug è lo stesso per tutti
     i progetti, quindi questa operazione si fa una volta sola.
   - Completa → **Download `google-services.json`**.
4. **Posiziona il file**: salvalo come **`app/google-services.json`** nella radice
   della cartella `app/` (sopra a `build.gradle.kts`). Il file è nel `.gitignore`
   per design: contiene gli identificatori del tuo progetto e **non va committato**.

> ⚠️ Se in futuro rigeneri la chiave debug o passi al release signing, devi
> aggiornare l'impronta SHA-1 nella Console (altrimenti il login Google fallisce
> con `invalid_request` / `auth/invalid-credential`).

## Passo 2 — Regole e indici (firebase CLI)

Dalla cartella `backend/`:

```bash
cd backend
firebase login                # una tantum, apre il browser
firebase use <il-tuo-project-id>   # seleziona il progetto creato al passo 1
firebase deploy               # pubblica firestore.rules + indexes + storage.rules
```

- `firestore.rules`: membership come criterio di accesso; la creazione di
  `teams/{teamId}/members/{uid}` è **vietata dal client** (solo l'endpoint
  `accept-invite` con Admin SDK può crearla) — non "correggere" questa regola.
- `storage.rules` (nuovo in v1.6.0): foto clienti condivise, foto interventi
  scrivibili solo dal proprietario della sosta.
- `firestore.indexes.json`: indici semplici su `stops.startedAt`/`endedAt`
  (le query dell'app usano range+orderBy sullo stesso campo: non servono indici
  compositi; se in fase di test Firestore segnalasse un indice mancante, la
  console te lo propone con un link "Create" — cliccare e ri-deployare).

## Passo 3 — Backend Vercel

1. **Importa il repo su Vercel** (vercel.com → *Add New* → *Project* → scegli il
   repo GitHub `chicco83/Plumber-Diary`):
   - Framework Preset: **Other** (non Next.js).
   - **Root Directory: `backend`** ← fondamentale, altrimenti Vercel non trova
     le funzioni.
   - Build Command / Output Directory: lasciali vuoti (le `api/*.js` vengono
     rilevate automaticamente come Serverless Functions).
2. **Variabili d'ambiente** (Vercel → *Settings* → *Environment Variables*,
   impostale su tutti gli ambienti). Valori dal progetto Firebase:
   Console Firebase → *Impostazioni progetto* → **Account di servizio** →
   *Genera nuova chiave privata* → apre un JSON da cui prendere i campi:

   | Variabile | Valore |
   |---|---|
   | `FIREBASE_PROJECT_ID` | campo `project_id` della chiave |
   | `FIREBASE_CLIENT_EMAIL` | campo `client_email` |
   | `FIREBASE_PRIVATE_KEY` | campo `private_key` — **conserva gli `\n` letterali** (Vercel non accetta a capo reali; il codice in `_lib/firebase-admin.js` li ricodifica) |
   | `FIREBASE_STORAGE_BUCKET` | campo `storageBucket` (es. `plumber-diary.appspot.com`) |
   | `SMTP_HOST` | es. `smtp.gmail.com` |
   | `SMTP_PORT` | `587` |
   | `SMTP_USER` | la tua email Gmail completa |
   | `SMTP_PASS` | **App Password** di Gmail (non la password del conto!): account.google.com → *Sicurezza* → *Password app* |
   | `SMTP_FROM` | es. `"Plumber Diary" <la-tua-email@gmail.com>` (deve coincidere con `SMTP_USER`) |
   | `CLEANUP_TOKEN` | stringa casuale lunga (es. `openssl rand -hex 32`) — la stessi su GitHub Actions, vedi passo 6 |

3. Deploy automatico: al primo import Vercel fa il deploy; in seguito ogni push
   alla branch configurata re-deploya. Verifica che `https://<tuodominio>/api/create-team`
   risponda (con un POST non autorizzato dovrebbe dare un errore JSON di auth,
   **non** 404: significa che le funzioni sono live).

## Passo 4 — URL del backend nell'app

Se il dominio Vercel assegnato è diverso dal placeholder, aggiorna **una sola
costante**: `app/src/main/java/com/plumberdiary/app/data/BackendConfig.kt`

```kotlin
object BackendConfig {
    const val BASE_URL = "https://IL-TUO-DOMINIO.vercel.app"
}
```

Da v1.6.0 tutte le schermate (NavHost, Recap, Cliente, Squadra, Opzioni) la
leggono da qui: non cercare più stringhe `vercel.app` sparse.

## Passo 5 — Build e primo run (Android Studio)

1. Apri la cartella del repo in Android Studio → *Open* → attendi il **Gradle sync**
   (al primo sync scarica tutte le dipendenze, tra cui la nuova `coil-compose` per
   le miniature foto).
2. Seleziona un dispositivo: **serve un device/emulator con API 26+** e, per il
   tracciamento in background, un telefono reale è molto più fedele (il Play
   Services dell'emulator simula male i fix GPS continui).
3. *Run* → l'app parte su **Login**.
4. Al primo avvio concedi: posizione (foreground), poi la richiesta separata per
   il background, e le notifiche (Android 13+).

### Checklist di test end-to-end (in ordine)

- [ ] Login Google → schermata selezione squadra → **Crea squadra** (verifica su
      Console Firebase → Firestore che sia apparso `teams/{id}` con il tuo membro).
- [ ] Home → **Inizia tracciamento** → la notifica "Tracciamento posizione attivo"
      resta in alto.
- [ ] Sosta ≥ soglia (default 15 min, modificabile in Opzioni) su una posizione
      **già nota** di un cliente → arriva la notifica locale *"Sei da [cliente]?"*
      → tocca → schermata conferma → *Sì, confermo* → torna alla Home con la sosta
      associata. (*Non ora* deve silenziare la ri-notifica per quella sosta.)
- [ ] **Recap**: una card per sosta; suggerimento cliente con conferma inline;
      modifica note; *Conferma recap* salva tutto.
- [ ] **Invia recap via email** (imposta prima l'indirizzo in Opzioni → Recap
      serale): arriva la mail con le foto in alta risoluzione (se caricate).
- [ ] **Dettaglio**: correggi orari, associa/crea cliente, aggiungi materiali dal
      listino, allega una foto dalla galleria → le miniature compaiono subito.
- [ ] **Cliente** (da Dettaglio o Storico): modifica anagrafica, *Mandatino delle
      ore* → conferma esplicita → mail al destinatario con PDF allegato.
- [ ] **Squadra**: generi un codice invito; da un secondo telefono/account fai
      login e *Unisciti* (ID squadra + codice) → entrambi i marker compaiono sulla
      mappa osmdroid. (Se la mappa resta grigia: il tile server OSM a volte è lento
      al primo caricamento — attendi qualche secondo / zooma.)
- [ ] **Opzioni**: imposta sede ("usa posizione attuale") e una pausa → una sosta
      in quel raggio/orario non genera mai proposte cliente né notifiche.
- [ ] **Rapportino** (da Recap, su una sosta con cliente): firma sul canvas →
      *Genera rapportino* → si apre la share sheet con il PDF; anche *Salta la
      firma* genera il PDF (con dicitura "non firmato").
- [ ] **Storico** (giorni precedenti) e **Dashboard mensile** (ore/km/materiali/
      interventi per cliente).

## Passo 6 — Cron di pulizia (GitHub Actions)

Il workflow `.github/workflows/cleanup-cron.yml` chiama `/api/cleanup` una volta al
giorno (i Cron Job nativi di Vercel non sono disponibili sul piano Hobby). Va
abilitato con due secret su **GitHub → repo → Settings → Secrets and variables →
Actions**:

| Secret | Valore |
|---|---|
| `BACKEND_URL` | l'URL del deploy Vercel (es. `https://plumber-diary.vercel.app`) |
| `CLEANUP_TOKEN` | lo stesso valore impostato come `CLEANUP_TOKEN` su Vercel |

Senza questi secret il cron fallisce silenziosamente: le foto "original" HD non
vengono cancellate dopo l'invio del recap e, col tempo, occupano lo storage
gratuito (5 GB). È l'unico step "non visibile": nessun errore in app, si nota solo
controllando la Console Storage.

## Troubleshooting rapido

| Sintomo | Causa più probabile | Rimedio |
|---|---|---|
| Login Google: `auth/invalid-credential` / crash su `default_web_client_id` | `google-services.json` mancante, in `app/` sbagliata posizione, o SHA-1 errata | ripeti passo 1.3–1.4; rigenera l'impronta dopo ogni cambio keystore |
| Login ok ma "Crea squadra" dà errore HTTP 401/500 | env var Vercel mancanti/errate (soprattutto `FIREBASE_PRIVATE_KEY` senza gli `\n`) | verifica passo 3.2; i log sono in Vercel → *Deployments* → ultimo deploy → tab *Functions* |
| Le funzioni rispondono ma le scritture Firestore falliscono | regole non pubblicate o progetto Firebase diverso da quello di `google-services.json` | `firebase deploy` nel progetto corretto (passo 2) |
| Foto caricate ma miniature vuote in Dettaglio/Cliente | `storage.rules` non pubblicate (lettura negata) | passo 2: `firebase deploy` include ora anche le regole Storage |
| Mappa Squadra grigia/vuota | tile server OSM lento al primo avvio, o init osmdroid mancante | attendi/zooma; l'init è già in `PlumberDiaryApp.onCreate` — non rimuoverlo |
| Notifica "Sei da…" non arriva mai | soglia non superata, posizione su sede/pausa, oppure già confermata/silenziata sulla stessa sosta | controlla Opzioni (soglia, sede, pause); il campo `realtimeDismissedAt` sulla sosta blocca la ri-notifica finché la sosta resta aperta |
| Recap email: "Invio recap fallito" | SMTP non configurato o App Password scaduta | passo 3.2; le App Password Gmail si rigenerano in *Password app* |

## Prossimi step (fuori scope di stasera, già documentati nel codice)

1. **Google Calendar vero** (requisito 7): oggi il promemoria è un testo salvato
   sulla sosta (`Stop.reminderText`); l'evento calendario reale richiede OAuth
   (le dipendenze `google-api-client`/`calendar` sono già in `build.gradle.kts`).
2. **Push FCM "recap pronto"** all'orario configurato: `PlumberFcmService` gestisce
   già i payload data-only, manca il job client che schedula l'invio (WorkManager)
   e la registrazione del token lato backend.
3. **Invio recap automatico all'orario delle Opzioni**: oggi è manuale dal Recap
   (*Invia recap via email*); lo schedulato riusa lo stesso endpoint.
4. **Export CSV** della dashboard (ore/km/materiali per cliente) — requisito 17,
   lato client, nessun backend coinvolto.
5. **"Vedi recap dei colleghi"** (default OFF): la lettura è già consentita dalle
   regole; manca il selettore UI in Storico che mostra i recap degli altri membri.