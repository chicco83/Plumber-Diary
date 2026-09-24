// BackendConfig.kt — v1.6.0 — 2026-09-24
//
// URL base del deploy Vercel del backend (vedi backend/README.md). Centralizzato
// il 2026-09-24: prima era duplicato in PlumberDiaryNavHost + 4 schermate
// (Recap, Cliente, Squadra, Opzioni), quindi un cambio di dominio avrebbe
// richiesto 5 modifiche. Una volta fatto il deploy reale, aggiornare SOLO
// questa costante — vedi SETUP.md, passo 4.
package com.plumberdiary.app.data

object BackendConfig {
    const val BASE_URL = "https://plumber-diary.vercel.app"
}