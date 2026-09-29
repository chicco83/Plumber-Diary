// _lib/quota.js — v1.12.0 — 2026-09-29 (v1.0.0 — 2026-09-20 00:10 UTC)
//
// Guardia di quota giornaliera per utente/endpoint — misura di sicurezza
// contro bug/loop lato client, non perché ci si avvicini ai limiti reali del
// piano gratuito (vedi context.md, tabella limiti). Stesso pattern già
// verificato nel progetto gemello gwatch-child-tracker.
//
// v1.12.0 — 2026-09-29: limite configurabile per endpoint. Gli endpoint che
// spediscono email (send-mandatino, send-recap-email) usano un limite basso:
// con 4000 invii al giorno un account compromesso avrebbe potuto usare il
// Gmail aziendale per spam e farlo sospendere.

const { db } = require('./firebase-admin');

const DAILY_LIMIT = 4000;

// Versione precedente (v1.0.0 — 2026-09-20), sostituita il 2026-09-29:
// async function checkAndIncrementQuota(key) { ... if (count >= DAILY_LIMIT) return true; ... }
async function checkAndIncrementQuota(key, dailyLimit = DAILY_LIMIT) {
  const today = new Date().toISOString().slice(0, 10);
  const ref = db.collection('quota').doc(`${key}_${today}`);

  const exceeded = await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const count = snap.exists ? snap.data().count : 0;
    if (count >= dailyLimit) return true;
    tx.set(
      ref,
      { count: count + 1, expiresAt: Date.now() + 3 * 24 * 60 * 60 * 1000 },
      { merge: true },
    );
    return false;
  });

  if (exceeded) {
    const err = new Error('Quota giornaliera superata');
    err.statusCode = 429;
    throw err;
  }
}

module.exports = { checkAndIncrementQuota };
