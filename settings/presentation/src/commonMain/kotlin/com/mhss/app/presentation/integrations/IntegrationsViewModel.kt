package com.mhss.app.presentation.integrations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.mhss.app.data.noteMarkdownModule
import com.mhss.app.data.noteRoomModule
import com.mhss.app.data.rag.KnowledgeEmbeddingProvider
import com.mhss.app.data.rag.KnowledgeIndexer
import com.mhss.app.data.rag.KnowledgeSettings
import com.mhss.app.data.rag.toKnowledgeEmbeddingProvider
import com.mhss.app.domain.gemininano.GeminiNanoService
import com.mhss.app.domain.gemininano.GeminiNanoStatus
import com.mhss.app.domain.gemininano.toGeminiNanoMode
import com.mhss.app.domain.use_case.UpdateExternalNotesFolderUseCase
import com.mhss.app.preferences.PrefsConstants
import com.mhss.app.preferences.domain.model.AiProvider
import com.mhss.app.preferences.domain.model.PrefsKey
import com.mhss.app.preferences.domain.model.PrefsKey.BooleanKey
import com.mhss.app.preferences.domain.model.PrefsKey.IntKey
import com.mhss.app.preferences.domain.model.customUrlEnabledPrefsKey
import com.mhss.app.preferences.domain.model.customUrlPrefsKey
import com.mhss.app.preferences.domain.model.keyPrefsKey
import com.mhss.app.preferences.domain.model.modelPrefsKey
import com.mhss.app.preferences.domain.model.stringPreferencesKey
import com.mhss.app.preferences.domain.model.toAiProvider
import com.mhss.app.preferences.domain.use_case.GetPreferenceUseCase
import com.mhss.app.preferences.domain.use_case.SavePreferenceUseCase
import com.mhss.app.storage.FileUtilsRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import org.koin.core.context.GlobalContext.loadKoinModules
import org.koin.core.context.GlobalContext.unloadKoinModules

@KoinViewModel
class IntegrationsViewModel(
    private val savePreference: SavePreferenceUseCase,
    private val getPreference: GetPreferenceUseCase,
    private val updateExternalNotesFolder: UpdateExternalNotesFolderUseCase,
    private val fileUtilsRepository: FileUtilsRepository,
    private val geminiNanoService: GeminiNanoService,
    private val knowledgeIndexer: KnowledgeIndexer
) : ViewModel() {

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        throwable.printStackTrace()
    }
    val geminiNanoStatus: Flow<GeminiNanoStatus> = geminiNanoService.status

    private val _indexStats = mutableStateOf<KnowledgeIndexer.ReindexStats?>(null)
    val indexStats: State<KnowledgeIndexer.ReindexStats?> = _indexStats

    private val _knowledgeIndexing = mutableStateOf(false)
    val knowledgeIndexing: State<Boolean> = _knowledgeIndexing

    init {
        viewModelScope.launch(exceptionHandler) {
            refreshGeminiNanoIfEnabled()
        }
        viewModelScope.launch(exceptionHandler) {
            _indexStats.value = knowledgeIndexer.stats()
        }
    }

    fun getKnowledgeEmbeddingProvider(): Flow<KnowledgeEmbeddingProvider> =
        getPreference(
            IntKey(PrefsConstants.KNOWLEDGE_EMBEDDING_PROVIDER_KEY),
            KnowledgeEmbeddingProvider.NONE.id
        ).map { it.toKnowledgeEmbeddingProvider() }

    fun getKnowledgeEmbeddingModel(): Flow<String> = getPreference(
        stringPreferencesKey(PrefsConstants.KNOWLEDGE_EMBEDDING_MODEL_KEY),
        KnowledgeSettings.DEFAULT_EMBEDDING_MODEL
    )

    fun <T> getSettings(key: PrefsKey<T>, defaultValue: T): Flow<T> {
        return getPreference(key, defaultValue)
    }

    fun getAiProvider(): Flow<AiProvider> = getPreference(
        IntKey(PrefsConstants.AI_PROVIDER_KEY),
        AiProvider.None.id
    ).map { it.toAiProvider() }

    fun getExternalNotesFolderPath(): Flow<String?> {
        return getPreference(
            stringPreferencesKey(PrefsConstants.EXTERNAL_NOTES_FOLDER_URI),
            ""
        ).map { uri ->
            if (uri.isBlank()) null
            else fileUtilsRepository.getPathFromUri(uri)
        }
    }

    fun onEvent(event: IntegrationsEvent) {
        when (event) {
            is IntegrationsEvent.ToggleAiProvider -> {
                saveSettings(
                    IntKey(PrefsConstants.AI_PROVIDER_KEY),
                    if (event.enabled) AiProvider.OpenAI.id else AiProvider.None.id
                )
            }

            is IntegrationsEvent.SelectProvider -> {
                saveSettings(
                    IntKey(PrefsConstants.AI_PROVIDER_KEY),
                    event.provider.id
                )
                if (event.provider == AiProvider.GeminiNano) {
                    viewModelScope.launch { refreshGeminiNanoStatus() }
                }
            }

            is IntegrationsEvent.UpdateApiKey -> {
                event.provider.keyPrefsKey?.let { key ->
                    saveSettings(key, event.key)
                }
            }

            is IntegrationsEvent.UpdateModel -> {
                event.provider.modelPrefsKey?.let { key ->
                    saveSettings(key, event.model)
                }
                if (event.provider == AiProvider.GeminiNano) {
                    viewModelScope.launch(exceptionHandler) {
                        geminiNanoService.refreshStatus(event.model.toGeminiNanoMode())
                    }
                }
            }

            is IntegrationsEvent.ToggleCustomURL -> {
                if (!event.provider.supportsCustomUrl) return
                event.provider.customUrlEnabledPrefsKey?.let { key ->
                    saveSettings(key, event.enabled)
                }
                val defaultBaseUrl = event.provider.defaultBaseUrl
                if (!event.enabled && defaultBaseUrl != null) {
                    event.provider.customUrlPrefsKey?.let { key ->
                        saveSettings(key, defaultBaseUrl)
                    }
                }
            }

            is IntegrationsEvent.UpdateCustomURL -> {
                if (!event.provider.supportsCustomUrl) return
                event.provider.customUrlPrefsKey?.let { key ->
                    saveSettings(key, event.url)
                }
            }

            is IntegrationsEvent.ToggleAiTools -> {
                saveSettings(
                    BooleanKey(PrefsConstants.AI_TOOLS_ENABLED_KEY),
                    event.enabled
                )
            }

            is IntegrationsEvent.SelectKnowledgeEmbeddingProvider -> {
                saveSettings(
                    IntKey(PrefsConstants.KNOWLEDGE_EMBEDDING_PROVIDER_KEY),
                    event.provider.id
                )
            }

            is IntegrationsEvent.UpdateKnowledgeEmbeddingModel -> {
                saveSettings(
                    stringPreferencesKey(PrefsConstants.KNOWLEDGE_EMBEDDING_MODEL_KEY),
                    event.model
                )
            }

            IntegrationsEvent.ReindexKnowledge -> {
                viewModelScope.launch(exceptionHandler) {
                    _knowledgeIndexing.value = true
                    _indexStats.value = knowledgeIndexer.reindexAll()
                    _knowledgeIndexing.value = false
                }
            }

            IntegrationsEvent.ClearKnowledgeIndex -> {
                viewModelScope.launch(exceptionHandler) {
                    knowledgeIndexer.clearAll()
                    _indexStats.value = null
                }
            }

            IntegrationsEvent.DownloadGeminiNanoModel -> {
                viewModelScope.launch(exceptionHandler) {
                    val model = getPreference(
                        stringPreferencesKey(PrefsConstants.GEMINI_NANO_MODEL_KEY),
                        AiProvider.GeminiNano.defaultModel.orEmpty()
                    ).first()
                    geminiNanoService.download(model.toGeminiNanoMode())
                }
            }

            is IntegrationsEvent.SelectExternalNotesFolder -> {
                viewModelScope.launch {
                    updateExternalNotesFolder(event.folderUri)
                    unloadKoinModules(noteRoomModule)
                    loadKoinModules(noteMarkdownModule(event.folderUri))
                }
            }

            is IntegrationsEvent.SetExternalNotesEnabled -> {
                saveSettings(
                    BooleanKey(PrefsConstants.EXTERNAL_NOTES_ENABLED),
                    event.enabled
                )
                viewModelScope.launch {
                    if (event.enabled) {
                        val rootUri = getPreference(
                            stringPreferencesKey(PrefsConstants.EXTERNAL_NOTES_FOLDER_URI),
                            ""
                        ).first()
                        if (rootUri.isNotBlank()) {
                            loadKoinModules(noteMarkdownModule(rootUri))
                        }
                    } else {
                        loadKoinModules(noteRoomModule)
                    }
                }
            }
        }
    }

    private fun <T> saveSettings(key: PrefsKey<T>, value: T) {
        viewModelScope.launch {
            savePreference(key, value)
        }
    }

    private suspend fun refreshGeminiNanoStatus() {
        val model = getPreference(
            stringPreferencesKey(PrefsConstants.GEMINI_NANO_MODEL_KEY),
            AiProvider.GeminiNano.defaultModel.orEmpty()
        ).first()
        geminiNanoService.refreshStatus(model.toGeminiNanoMode())
    }

    private suspend fun refreshGeminiNanoIfEnabled() {
        val provider = getPreference(
            IntKey(PrefsConstants.AI_PROVIDER_KEY),
            AiProvider.None.id
        ).first().toAiProvider()
        if (provider != AiProvider.GeminiNano) return

        refreshGeminiNanoStatus()
    }
}
