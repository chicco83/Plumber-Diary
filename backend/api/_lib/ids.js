// _lib/ids.js — v1.12.0 — 2026-09-29
//
// Validazione degli identificativi ricevuti dal client prima di usarli in un
// percorso Firestore/Storage. Prima teamId/inviteCode/clientId finivano nel
// percorso senza controlli: un valore con "/" puntava a un documento diverso
// da quello atteso.

// Id documento Firestore generati dall'SDK (20 caratteri alfanumerici) o
// comunque id "semplici": lettere, cifre, trattino e underscore, niente "/".
const SAFE_ID = /^[A-Za-z0-9_-]{1,128}$/;

function isSafeId(value) {
  return typeof value === 'string' && SAFE_ID.test(value);
}

function requireSafeIds(fields) {
  for (const [name, value] of Object.entries(fields)) {
    if (!isSafeId(value)) {
      const err = new Error(`${name} non valido`);
      err.statusCode = 400;
      throw err;
    }
  }
}

/**
 * v1.12.0 — 2026-09-29: codice invito "completo" = "<teamId>.<codice>".
 * Prima l'app mostrava e copiava solo il codice, ma per unirsi servivano
 * anche l'ID squadra, che non compariva in nessuna schermata: il collega
 * invitato non poteva entrare. Ora il codice condiviso contiene entrambi.
 */
function composeInvite(teamId, code) {
  return `${teamId}.${code}`;
}

function parseInvite(fullCode) {
  const parts = String(fullCode || '').trim().split('.');
  if (parts.length !== 2) return null;
  const [teamId, code] = parts;
  if (!isSafeId(teamId) || !isSafeId(code)) return null;
  return { teamId, code };
}

module.exports = { isSafeId, requireSafeIds, composeInvite, parseInvite };
