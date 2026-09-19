// _lib/auth.js — v1.0.0 — 2026-09-20 00:10 UTC
//
// Verifica dell'ID token Firebase del chiamante (stesso pattern di
// checkParentAuth nel progetto gemello gwatch-child-tracker): ogni endpoint
// che agisce per conto di un utente autenticato lo richiama PRIMA di
// qualunque lettura/scrittura Firestore con l'Admin SDK (che bypassa le
// Security Rules, quindi il controllo va fatto qui esplicitamente).

const { auth } = require('./firebase-admin');

async function requireAuthenticatedUser(req) {
  const header = req.headers.authorization || '';
  const match = header.match(/^Bearer (.+)$/);
  if (!match) {
    const err = new Error('Token mancante');
    err.statusCode = 401;
    throw err;
  }
  try {
    return await auth.verifyIdToken(match[1]);
  } catch (e) {
    const err = new Error('Token non valido');
    err.statusCode = 401;
    throw err;
  }
}

module.exports = { requireAuthenticatedUser };
