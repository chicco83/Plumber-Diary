// api/photo-view-urls.js — v1.13.0 — 2026-09-29
//
// URL firmati (validi 1 ora) per vedere le miniature nell'app. Sostituisce le
// regole di lettura di storage.rules: qualunque membro della squadra vede le
// copie "display" di clienti e interventi (sync di squadra). Le copie
// "original" in alta risoluzione NON sono mai esposte all'app: le legge solo
// send-recap-email.js.
//
// Richiesta: { teamId, keys: [...] }  (massimo 50)
// Risposta:  { urls: { [key]: url } }

const { db } = require('./_lib/firebase-admin');
const { requireAuthenticatedUser } = require('./_lib/auth');
const { requireSafeIds } = require('./_lib/ids');
const { parsePhotoKey } = require('./_lib/photoKeys');
const { presignGet } = require('./_lib/objectStore');

const MAX_KEYS = 50;
const VIEW_TTL_SECONDS = 60 * 60;

module.exports = async (req, res) => {
  if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed' });

  try {
    const user = await requireAuthenticatedUser(req);
    const { teamId, keys } = req.body || {};
    requireSafeIds({ teamId });
    if (!Array.isArray(keys) || keys.length > MAX_KEYS) {
      return res.status(400).json({ error: `Massimo ${MAX_KEYS} foto per richiesta` });
    }

    const memberSnap = await db.doc(`teams/${teamId}/members/${user.uid}`).get();
    if (!memberSnap.exists) return res.status(403).json({ error: 'Non sei membro di questa squadra' });

    const urls = {};
    for (const key of keys) {
      const parsed = parsePhotoKey(key);
      if (!parsed || parsed.teamId !== teamId || parsed.variant !== 'display') continue; // ignorata, non errore
      urls[key] = await presignGet(key, VIEW_TTL_SECONDS);
    }

    return res.status(200).json({ urls, expiresInSeconds: VIEW_TTL_SECONDS });
  } catch (e) {
    return res.status(e.statusCode || 500).json({ error: e.message });
  }
};
