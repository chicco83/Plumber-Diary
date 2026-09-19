// api/create-invite.js — v1.0.0 — 2026-09-20 00:10 UTC
//
// Genera un codice di invito a scadenza (teams/{teamId}/invites/{code}),
// consumabile una sola volta da accept-invite.js. Solo un membro già della
// squadra può generarlo.

const crypto = require('crypto');
const { db } = require('./_lib/firebase-admin');
const { requireAuthenticatedUser } = require('./_lib/auth');

const INVITE_TTL_MS = 7 * 24 * 60 * 60 * 1000; // 7 giorni

module.exports = async (req, res) => {
  if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed' });

  try {
    const user = await requireAuthenticatedUser(req);
    const { teamId } = req.body || {};
    if (!teamId) return res.status(400).json({ error: 'teamId mancante' });

    const memberSnap = await db.doc(`teams/${teamId}/members/${user.uid}`).get();
    if (!memberSnap.exists) {
      return res.status(403).json({ error: 'Non sei membro di questa squadra' });
    }

    const code = crypto.randomBytes(6).toString('hex');
    await db.doc(`teams/${teamId}/invites/${code}`).set({
      createdBy: user.uid,
      createdAt: Date.now(),
      expiresAt: Date.now() + INVITE_TTL_MS,
      consumed: false,
    });

    return res.status(200).json({ inviteCode: code, expiresAt: Date.now() + INVITE_TTL_MS });
  } catch (e) {
    return res.status(e.statusCode || 500).json({ error: e.message });
  }
};
