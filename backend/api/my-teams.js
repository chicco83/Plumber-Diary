// api/my-teams.js — v1.12.0 — 2026-09-29
//
// Squadre di cui l'utente autenticato è già membro. Serve dopo un logout,
// una reinstallazione o un cambio di telefono: prima l'app, non avendo più il
// teamId salvato in locale, obbligava a creare una squadra nuova o a farsi
// mandare un nuovo invito, anche al titolare della squadra.
//
// Query collection group su members.uid (indice dichiarato in
// firestore.indexes.json, fieldOverrides): passa dall'Admin SDK perché le
// regole client non concedono letture fuori dalle squadre già note.

const { db } = require('./_lib/firebase-admin');
const { requireAuthenticatedUser } = require('./_lib/auth');

module.exports = async (req, res) => {
  if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed' });

  try {
    const user = await requireAuthenticatedUser(req);
    const membersSnap = await db.collectionGroup('members').where('uid', '==', user.uid).get();

    const teams = [];
    for (const memberDoc of membersSnap.docs) {
      const teamRef = memberDoc.ref.parent.parent; // teams/{teamId}
      if (!teamRef) continue;
      const teamSnap = await teamRef.get();
      if (!teamSnap.exists) continue;
      teams.push({ teamId: teamRef.id, name: teamSnap.data().name || '' });
    }

    return res.status(200).json({ teams });
  } catch (e) {
    return res.status(e.statusCode || 500).json({ error: e.message });
  }
};
