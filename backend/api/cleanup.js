// api/cleanup.js — v1.0.0 — 2026-09-20 00:10 UTC
//
// Pulizia programmata, chiamata una volta al giorno da un workflow GitHub
// Actions (vedi .github/workflows/cleanup-cron.yml) invece che da un Cron
// Job nativo Vercel: nel progetto gemello gwatch-child-tracker i Cron Job
// Vercel bloccavano il deploy sul piano Hobby — stessa scelta qui.
//
// Compiti:
//  1. Cancella le soste più vecchie della retention impostata per utente
//     (default 12 mesi, vedi UserSettings.positionRetentionMonths).
//  2. Cancella le copie "original" delle foto rimaste orfane oltre il
//     periodo di grazia (mail inviata con link firmati ormai scaduti, o mai
//     inviata per un errore — non deve restare per sempre, vedi context.md).
//  3. Cancella inviti scaduti/consumati.
//  4. Cancella i contatori di quota più vecchi di qualche giorno.
//
// Protetto da un token statico (stesso pattern di ha-status/cleanup nel
// progetto gemello): questo endpoint non richiede un utente Firebase, è
// chiamato da un job automatico, non da un dispositivo.

const { db, storage } = require('./_lib/firebase-admin');

const PHOTO_GRACE_PERIOD_MS = 7 * 24 * 60 * 60 * 1000; // 7 giorni oltre l'upload

module.exports = async (req, res) => {
  const token = req.headers['x-cleanup-token'];
  if (!token || token !== process.env.CLEANUP_TOKEN) {
    return res.status(401).json({ error: 'Token non valido' });
  }

  const now = Date.now();
  let stopsDeleted = 0;
  let photosDeleted = 0;
  let invitesDeleted = 0;
  let quotaDeleted = 0;

  const teamsSnap = await db.collection('teams').get();
  for (const teamDoc of teamsSnap.docs) {
    const membersSnap = await teamDoc.ref.collection('members').get();
    for (const memberDoc of membersSnap.docs) {
      const settingsSnap = await memberDoc.ref.collection('settings').doc('preferences').get();
      const retentionMonths = settingsSnap.exists
        ? settingsSnap.data().positionRetentionMonths || 12
        : 12;
      const cutoff = now - retentionMonths * 30 * 24 * 60 * 60 * 1000;

      const oldStopsSnap = await memberDoc.ref
        .collection('stops')
        .where('startedAt', '<', cutoff)
        .get();
      for (const stopDoc of oldStopsSnap.docs) {
        await stopDoc.ref.delete();
        stopsDeleted += 1;
      }
    }

    const invitesSnap = await teamDoc.ref.collection('invites').get();
    for (const inviteDoc of invitesSnap.docs) {
      const invite = inviteDoc.data();
      if (invite.consumed || invite.expiresAt < now) {
        await inviteDoc.ref.delete();
        invitesDeleted += 1;
      }
    }
  }

  // Foto "original" oltre il periodo di grazia: qualunque file sotto
  // .../photos/*/original.jpg più vecchio del periodo di grazia viene
  // rimosso, indipendentemente dall'esito dell'invio email (non deve mai
  // accumularsi indefinitamente, vedi context.md).
  const [files] = await storage.bucket().getFiles({ prefix: 'teams/' });
  for (const file of files) {
    if (!file.name.endsWith('/original.jpg')) continue;
    const [metadata] = await file.getMetadata();
    const createdAt = new Date(metadata.timeCreated).getTime();
    if (now - createdAt > PHOTO_GRACE_PERIOD_MS) {
      await file.delete().catch(() => {});
      photosDeleted += 1;
    }
  }

  const quotaSnap = await db.collection('quota').where('expiresAt', '<', now).get();
  for (const doc of quotaSnap.docs) {
    await doc.ref.delete();
    quotaDeleted += 1;
  }

  return res.status(200).json({ ok: true, stopsDeleted, photosDeleted, invitesDeleted, quotaDeleted });
};
