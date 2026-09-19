// api/send-recap-email.js — v1.0.0 — 2026-09-20 00:10 UTC
//
// Requisito 6/12: invia il recap giornaliero all'indirizzo amministrazione
// impostato nelle Opzioni (default ON), con le foto degli interventi in
// ALTA RISOLUZIONE. Se il totale supera la soglia pratica di un provider
// email, passa da allegati diretti a link di download sicuro e a scadenza
// (vedi context.md, "Foto"). Dopo un invio riuscito, cancella le copie
// "original" delle foto: solo quella temporanea, la copia "display" resta
// intatta per l'uso in app (vedi PhotoUploader lato Android).
//
// Chiamato da un job schedulato lato client (WorkManager, all'orario
// impostato nelle Opzioni) o da un trigger manuale "invia ora".

const { db, storage } = require('./_lib/firebase-admin');
const { requireAuthenticatedUser } = require('./_lib/auth');
const { sendMail } = require('./_lib/mailer');

const MAX_ATTACHMENTS_BYTES = 20 * 1024 * 1024; // soglia pratica ~20MB, sotto i limiti tipici dei provider
const SIGNED_URL_TTL_MS = 5 * 24 * 60 * 60 * 1000; // 5 giorni

module.exports = async (req, res) => {
  if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed' });

  try {
    const user = await requireAuthenticatedUser(req);
    const { teamId, dayStartMillis, dayEndMillis, recipientEmail } = req.body || {};
    if (!teamId || !dayStartMillis || !dayEndMillis || !recipientEmail) {
      return res.status(400).json({ error: 'Parametri mancanti' });
    }

    const memberSnap = await db.doc(`teams/${teamId}/members/${user.uid}`).get();
    if (!memberSnap.exists) return res.status(403).json({ error: 'Non sei membro di questa squadra' });

    const stopsSnap = await db
      .collection(`teams/${teamId}/members/${user.uid}/stops`)
      .where('startedAt', '>=', dayStartMillis)
      .where('startedAt', '<', dayEndMillis)
      .orderBy('startedAt')
      .get();
    const stops = stopsSnap.docs.map((d) => ({ id: d.id, ...d.data() }));

    const bucket = storage.bucket();
    const photoFiles = []; // { stopId, photoId, file, sizeBytes }
    for (const stop of stops) {
      for (const photoId of stop.photoIds || []) {
        const path = `teams/${teamId}/members/${user.uid}/stops/${stop.id}/photos/${photoId}/original.jpg`;
        const file = bucket.file(path);
        const [exists] = await file.exists();
        if (!exists) continue;
        const [metadata] = await file.getMetadata();
        photoFiles.push({ stopId: stop.id, photoId, file, sizeBytes: Number(metadata.size || 0) });
      }
    }

    const totalBytes = photoFiles.reduce((sum, p) => sum + p.sizeBytes, 0);
    const useAttachments = totalBytes <= MAX_ATTACHMENTS_BYTES;

    let attachments = [];
    let linksHtml = '';
    if (useAttachments) {
      attachments = await Promise.all(
        photoFiles.map(async (p) => {
          const [buffer] = await p.file.download();
          return { filename: `${p.stopId}_${p.photoId}.jpg`, content: buffer };
        }),
      );
    } else {
      const links = await Promise.all(
        photoFiles.map(async (p) => {
          const [url] = await p.file.getSignedUrl({
            action: 'read',
            expires: Date.now() + SIGNED_URL_TTL_MS,
          });
          return `<li><a href="${url}">${p.stopId}_${p.photoId}.jpg</a></li>`;
        }),
      );
      linksHtml = `<p>Le foto in alta risoluzione superano la dimensione consentita in allegato: scaricale dai link qui sotto (validi 5 giorni).</p><ul>${links.join('')}</ul>`;
    }

    const html = renderRecapHtml(stops) + linksHtml;
    await sendMail({ to: recipientEmail, subject: `Recap giornaliero — ${new Date(dayStartMillis).toLocaleDateString('it-IT')}`, html, attachments });

    if (useAttachments) {
      // Copie "original" non più necessarie dopo l'invio riuscito (vedi
      // context.md: non si accumulano nel piano gratuito Storage).
      await Promise.all(photoFiles.map((p) => p.file.delete().catch(() => {})));
    }
    // Nota: quando si usano i link firmati, la cancellazione dell'originale
    // avviene più avanti dal job cleanup.js (scaduto il link, non prima),
    // per non rompere un link appena inviato.

    return res.status(200).json({ sent: true, attachmentsCount: attachments.length, usedSignedLinks: !useAttachments });
  } catch (e) {
    return res.status(e.statusCode || 500).json({ error: e.message });
  }
};

function renderRecapHtml(stops) {
  const rows = stops
    .map((s) => {
      const minutes = Math.round((s.endedAt - s.startedAt) / 60000);
      return `<tr><td>${new Date(s.startedAt).toLocaleTimeString('it-IT', { hour: '2-digit', minute: '2-digit' })}</td><td>${s.addressLabel || ''}</td><td>${Math.floor(minutes / 60)}h ${minutes % 60}m</td><td>${s.notes || ''}</td></tr>`;
    })
    .join('');
  return `<h2>Recap giornaliero</h2><table border="1" cellpadding="6" cellspacing="0"><tr><th>Ora</th><th>Posizione</th><th>Durata</th><th>Note</th></tr>${rows}</table>`;
}
