// _lib/firebase-admin.js — v1.13.0 — 2026-09-29 (v1.0.0 — 2026-09-20 00:10 UTC)
//
// v1.13.0 — 2026-09-29: rimosso Firebase Storage (foto spostate su uno storage
// a oggetti S3, vedi objectStore.js): niente più storageBucket né FIREBASE_STORAGE_BUCKET.
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
    // Prima: storageBucket: process.env.FIREBASE_STORAGE_BUCKET,
  });
}

module.exports = {
  db: admin.firestore(),
  auth: admin.auth(),
  // Prima: storage: admin.storage(),
  admin,
};
