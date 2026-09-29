// api/send-mandatino.js — v1.12.0 — 2026-09-29 (v1.0.0 — 2026-09-20 00:10 UTC)
//
// Requisito 9 (con 15): invia il "mandatino delle ore" PDF già generato lato
// client (com.plumberdiary.app.pdf.MandatinoPdfGenerator) all'email del
// cliente, SOLO dopo la conferma esplicita del tecnico nella schermata
// Mandatino — questo endpoint non genera né decide nulla, si limita a
// spedire quanto l'utente ha già confermato.
//
// v1.12.0 — 2026-09-29, sicurezza:
//  - il destinatario NON arriva più dall'app ma dall'anagrafica condivisa del
//    cliente (hoursReportEmail). Prima qualunque membro poteva spedire un PDF
//    qualsiasi a un indirizzo qualsiasi usando il Gmail aziendale (rischio
//    spam/phishing e sospensione dell'account). Se il tecnico corregge
//    l'email nella schermata, l'app aggiorna prima l'anagrafica e poi invia;
//    `recipientEmail` nella richiesta, se presente, deve coincidere;
//  - quota bassa (50 invii/giorno per utente) invece di 4000;
//  - nome cliente ed etichetta periodo "escaped" nel corpo HTML.

const { db } = require('./_lib/firebase-admin');
const { requireAuthenticatedUser } = require('./_lib/auth');
const { sendMail } = require('./_lib/mailer');
const { checkAndIncrementQuota } = require('./_lib/quota');
const { escapeHtml } = require('./_lib/html');
const { requireSafeIds } = require('./_lib/ids');

const DAILY_MANDATINO_LIMIT = 50;
const MAX_PDF_BASE64_CHARS = 4 * 1024 * 1024; // sotto il limite di 4,5 MB del corpo richiesta Vercel

module.exports = async (req, res) => {
  if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed' });

  try {
    const user = await requireAuthenticatedUser(req);
    const { teamId, clientId, recipientEmail, periodLabel, pdfBase64 } = req.body || {};
    // Versione precedente (v1.0.0 — 2026-09-20), sostituita il 2026-09-29:
    // if (!teamId || !clientId || !recipientEmail || !pdfBase64) {
    if (!teamId || !clientId || !pdfBase64) {
      return res.status(400).json({ error: 'Parametri mancanti' });
    }
    requireSafeIds({ teamId, clientId });
    if (typeof pdfBase64 !== 'string' || pdfBase64.length > MAX_PDF_BASE64_CHARS) {
      return res.status(413).json({ error: 'PDF troppo grande' });
    }

    const memberSnap = await db.doc(`teams/${teamId}/members/${user.uid}`).get();
    if (!memberSnap.exists) return res.status(403).json({ error: 'Non sei membro di questa squadra' });
    // Versione precedente: await checkAndIncrementQuota(`send-mandatino_${user.uid}`);
    await checkAndIncrementQuota(`send-mandatino_${user.uid}`, DAILY_MANDATINO_LIMIT);

    const clientSnap = await db.doc(`teams/${teamId}/clients/${clientId}`).get();
    if (!clientSnap.exists) return res.status(404).json({ error: 'Cliente non trovato' });
    const client = clientSnap.data();
    const clientName = client.name || 'cliente';
    const to = String(client.hoursReportEmail || '').trim();
    if (!to) {
      return res.status(400).json({ error: "Il cliente non ha un'email per il mandatino in anagrafica" });
    }
    if (recipientEmail && String(recipientEmail).trim().toLowerCase() !== to.toLowerCase()) {
      return res.status(409).json({ error: "L'email indicata non coincide con quella in anagrafica: salva prima l'anagrafica" });
    }

    // Versione precedente (v1.0.0 — 2026-09-20), sostituita il 2026-09-29 (destinatario dal client, HTML non escaped):
    // await sendMail({ to: recipientEmail, subject: `Mandatino delle ore — ${clientName}...`,
    //   html: `<p>In allegato il mandatino delle ore per ${clientName}.</p>`, ... });
    await sendMail({
      to,
      subject: `Mandatino delle ore — ${clientName}${periodLabel ? ` (${periodLabel})` : ''}`,
      html: `<p>In allegato il mandatino delle ore per ${escapeHtml(clientName)}${periodLabel ? ` (${escapeHtml(periodLabel)})` : ''}.</p>`,
      attachments: [{ filename: 'mandatino-ore.pdf', content: Buffer.from(pdfBase64, 'base64') }],
    });

    return res.status(200).json({ sent: true, to });
  } catch (e) {
    return res.status(e.statusCode || 500).json({ error: e.message });
  }
};
