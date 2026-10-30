package com.mhss.app.data.rag

/**
 * The embedding engine used to build retrieval vectors for the knowledge index.
 * `NONE` keeps the index keyword-only (FTS), which works fully offline with no
 * external server.
 */
enum class KnowledgeEmbeddingProvider(val id: Int) {
    NONE(0),
    OLLAMA(1),
    OPENAI_COMPATIBLE(2) // LM Studio or any server exposing /v1/embeddings
}

fun Int.toKnowledgeEmbeddingProvider() =
    KnowledgeEmbeddingProvider.entries.firstOrNull { it.id == this } ?: KnowledgeEmbeddingProvider.NONE
