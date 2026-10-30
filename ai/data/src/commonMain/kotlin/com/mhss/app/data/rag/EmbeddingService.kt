package com.mhss.app.data.rag

/**
 * Produces embedding vectors for a batch of texts.
 *
 * Implementations must be resilient: return `null` (not throw) when the
 * provider is unreachable or refuses the request so indexing can degrade
 * gracefully to keyword-only chunks.
 */
interface EmbeddingService {
    suspend fun embed(texts: List<String>): List<List<Float>>?
}
