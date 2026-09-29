// api/create-invite.js — v1.12.0 — 2026-09-29 (v1.0.0 — 2026-09-20 00:10 UTC)
//
// Genera un codice di invito a scadenza (teams/{teamId}/invites/{code}),
// consumabile una sola volta da accept-invite.js. Solo un membro già della
// squadra può generarlo.
//
// v1.12.0 — 2026-09-29: `inviteCode` restituito all'app è ora il codice
// COMPLETO "<teamId>.<codice>" (vedi _lib/ids.js): prima era il solo codice e
// l'invitato non aveva modo di conoscere l'ID squadra richiesto per unirsi.

const crypto = require('crypto');
const { db } = require('./_lib/firebase-admin');
const { requireAuthenticatedUser } = require('./_lib/auth');
const { requireSafeIds, composeInvite } = require('./_lib/ids');

const INVITE_TTL_MS = 7 * 24 * 60 * 60 * 1000; // 7 giorni

module.exports = async (req, res) => {
  if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed' });

  try {
    const user = await requireAuthenticatedUser(req);
    const { teamId } = req.body || {};
    if (!teamId) return res.status(400).json({ error: 'teamId mancante' });
    requireSafeIds({ teamId });

    const memberSnap = await db.doc(`teams/${teamId}/members/${user.uid}`).get();
    if (!memberSnap.exists) {
      return res.status(403).json({ error: 'Non sei membro di questa squadra' });
    }

    const code = crypto.randomBytes(6).toString('hex');
    const expiresAt = Date.now() + INVITE_TTL_MS;
    await db.doc(`teams/${teamId}/invites/${code}`).set({
      createdBy: user.uid,
      createdAt: Date.now(),
      expiresAt,
      consumed: false,
    });

    // Versione precedente (v1.0.0 — 2026-09-20), sostituita il 2026-09-29:
    // return res.status(200).json({ inviteCode: code, expiresAt: Date.now() + INVITE_TTL_MS });
    return res.status(200).json({ inviteCode: composeInvite(teamId, code), expiresAt });
  } catch (e) {
    return res.status(e.statusCode || 500).json({ error: e.message });
  }
};
