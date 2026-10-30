package com.mhss.app.presentation.integrations

import com.mhss.app.data.rag.KnowledgeEmbeddingProvider
import com.mhss.app.preferences.domain.model.AiProvider

sealed class IntegrationsEvent {
    data class ToggleAiProvider(val enabled: Boolean) : IntegrationsEvent()
    data class SelectProvider(val provider: AiProvider) : IntegrationsEvent()
    data class UpdateApiKey(val provider: AiProvider, val key: String) : IntegrationsEvent()
    data class UpdateModel(val provider: AiProvider, val model: String) : IntegrationsEvent()
    data class ToggleCustomURL(val provider: AiProvider, val enabled: Boolean) : IntegrationsEvent()
    data class UpdateCustomURL(val provider: AiProvider, val url: String) : IntegrationsEvent()
    data class ToggleAiTools(val enabled: Boolean) : IntegrationsEvent()
    data object DownloadGeminiNanoModel : IntegrationsEvent()
    data class SetExternalNotesEnabled(val enabled: Boolean) : IntegrationsEvent()
    data class SelectExternalNotesFolder(val folderUri: String) : IntegrationsEvent()
    data class SelectKnowledgeEmbeddingProvider(val provider: KnowledgeEmbeddingProvider) : IntegrationsEvent()
    data class UpdateKnowledgeEmbeddingModel(val model: String) : IntegrationsEvent()
    data object ReindexKnowledge : IntegrationsEvent()
    data object ClearKnowledgeIndex : IntegrationsEvent()
}
