// api/photo-upload-urls.js — v1.13.0 — 2026-09-29
//
// URL firmati (validi 15 minuti) per caricare foto nello storage a oggetti
// (vedi _lib/objectStore.js). Sostituisce le regole di scrittura di
// storage.rules (Firebase Storage abbandonato il 2026-09-29):
//  - solo membri della squadra;
//  - foto di una sosta: solo sotto il PROPRIO uid (nessuno scrive nelle
//    soste di un collega);
//  - foto della scheda cliente: il cliente deve esistere; solo copia display;
//  - solo immagini (Content-Type image/*), massimo 8 foto per richiesta.
// La dimensione massima (25 MB) non si può imporre con un URL PUT firmato:
// gli oggetti oltre soglia vengono cancellati da cleanup.js.
//
// Richiesta: { teamId, uploads: [{ key, contentType }] }
// Risposta:  { urls: [{ key, url }] }

const { db } = require('./_lib/firebase-admin');
const { requireAuthenticatedUser } = require('./_lib/auth');
const { checkAndIncrementQuota } = require('./_lib/quota');
const { requireSafeIds } = require('./_lib/ids');
const { parsePhotoKey } = require('./_lib/photoKeys');
const { presignPut } = require('./_lib/objectStore');

const MAX_PER_REQUEST = 8; // 4 foto × (originale + display)
const DAILY_UPLOAD_LIMIT = 300;

module.exports = async (req, res) => {
  if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed' });

  try {
    const user = await requireAuthenticatedUser(req);
    const { teamId, uploads } = req.body || {};
    requireSafeIds({ teamId });
    if (!Array.isArray(uploads) || uploads.length === 0 || uploads.length > MAX_PER_REQUEST) {
      return res.status(400).json({ error: `Da 1 a ${MAX_PER_REQUEST} caricamenti per richiesta` });
    }

    const memberSnap = await db.doc(`teams/${teamId}/members/${user.uid}`).get();
    if (!memberSnap.exists) return res.status(403).json({ error: 'Non sei membro di questa squadra' });
    await checkAndIncrementQuota(`photo-upload_${user.uid}`, DAILY_UPLOAD_LIMIT);

    const checkedClients = new Set();
    const urls = [];
    for (const u of uploads) {
      const parsed = parsePhotoKey(u && u.key);
      const contentType = String((u && u.contentType) || '');
      if (!parsed || parsed.teamId !== teamId) return res.status(400).json({ error: 'Percorso foto non valido' });
      if (!/^image\/[A-Za-z0-9.+-]+$/.test(contentType)) return res.status(400).json({ error: 'Sono ammesse solo immagini' });

      if (parsed.kind === 'stop' && parsed.uid !== user.uid) {
        return res.status(403).json({ error: 'Puoi caricare foto solo sulle tue soste' });
      }
      if (parsed.kind === 'client' && !checkedClients.has(parsed.clientId)) {
        const clientSnap = await db.doc(`teams/${teamId}/clients/${parsed.clientId}`).get();
        if (!clientSnap.exists) return res.status(404).json({ error: 'Cliente non trovato' });
        checkedClients.add(parsed.clientId);
      }
      urls.push({ key: u.key, url: await presignPut(u.key, contentType) });
    }

    return res.status(200).json({ urls });
  } catch (e) {
    return res.status(e.statusCode || 500).json({ error: e.message });
  }
};
