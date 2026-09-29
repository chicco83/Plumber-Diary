// _lib/objectStore.js — v1.13.0 — 2026-09-29
//
// Archivio delle foto su uno storage a oggetti COMPATIBILE S3, al posto di
// Firebase Storage (decisione dell'utente del 2026-09-29: da fine 2024
// Firebase Storage richiede il piano Blaze, con carta, nei progetti nuovi).
//
// Servizio consigliato: Backblaze B2 (10 GB gratuiti, nessuna carta per il
// piano gratuito, API S3). Il codice usa solo l'API S3 standard: per passare
// a un altro servizio compatibile (Cloudflare R2, Wasabi, MinIO...) bastano le
// variabili d'ambiente Vercel:
//   S3_ENDPOINT           es. https://s3.eu-central-003.backblazeb2.com
//   S3_REGION             es. eu-central-003
//   S3_BUCKET             nome del bucket (PRIVATO)
//   S3_ACCESS_KEY_ID      keyID della Application Key B2
//   S3_SECRET_ACCESS_KEY  applicationKey della Application Key B2
//
// Il bucket è privato: l'app non ha mai le credenziali. Riceve dal backend
// URL firmati a scadenza (caricamento PUT e visualizzazione GET), emessi solo
// dopo il controllo di appartenenza alla squadra (vedi photo-upload-urls.js e
// photo-view-urls.js): è il ruolo che prima avevano le storage.rules.

const {
  S3Client,
  PutObjectCommand,
  GetObjectCommand,
  HeadObjectCommand,
  ListObjectsV2Command,
  DeleteObjectsCommand,
} = require('@aws-sdk/client-s3');
const { getSignedUrl } = require('@aws-sdk/s3-request-presigner');

let client = null;

function s3() {
  if (!client) {
    client = new S3Client({
      endpoint: process.env.S3_ENDPOINT,
      region: process.env.S3_REGION || 'us-east-1',
      forcePathStyle: true, // funziona con tutti i servizi compatibili
      credentials: {
        accessKeyId: process.env.S3_ACCESS_KEY_ID || '',
        secretAccessKey: process.env.S3_SECRET_ACCESS_KEY || '',
      },
      // Le versioni recenti dell'SDK aggiungono checksum CRC32 a ogni
      // richiesta: diversi servizi compatibili (B2 compreso) li rifiutano
      // negli URL firmati. Solo quando strettamente richiesti.
      requestChecksumCalculation: 'WHEN_REQUIRED',
      responseChecksumValidation: 'WHEN_REQUIRED',
    });
  }
  return client;
}

const bucket = () => process.env.S3_BUCKET;

/** URL firmato per caricare [key]; il client deve inviare lo stesso Content-Type. */
function presignPut(key, contentType, ttlSeconds = 15 * 60) {
  // signableHeaders: il Content-Type entra nella firma, così l'URL vale solo
  // per quel tipo (un'immagine) e non per un file qualsiasi.
  return getSignedUrl(s3(), new PutObjectCommand({ Bucket: bucket(), Key: key, ContentType: contentType }), {
    expiresIn: ttlSeconds,
    signableHeaders: new Set(['content-type']),
  });
}

/** URL firmato per leggere [key] (massimo 7 giorni, limite della firma S3). */
function presignGet(key, ttlSeconds = 60 * 60) {
  return getSignedUrl(s3(), new GetObjectCommand({ Bucket: bucket(), Key: key }), {
    expiresIn: Math.min(ttlSeconds, 7 * 24 * 60 * 60),
  });
}

/** Metadati dell'oggetto, o null se non esiste. */
async function head(key) {
  try {
    const r = await s3().send(new HeadObjectCommand({ Bucket: bucket(), Key: key }));
    return { size: Number(r.ContentLength || 0), contentType: r.ContentType || '' };
  } catch (e) {
    if (e.name === 'NotFound' || (e.$metadata && e.$metadata.httpStatusCode === 404)) return null;
    throw e;
  }
}

async function getBuffer(key) {
  const r = await s3().send(new GetObjectCommand({ Bucket: bucket(), Key: key }));
  return Buffer.from(await r.Body.transformToByteArray());
}

/** Tutti gli oggetti sotto [prefix]: [{ key, size, lastModified (ms) }]. */
async function list(prefix) {
  const out = [];
  let token;
  do {
    const r = await s3().send(new ListObjectsV2Command({ Bucket: bucket(), Prefix: prefix, ContinuationToken: token }));
    for (const o of r.Contents || []) {
      out.push({ key: o.Key, size: Number(o.Size || 0), lastModified: new Date(o.LastModified).getTime() });
    }
    token = r.IsTruncated ? r.NextContinuationToken : undefined;
  } while (token);
  return out;
}

/** Cancella le chiavi indicate (a blocchi da 1000, limite S3). */
async function deleteKeys(keys) {
  for (let i = 0; i < keys.length; i += 1000) {
    const chunk = keys.slice(i, i + 1000);
    if (chunk.length === 0) continue;
    await s3().send(new DeleteObjectsCommand({
      Bucket: bucket(),
      Delete: { Objects: chunk.map((Key) => ({ Key })), Quiet: true },
    }));
  }
  return keys.length;
}

async function deletePrefix(prefix) {
  const objects = await list(prefix);
  return deleteKeys(objects.map((o) => o.key));
}

module.exports = { presignPut, presignGet, head, getBuffer, list, deleteKeys, deletePrefix };
