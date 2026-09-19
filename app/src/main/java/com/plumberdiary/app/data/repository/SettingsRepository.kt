// SettingsRepository.kt — v1.0.0 — 2026-09-20 00:10 UTC
package com.plumberdiary.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import com.plumberdiary.app.data.FirestorePaths
import com.plumberdiary.app.data.model.UserSettings
import kotlinx.coroutines.tasks.await

class SettingsRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    suspend fun get(teamId: String, uid: String): UserSettings {
        val doc = firestore.document(FirestorePaths.memberSettings(teamId, uid)).get().await()
        return doc.toObject<UserSettings>() ?: UserSettings()
    }

    suspend fun save(teamId: String, uid: String, settings: UserSettings) {
        firestore.document(FirestorePaths.memberSettings(teamId, uid)).set(settings).await()
    }
}
