// api/send-mandatino.js — v1.0.0 — 2026-09-20 00:10 UTC
//
// Requisito 9: invia il "mandatino delle ore" PDF già generato lato client
// (com.plumberdiary.app.pdf.MandatinoPdfGenerator) all'email del cliente,
// SOLO dopo la conferma esplicita mostrata nel mockup "ConfermaPDF" — questo
// endpoint non genera né decide nulla, si limita a spedire quanto l'utente
// ha già confermato.

const { db } = require('./_lib/firebase-admin');
const { requireAuthenticatedUser } = require('./_lib/auth');
const { sendMail } = require('./_lib/mailer');
const { checkAndIncrementQuota } = require('./_lib/quota');

module.exports = async (req, res) => {
  if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed' });

  try {
    const user = await requireAuthenticatedUser(req);
    const { teamId, clientId, recipientEmail, periodLabel, pdfBase64 } = req.body || {};
    if (!teamId || !clientId || !recipientEmail || !pdfBase64) {
      return res.status(400).json({ error: 'Parametri mancanti' });
    }

    const memberSnap = await db.doc(`teams/${teamId}/members/${user.uid}`).get();
    if (!memberSnap.exists) return res.status(403).json({ error: 'Non sei membro di questa squadra' });
    await checkAndIncrementQuota(`send-mandatino_${user.uid}`);

    const clientSnap = await db.doc(`teams/${teamId}/clients/${clientId}`).get();
    if (!clientSnap.exists) return res.status(404).json({ error: 'Cliente non trovato' });
    const clientName = clientSnap.data().name || 'cliente';

    await sendMail({
      to: recipientEmail,
      subject: `Mandatino delle ore — ${clientName}${periodLabel ? ` (${periodLabel})` : ''}`,
      html: `<p>In allegato il mandatino delle ore per ${clientName}.</p>`,
      attachments: [{ filename: 'mandatino-ore.pdf', content: Buffer.from(pdfBase64, 'base64') }],
    });

    return res.status(200).json({ sent: true });
  } catch (e) {
    return res.status(e.statusCode || 500).json({ error: e.message });
  }
};
