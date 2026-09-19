// Article.kt — v1.0.0 — 2026-09-20 00:10 UTC
package com.plumberdiary.app.data.model

/**
 * Voce del listino articoli condiviso: teams/{teamId}/articles/{articleId}.
 * Le [com.plumberdiary.app.data.model.ArticleLine] su una sosta ne copiano
 * codice/descrizione/prezzo al momento dell'uso, così una modifica successiva
 * del listino non altera retroattivamente gli interventi già registrati.
 */
data class Article(
    val id: String = "",
    val code: String = "",
    val description: String = "",
    val unitPrice: Double = 0.0,
)
