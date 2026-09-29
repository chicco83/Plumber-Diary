// api/send-recap-email.js — v1.13.0 — 2026-09-29 (v1.12.0 — 2026-09-29; v1.0.0 — 2026-09-20 00:10 UTC)
//
// Requisito 6/12: invia il recap giornaliero all'indirizzo amministrazione
// impostato nelle Opzioni (default ON), con le foto degli interventi in
// ALTA RISOLUZIONE. Se il totale supera la soglia pratica di un provider
// email, passa da allegati diretti a link di download sicuro e a scadenza
// (vedi context.md, "Foto").
//
// Chiamato dalla schermata Recap alla conferma del recap o dal pulsante
// "Invia recap via email".
//
// v1.12.0 — 2026-09-29, correzioni della review:
//  - la mail non diceva PRESSO CHI si era lavorato: mostrava ora, una colonna
//    "Posizione" sempre vuota (addressLabel non è mai valorizzato) e le note.
//    Ora per ogni sosta: orario, durata, cliente (o sede/pausa/da associare),
//    note, promemoria, materiali con totale; in fondo i totali del giorno;
//  - destinatario letto dalle Opzioni dell'utente sul server
//    (settings/preferences.recapEmailAddress), non dalla richiesta: prima
//    l'endpoint spediva a qualunque indirizzo gli venisse passato;
//  - le copie "original" NON vengono più cancellate subito dopo l'invio:
//    un secondo invio ("Invia recap via email") arrivava senza foto HD. Le
//    cancella cleanup.js dopo il periodo di grazia (7 giorni);
//  - testi dell'utente "escaped" nell'HTML; quota 20 invii/giorno.
//
// v1.13.0 — 2026-09-29: le foto HD si leggono dallo storage a oggetti S3
// (_lib/objectStore.js) invece che da Firebase Storage; i link firmati per
// le mail troppo pesanti sono URL S3 firmati (stessa durata, 5 giorni).

// Prima (v1.12.0): const { db, storage } = require('./_lib/firebase-admin');
const { db } = require('./_lib/firebase-admin');
const objectStore = require('./_lib/objectStore');
const { requireAuthenticatedUser } = require('./_lib/auth');
const { sendMail } = require('./_lib/mailer');
const { checkAndIncrementQuota } = require('./_lib/quota');
const { escapeHtml } = require('./_lib/html');
const { requireSafeIds } = require('./_lib/ids');

const MAX_ATTACHMENTS_BYTES = 20 * 1024 * 1024; // soglia pratica ~20MB, sotto i limiti tipici dei provider
const SIGNED_URL_TTL_MS = 5 * 24 * 60 * 60 * 1000; // 5 giorni (le originali vivono 7 giorni, vedi cleanup.js)
const DAILY_RECAP_LIMIT = 20;
const TZ = 'Europe/Rome'; // le funzioni Vercel girano in UTC: orari della mail nel fuso dei tecnici

module.exports = async (req, res) => {
  if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed' });

  try {
    const user = await requireAuthenticatedUser(req);
    // Versione precedente (v1.0.0 — 2026-09-20), sostituita il 2026-09-29:
    // const { teamId, dayStartMillis, dayEndMillis, recipientEmail } = req.body || {};
    // if (!teamId || !dayStartMillis || !dayEndMillis || !recipientEmail) { ... }
    const { teamId, dayStartMillis, dayEndMillis } = req.body || {};
    if (!teamId || !dayStartMillis || !dayEndMillis) {
      return res.status(400).json({ error: 'Parametri mancanti' });
    }
    requireSafeIds({ teamId });

    const memberRef = db.doc(`teams/${teamId}/members/${user.uid}`);
    const memberSnap = await memberRef.get();
    if (!memberSnap.exists) return res.status(403).json({ error: 'Non sei membro di questa squadra' });
    await checkAndIncrementQuota(`send-recap-email_${user.uid}`, DAILY_RECAP_LIMIT);

    const settingsSnap = await memberRef.collection('settings').doc('preferences').get();
    const settings = settingsSnap.exists ? settingsSnap.data() : {};
    const recipientEmail = String(settings.recapEmailAddress || '').trim();
    if (!recipientEmail) {
      return res.status(400).json({ error: "Manca l'email dell'amministrazione nelle Opzioni" });
    }

    const stopsSnap = await memberRef
      .collection('stops')
      .where('startedAt', '>=', Number(dayStartMillis))
      .where('startedAt', '<', Number(dayEndMillis))
      .orderBy('startedAt')
      .get();
    const stops = stopsSnap.docs.map((d) => ({ id: d.id, ...d.data() }));

    const clientsSnap = await db.collection(`teams/${teamId}/clients`).get();
    const clientNames = {};
    clientsSnap.docs.forEach((d) => { clientNames[d.id] = d.data().name || ''; });

    // Versione precedente (v1.12.0 — 2026-09-29), sostituita il 2026-09-29 — Firebase Storage:
    // const bucket = storage.bucket();
    // const file = bucket.file(path); const [exists] = await file.exists(); if (!exists) continue;
    // const [metadata] = await file.getMetadata();
    // photoFiles.push({ stop, photoId, file, sizeBytes: Number(metadata.size || 0), ext: extensionFor(metadata.contentType) });
    const photoFiles = []; // { stop, photoId, key, sizeBytes, ext }
    for (const stop of stops) {
      for (const photoId of stop.photoIds || []) {
        const key = `teams/${teamId}/members/${user.uid}/stops/${stop.id}/photos/${photoId}/original.jpg`;
        const meta = await objectStore.head(key);
        if (!meta) continue; // originale già scaduto (oltre 7 giorni) o mai caricato
        photoFiles.push({ stop, photoId, key, sizeBytes: meta.size, ext: extensionFor(meta.contentType) });
      }
    }

    const totalBytes = photoFiles.reduce((sum, p) => sum + p.sizeBytes, 0);
    const useAttachments = totalBytes <= MAX_ATTACHMENTS_BYTES;

    // Nome file leggibile: orario + cliente, invece degli id tecnici.
    const fileName = (p, i) =>
      `${timeLabel(p.stop.startedAt).replace(':', '')}_${safeFileName(stopLabel(p.stop, clientNames))}_${i + 1}.${p.ext}`;

    let attachments = [];
    let linksHtml = '';
    if (useAttachments) {
      attachments = await Promise.all(
        photoFiles.map(async (p, i) => {
          // Prima: const [buffer] = await p.file.download();
          const buffer = await objectStore.getBuffer(p.key);
          return { filename: fileName(p, i), content: buffer };
        }),
      );
    } else {
      const links = await Promise.all(
        photoFiles.map(async (p, i) => {
          // Prima: const [url] = await p.file.getSignedUrl({ action: 'read', expires: Date.now() + SIGNED_URL_TTL_MS });
          const url = await objectStore.presignGet(p.key, SIGNED_URL_TTL_MS / 1000);
          return `<li><a href="${escapeHtml(url)}">${escapeHtml(fileName(p, i))}</a></li>`;
        }),
      );
      linksHtml = `<p>Le foto in alta risoluzione superano la dimensione consentita in allegato: scaricale dai link qui sotto (validi 5 giorni).</p><ul>${links.join('')}</ul>`;
    }

    const technician = memberSnap.data().displayName || memberSnap.data().email || '';
    const html = renderRecapHtml(stops, clientNames, technician, Number(dayStartMillis)) + linksHtml;
    await sendMail({
      to: recipientEmail,
      subject: `Recap giornaliero — ${technician ? `${technician} — ` : ''}${dateLabel(Number(dayStartMillis))}`,
      html,
      attachments,
    });

    // Versione precedente (v1.0.0 — 2026-09-20), rimossa il 2026-09-29: cancellava
    // le originali subito dopo l'invio, così un secondo invio arrivava senza foto HD.
    // if (useAttachments) {
    //   await Promise.all(photoFiles.map((p) => p.file.delete().catch(() => {})));
    // }
    // Ora le cancella solo cleanup.js, trascorso il periodo di grazia.

    return res.status(200).json({ sent: true, to: recipientEmail, attachmentsCount: attachments.length, usedSignedLinks: !useAttachments });
  } catch (e) {
    return res.status(e.statusCode || 500).json({ error: e.message });
  }
};

function stopLabel(stop, clientNames) {
  if (stop.clientId) return clientNames[stop.clientId] || 'Cliente eliminato';
  if (stop.kind === 'DEPOT') return 'Sede / deposito';
  if (stop.kind === 'BREAK') return 'Pausa';
  return 'Cliente non associato';
}

function minutesOf(stop) {
  const end = stop.endedAt > 0 ? stop.endedAt : Date.now();
  return Math.max(0, Math.round((end - stop.startedAt) / 60000));
}

function durationLabel(minutes) {
  return `${Math.floor(minutes / 60)}h ${String(minutes % 60).padStart(2, '0')}m`;
}

function euros(value) {
  return `€ ${Number(value || 0).toFixed(2).replace('.', ',')}`;
}

function timeLabel(millis) {
  return new Date(millis).toLocaleTimeString('it-IT', { hour: '2-digit', minute: '2-digit', timeZone: TZ });
}

function dateLabel(millis) {
  return new Date(millis).toLocaleDateString('it-IT', { timeZone: TZ });
}

function extensionFor(contentType) {
  if (contentType === 'image/png') return 'png';
  if (contentType === 'image/heic' || contentType === 'image/heif') return 'heic';
  if (contentType === 'image/webp') return 'webp';
  return 'jpg';
}

function safeFileName(text) {
  return String(text).replace(/[^A-Za-z0-9À-ÿ_-]+/g, '-').slice(0, 40);
}

// Versione precedente (v1.0.0 — 2026-09-20), sostituita il 2026-09-29:
// function renderRecapHtml(stops) {
//   const rows = stops.map((s) => {
//     const minutes = Math.round((s.endedAt - s.startedAt) / 60000);
//     return `<tr><td>${ora}</td><td>${s.addressLabel || ''}</td><td>${Math.floor(minutes / 60)}h ${minutes % 60}m</td><td>${s.notes || ''}</td></tr>`;
//   }).join('');
//   return `<h2>Recap giornaliero</h2><table ...><tr><th>Ora</th><th>Posizione</th><th>Durata</th><th>Note</th></tr>${rows}</table>`;
// }
function renderRecapHtml(stops, clientNames, technician, dayStartMillis) {
  let clientMinutes = 0;
  let materialsTotal = 0;

  const rows = stops
    .map((s) => {
      const minutes = minutesOf(s);
      if (s.clientId) clientMinutes += minutes;
      const lines = s.articleLines || [];
      const stopMaterials = lines.reduce((sum, l) => sum + (l.unitPrice || 0) * (l.quantity || 0), 0);
      materialsTotal += stopMaterials;
      const materialsHtml = lines.length
        ? `<ul style="margin:0;padding-left:16px">${lines
          .map((l) => `<li>${escapeHtml(l.code)} ${escapeHtml(l.description)} × ${escapeHtml(l.quantity)} (${euros((l.unitPrice || 0) * (l.quantity || 0))})</li>`)
          .join('')}</ul>`
        : '';
      const end = s.endedAt > 0 ? timeLabel(s.endedAt) : 'in corso';
      const notes = [s.notes, s.reminderText ? `Promemoria: ${s.reminderText}` : '']
        .filter(Boolean)
        .map(escapeHtml)
        .join('<br>');
      const photos = (s.photoIds || []).length;
      return `<tr>
        <td>${timeLabel(s.startedAt)}–${end}</td>
        <td>${durationLabel(minutes)}</td>
        <td><b>${escapeHtml(stopLabel(s, clientNames))}</b></td>
        <td>${notes}${photos ? `<br><i>${photos} foto</i>` : ''}</td>
        <td>${materialsHtml}${stopMaterials ? `<b>${euros(stopMaterials)}</b>` : ''}</td>
      </tr>`;
    })
    .join('');

  return `<h2>Recap giornaliero — ${escapeHtml(dateLabel(dayStartMillis))}</h2>
    ${technician ? `<p>Tecnico: <b>${escapeHtml(technician)}</b></p>` : ''}
    <p>Ore presso clienti: <b>${durationLabel(clientMinutes)}</b> · Materiali: <b>${euros(materialsTotal)}</b> · Soste: ${stops.length}</p>
    <table border="1" cellpadding="6" cellspacing="0">
      <tr><th>Orario</th><th>Durata</th><th>Cliente</th><th>Note</th><th>Materiali</th></tr>
      ${rows}
    </table>`;
}
