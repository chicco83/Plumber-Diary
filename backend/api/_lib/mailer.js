// _lib/mailer.js — v1.0.0 — 2026-09-20 00:10 UTC
//
// Invio email tramite SMTP generico (nodemailer), configurato via env var
// Vercel: SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASS, SMTP_FROM. Scelta
// deliberata per restare a costo zero: funziona anche con un account Gmail
// e una App Password dedicata (nessun servizio email a pagamento
// richiesto), coerente con la filosofia "mai una carta collegata" già
// seguita nel progetto gemello per il resto dello stack.

const nodemailer = require('nodemailer');

function getTransport() {
  return nodemailer.createTransport({
    host: process.env.SMTP_HOST,
    port: Number(process.env.SMTP_PORT || 587),
    secure: Number(process.env.SMTP_PORT) === 465,
    auth: { user: process.env.SMTP_USER, pass: process.env.SMTP_PASS },
  });
}

/**
 * @param {{to: string, subject: string, html: string, attachments?: Array<{filename: string, content: Buffer}>}} options
 */
async function sendMail(options) {
  const transport = getTransport();
  await transport.sendMail({
    from: process.env.SMTP_FROM,
    to: options.to,
    subject: options.subject,
    html: options.html,
    attachments: options.attachments || [],
  });
}

module.exports = { sendMail };
