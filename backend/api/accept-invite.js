// api/accept-invite.js — v1.0.0 — 2026-09-20 00:10 UTC
//
// UNICO punto in cui un utente entra a far parte di una squadra (vedi
// firestore.rules: teams/{teamId}/members/{uid} non è mai creabile dal
// client). Verifica che l'invito esista, non sia scaduto e non sia già
// stato consumato, prima di creare il documento membro.

const { db } = require('./_lib/firebase-admin');
const { requireAuthenticatedUser } = require('./_lib/auth');

module.exports = async (req, res) => {
  if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed' });

  try {
    const user = await requireAuthenticatedUser(req);
    const { teamId, inviteCode } = req.body || {};
    if (!teamId || !inviteCode) {
      return res.status(400).json({ error: 'teamId e inviteCode sono obbligatori' });
    }

    const inviteRef = db.doc(`teams/${teamId}/invites/${inviteCode}`);

    await db.runTransaction(async (tx) => {
      const inviteSnap = await tx.get(inviteRef);
      if (!inviteSnap.exists) throw httpError(404, 'Invito non trovato');
      const invite = inviteSnap.data();
      if (invite.consumed) throw httpError(410, 'Invito già utilizzato');
      if (invite.expiresAt < Date.now()) throw httpError(410, 'Invito scaduto');

      tx.set(db.doc(`teams/${teamId}/members/${user.uid}`), {
        uid: user.uid,
        displayName: user.name || '',
        email: user.email || '',
        colorHex: randomColor(),
        joinedAt: Date.now(),
      });
      tx.update(inviteRef, { consumed: true, consumedBy: user.uid, consumedAt: Date.now() });
    });

    return res.status(200).json({ teamId });
  } catch (e) {
    return res.status(e.statusCode || 500).json({ error: e.message });
  }
};

function httpError(statusCode, message) {
  const err = new Error(message);
  err.statusCode = statusCode;
  return err;
}

function randomColor() {
  const palette = ['#0F766E', '#D97706', '#1D4ED8', '#9333EA', '#DC2626'];
  return palette[Math.floor(Math.random() * palette.length)];
}
