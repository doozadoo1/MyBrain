package com.mhss.app.data.rag

import com.mhss.app.preferences.PrefsConstants
import com.mhss.app.preferences.domain.model.intPreferencesKey
import com.mhss.app.preferences.domain.model.stringPreferencesKey
import com.mhss.app.preferences.domain.use_case.GetPreferenceUseCase
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single

/** Reads the knowledge-index related preferences. */
@Single
class KnowledgeSettings(private val getPreference: GetPreferenceUseCase) {

    suspend fun embeddingProvider(): KnowledgeEmbeddingProvider =
        getPreference(
            intPreferencesKey(PrefsConstants.KNOWLEDGE_EMBEDDING_PROVIDER_KEY),
            KnowledgeEmbeddingProvider.NONE.id
        ).first().toKnowledgeEmbeddingProvider()

    suspend fun embeddingModel(): String =
        getPreference(
            stringPreferencesKey(PrefsConstants.KNOWLEDGE_EMBEDDING_MODEL_KEY),
            DEFAULT_EMBEDDING_MODEL
        ).first().ifBlank { DEFAULT_EMBEDDING_MODEL }

    companion object {
        const val DEFAULT_EMBEDDING_MODEL = "nomic-embed-text"

        /** Chunk texts are embedded in batches of this size. */
        const val EMBED_BATCH_SIZE = 16

        /** Default number of chunks returned by a retrieval. */
        const val DEFAULT_TOP_K = 6

        /** Max characters of one chunk sent back to the LLM. */
        const val MAX_HIT_TEXT_LENGTH = 1500

        /** Max total characters of injected context. */
        const val MAX_INJECTION_LENGTH = 5000
    }
}
