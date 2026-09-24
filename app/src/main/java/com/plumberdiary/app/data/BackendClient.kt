// BackendClient.kt — v1.0.0 — 2026-09-20 00:30 UTC
package com.plumberdiary.app.data

import com.plumberdiary.app.auth.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Client per gli endpoint Vercel Functions in backend/api/ (vedi
 * backend/README.md per l'elenco completo). Ogni chiamata allega l'ID token
 * Firebase dell'utente come Authorization: Bearer — verificato lato server
 * da backend/api/_lib/auth.js prima di qualunque scrittura con l'Admin SDK.
 *
 * baseUrl va configurato con l'URL reale del deploy Vercel (es.
 * https://plumber-diary.vercel.app) una volta creato — vedi
 * app/README.md, "Passaggi manuali richiesti".
 */
class BackendClient(
    private val baseUrl: String,
    private val authRepository: AuthRepository,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun createTeam(teamName: String): String {
        val response = post("create-team", JSONObject().put("teamName", teamName))
        return response.getString("teamId")
    }

    suspend fun createInvite(teamId: String): String {
        val response = post("create-invite", JSONObject().put("teamId", teamId))
        return response.getString("inviteCode")
    }

    suspend fun acceptInvite(teamId: String, inviteCode: String) {
        post("accept-invite", JSONObject().put("teamId", teamId).put("inviteCode", inviteCode))
    }

    // v1.6.0 — 2026-09-23: i due endpoint di invio email (requisiti 6/12 e 9)
    // erano documentati in backend/README ma non ancora raggiungibili dall'app.

    /** Requisito 6/12: recap giornaliero con foto HD a [recipientEmail]. */
    suspend fun sendRecapEmail(
        teamId: String,
        dayStartMillis: Long,
        dayEndMillis: Long,
        recipientEmail: String,
    ) {
        post(
            "send-recap-email",
            JSONObject()
                .put("teamId", teamId)
                .put("dayStartMillis", dayStartMillis)
                .put("dayEndMillis", dayEndMillis)
                .put("recipientEmail", recipientEmail),
        )
    }

    /** Requisito 9: mandatino ore PDF (base64) già confermato dall'utente. */
    suspend fun sendMandatino(
        teamId: String,
        clientId: String,
        recipientEmail: String,
        periodLabel: String,
        pdfBase64: String,
    ) {
        post(
            "send-mandatino",
            JSONObject()
                .put("teamId", teamId)
                .put("clientId", clientId)
                .put("recipientEmail", recipientEmail)
                .put("periodLabel", periodLabel)
                .put("pdfBase64", pdfBase64),
        )
    }

    private suspend fun post(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val token = authRepository.getIdToken()
        val request = Request.Builder()
            .url("$baseUrl/api/$path")
            .addHeader("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = if (text.isNotBlank()) JSONObject(text) else JSONObject()
            if (!response.isSuccessful) {
                error(json.optString("error", "Errore HTTP ${response.code} su /api/$path"))
            }
            json
        }
    }
}
