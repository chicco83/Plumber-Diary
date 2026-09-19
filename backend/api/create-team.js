// api/create-team.js — v1.0.0 — 2026-09-20 00:10 UTC
//
// Crea una nuova squadra e vi iscrive automaticamente il creatore come primo
// membro. Passa dall'Admin SDK (mai una scrittura diretta del client su
// teams/{teamId}/members, vedi firestore.rules) anche per questo primo
// membro, per tenere un solo percorso di scrittura della membership.

const { db } = require('./_lib/firebase-admin');
const { requireAuthenticatedUser } = require('./_lib/auth');
const { checkAndIncrementQuota } = require('./_lib/quota');

module.exports = async (req, res) => {
  if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed' });

  try {
    const user = await requireAuthenticatedUser(req);
    await checkAndIncrementQuota(`create-team_${user.uid}`);

    const { teamName } = req.body || {};
    if (!teamName || typeof teamName !== 'string' || teamName.trim().length === 0) {
      return res.status(400).json({ error: 'teamName mancante' });
    }

    const teamRef = db.collection('teams').doc();
    await teamRef.set({
      name: teamName.trim(),
      ownerUid: user.uid,
      createdAt: Date.now(),
    });
    await teamRef.collection('members').doc(user.uid).set({
      uid: user.uid,
      displayName: user.name || '',
      email: user.email || '',
      colorHex: '#0F766E',
      joinedAt: Date.now(),
    });

    return res.status(200).json({ teamId: teamRef.id });
  } catch (e) {
    return res.status(e.statusCode || 500).json({ error: e.message });
  }
};
