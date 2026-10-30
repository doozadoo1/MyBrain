package com.mhss.app.presentation.integrations

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mhss.app.preferences.PrefsConstants
import com.mhss.app.preferences.domain.model.booleanPreferencesKey
import com.mhss.app.presentation.integrations.components.ExternalNotesCard
import com.mhss.app.presentation.integrations.components.KnowledgeIndexSettingsSection
import com.mhss.app.ui.Res
import com.mhss.app.ui.components.common.MyBrainAppBar
import com.mhss.app.ui.integrations
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.mhss.app.data.rag.KnowledgeEmbeddingProvider

@Composable
fun IntegrationsScreen(
    viewModel: IntegrationsViewModel = koinViewModel()
) {

    Scaffold(
        topBar = {
            MyBrainAppBar(
                title = stringResource(Res.string.integrations)
            )
        }
    ) { paddingValues ->
        LazyColumn(modifier = Modifier.fillMaxWidth().imePadding(), contentPadding = paddingValues) {
            item {
                AiProviderSection(
                    getAiProvider = viewModel::getAiProvider,
                    getStringSetting = viewModel::getSettings,
                    getBooleanSetting = viewModel::getSettings,
                    getGeminiNanoStatus = { viewModel.geminiNanoStatus },
                    onEvent = viewModel::onEvent
                )
            }
            item {
                val isExternalNotesEnabled by viewModel.getSettings(
                    booleanPreferencesKey(PrefsConstants.EXTERNAL_NOTES_ENABLED),
                    false
                ).collectAsStateWithLifecycle(false)
                val selectedFolder by viewModel.getExternalNotesFolderPath()
                    .collectAsStateWithLifecycle(null)
                ExternalNotesCard(
                    isEnabled = isExternalNotesEnabled,
                    selectedFolder = selectedFolder,
                    onSwitchToggled = {
                        viewModel.onEvent(IntegrationsEvent.SetExternalNotesEnabled(it))
                    },
                    onFolderSelected = {
                        viewModel.onEvent(IntegrationsEvent.SelectExternalNotesFolder(it))
                    }
                )
            }
            item {
                val provider by viewModel.getKnowledgeEmbeddingProvider()
                    .collectAsStateWithLifecycle(KnowledgeEmbeddingProvider.NONE)
                val stats by viewModel.indexStats
                val indexing by viewModel.knowledgeIndexing
                KnowledgeIndexSettingsSection(
                    provider = provider,
                    getModel = viewModel::getKnowledgeEmbeddingModel,
                    stats = stats,
                    indexing = indexing,
                    onSelectProvider = {
                        viewModel.onEvent(IntegrationsEvent.SelectKnowledgeEmbeddingProvider(it))
                    },
                    onModelSave = {
                        viewModel.onEvent(IntegrationsEvent.UpdateKnowledgeEmbeddingModel(it))
                    },
                    onReindex = { viewModel.onEvent(IntegrationsEvent.ReindexKnowledge) },
                    onClear = { viewModel.onEvent(IntegrationsEvent.ClearKnowledgeIndex) }
                )
            }
        }
    }
}
