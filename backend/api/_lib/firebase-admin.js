// _lib/firebase-admin.js — v1.0.0 — 2026-09-20 00:10 UTC
//
// Inizializzazione condivisa dell'Admin SDK, riusata da tutti gli endpoint.
// Le credenziali del service account arrivano da variabili d'ambiente Vercel
// (mai committate nel repo): FIREBASE_PROJECT_ID, FIREBASE_CLIENT_EMAIL,
// FIREBASE_PRIVATE_KEY.

const admin = require('firebase-admin');

if (!admin.apps.length) {
  admin.initializeApp({
    credential: admin.credential.cert({
      projectId: process.env.FIREBASE_PROJECT_ID,
      clientEmail: process.env.FIREBASE_CLIENT_EMAIL,
      // Vercel non conserva gli a-capo nelle env var: vanno ricodificati.
      privateKey: (process.env.FIREBASE_PRIVATE_KEY || '').replace(/\\n/g, '\n'),
    }),
    storageBucket: process.env.FIREBASE_STORAGE_BUCKET,
  });
}

module.exports = {
  db: admin.firestore(),
  auth: admin.auth(),
  storage: admin.storage(),
  admin,
};
