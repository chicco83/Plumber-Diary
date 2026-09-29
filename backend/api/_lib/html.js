// _lib/html.js — v1.12.0 — 2026-09-29
//
// Escape dei testi inseriti dall'utente (note, nomi clienti, descrizioni
// materiali) nel corpo HTML delle email. Prima venivano concatenati così come
// erano: un "<" in una nota rompeva l'impaginazione e permetteva di inserire
// HTML arbitrario (link, immagini) in una mail spedita dall'account aziendale.

function escapeHtml(value) {
  return String(value == null ? '' : value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

module.exports = { escapeHtml };
