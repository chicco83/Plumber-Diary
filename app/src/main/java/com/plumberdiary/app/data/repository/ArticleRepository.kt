// ArticleRepository.kt — v1.6.0 — 2026-09-23
//
// Listino articoli CONDIVISO a livello di squadra (teams/{teamId}/articles),
// requisito 11: un'unica fonte per tutti i membri, mai duplicata per utente.
// Mancava nello scaffolding v1.5.0: ClienteScreen e DettaglioScreen lo usano
// per gestire il listino e aggiungere materiali a una sosta.
package com.plumberdiary.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import com.plumberdiary.app.data.FirestorePaths
import com.plumberdiary.app.data.model.Article
import kotlinx.coroutines.tasks.await

class ArticleRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    suspend fun getAll(teamId: String): List<Article> {
        val snapshot = firestore.collection(FirestorePaths.articles(teamId)).get().await()
        return snapshot.documents.mapNotNull { it.toObject<Article>() }
    }

    suspend fun upsert(teamId: String, article: Article): String {
        val collection = firestore.collection(FirestorePaths.articles(teamId))
        val docRef = if (article.id.isBlank()) collection.document() else collection.document(article.id)
        docRef.set(article.copy(id = docRef.id)).await()
        return docRef.id
    }

    suspend fun delete(teamId: String, articleId: String) {
        firestore.document(FirestorePaths.articles(teamId) + "/$articleId").delete().await()
    }
}
