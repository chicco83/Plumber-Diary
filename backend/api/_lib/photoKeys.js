// _lib/photoKeys.js — v1.13.0 — 2026-09-29
//
// Validazione delle chiavi delle foto nello storage a oggetti. Sono le stesse
// dei percorsi Firebase Storage di prima (vedi FirestorePaths.kt lato app):
//   teams/{teamId}/clients/{clientId}/photos/{photoId}/display.jpg
//   teams/{teamId}/members/{uid}/stops/{stopId}/photos/{photoId}/original.jpg
//   teams/{teamId}/members/{uid}/stops/{stopId}/photos/{photoId}/display.jpg
// Qualunque altra chiave viene rifiutata: il backend firma URL solo per
// queste, dopo aver verificato chi chiede (sostituisce le storage.rules).

const ID = '[A-Za-z0-9_-]{1,128}';
const STOP_KEY = new RegExp(`^teams/(${ID})/members/(${ID})/stops/(${ID})/photos/(${ID})/(display|original)\\.jpg$`);
const CLIENT_KEY = new RegExp(`^teams/(${ID})/clients/(${ID})/photos/(${ID})/display\\.jpg$`);

/** { kind: 'stop'|'client', teamId, uid?, stopId?, clientId?, photoId, variant } oppure null. */
function parsePhotoKey(key) {
  if (typeof key !== 'string') return null;
  let m = STOP_KEY.exec(key);
  if (m) return { kind: 'stop', teamId: m[1], uid: m[2], stopId: m[3], photoId: m[4], variant: m[5] };
  m = CLIENT_KEY.exec(key);
  if (m) return { kind: 'client', teamId: m[1], clientId: m[2], photoId: m[3], variant: 'display' };
  return null;
}

module.exports = { parsePhotoKey };
