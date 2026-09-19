// FirestorePaths.kt — v1.0.0 — 2026-09-20 00:10 UTC
package com.plumberdiary.app.data

/**
 * Unico punto di verità per i percorsi delle collezioni Firestore, così i
 * repository e le Firestore Security Rules (backend/firestore.rules) restano
 * sincronizzati: se cambia uno schema qui, va rispecchiato là.
 *
 * Struttura (vedi context.md, "Architettura backend"):
 *   teams/{teamId}
 *   teams/{teamId}/members/{uid}                          — appartenenza (mai creata dal client)
 *   teams/{teamId}/members/{uid}/settings/preferences      — UserSettings
 *   teams/{teamId}/members/{uid}/stops/{stopId}            — soste/interventi dell'utente
 *   teams/{teamId}/clients/{clientId}                      — anagrafica CONDIVISA
 *   teams/{teamId}/articles/{articleId}                    — listino CONDIVISO
 *   teams/{teamId}/invites/{inviteCode}                    — inviti in sospeso (gestiti dal backend)
 */
object FirestorePaths {
    fun team(teamId: String) = "teams/$teamId"
    fun members(teamId: String) = "${team(teamId)}/members"
    fun member(teamId: String, uid: String) = "${members(teamId)}/$uid"
    fun memberSettings(teamId: String, uid: String) = "${member(teamId, uid)}/settings/preferences"
    fun stops(teamId: String, uid: String) = "${member(teamId, uid)}/stops"
    fun stop(teamId: String, uid: String, stopId: String) = "${stops(teamId, uid)}/$stopId"
    fun clients(teamId: String) = "${team(teamId)}/clients"
    fun client(teamId: String, clientId: String) = "${clients(teamId)}/$clientId"
    fun articles(teamId: String) = "${team(teamId)}/articles"
    fun invites(teamId: String) = "${team(teamId)}/invites"

    /** Storage: vedi context.md per la doppia risoluzione original/display. */
    fun clientPhotoDisplay(teamId: String, clientId: String, photoId: String) =
        "teams/$teamId/clients/$clientId/photos/$photoId/display.jpg"

    fun stopPhotoOriginal(teamId: String, uid: String, stopId: String, photoId: String) =
        "teams/$teamId/members/$uid/stops/$stopId/photos/$photoId/original.jpg"

    fun stopPhotoDisplay(teamId: String, uid: String, stopId: String, photoId: String) =
        "teams/$teamId/members/$uid/stops/$stopId/photos/$photoId/display.jpg"
}
