package com.mhss.app.presentation.integrations.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mhss.app.data.rag.KnowledgeEmbeddingProvider
import com.mhss.app.data.rag.KnowledgeIndexer
import com.mhss.app.presentation.components.ExperimentalBadge
import com.mhss.app.ui.Res
import com.mhss.app.ui.clear_index
import com.mhss.app.ui.embedding_model
import com.mhss.app.ui.embedding_provider
import com.mhss.app.ui.index_stats
import com.mhss.app.ui.indexing
import com.mhss.app.ui.knowledge_index
import com.mhss.app.ui.knowledge_index_description
import com.mhss.app.ui.knowledge_index_empty
import com.mhss.app.ui.notes_img
import com.mhss.app.ui.reindex_all
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private fun KnowledgeEmbeddingProvider.labelRes() = when (this) {
    KnowledgeEmbeddingProvider.NONE -> Res.string.embedding_provider_none
    KnowledgeEmbeddingProvider.OLLAMA -> Res.string.embedding_provider_ollama
    KnowledgeEmbeddingProvider.OPENAI_COMPATIBLE -> Res.string.embedding_provider_openai_compatible
}

/**
 * Knowledge index (RAG) settings: embedding provider selection, model, stats
 * and manual re-index / clear actions.
 */
@Composable
fun KnowledgeIndexSettingsSection(
    provider: KnowledgeEmbeddingProvider,
    getModel: () -> Flow<String>,
    stats: KnowledgeIndexer.ReindexStats?,
    indexing: Boolean,
    onSelectProvider: (KnowledgeEmbeddingProvider) -> Unit,
    onModelSave: (String) -> Unit,
    onReindex: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(25.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(Res.drawable.notes_img),
                        contentDescription = "",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.knowledge_index),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.width(8.dp))
                    ExperimentalBadge()
                }
            }
            Text(
                text = stringResource(Res.string.knowledge_index_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(Modifier.height(12.dp))
            ProviderDropdown(
                selected = provider,
                onSelected = onSelectProvider
            )
            AnimatedVisibility(provider != KnowledgeEmbeddingProvider.NONE) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    val model by getModel().collectAsStateWithLifecycle(
                        com.mhss.app.data.rag.KnowledgeSettings.DEFAULT_EMBEDDING_MODEL
                    )
                    SavableTextField(
                        text = model,
                        label = stringResource(Res.string.embedding_model),
                        onSave = onModelSave
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (stats == null || stats.chunks == 0L) {
                Text(
                    text = stringResource(Res.string.knowledge_index_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = stringResource(
                        Res.string.index_stats,
                        stats.chunks,
                        stats.embeddedChunks
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    shape = RoundedCornerShape(12.dp),
                    enabled = !indexing,
                    onClick = onReindex
                ) {
                    Text(
                        text = if (indexing) stringResource(Res.string.indexing)
                        else stringResource(Res.string.reindex_all)
                    )
                }
                OutlinedButton(
                    shape = RoundedCornerShape(12.dp),
                    enabled = !indexing,
                    onClick = onClear,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(text = stringResource(Res.string.clear_index))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderDropdown(
    selected: KnowledgeEmbeddingProvider,
    onSelected: (KnowledgeEmbeddingProvider) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val options = remember { KnowledgeEmbeddingProvider.entries }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth(),
            value = stringResource(selected.labelRes()),
            onValueChange = {},
            readOnly = true,
            shape = RoundedCornerShape(16.dp),
            label = { Text(text = stringResource(Res.string.embedding_provider)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.textFieldColors()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(option.labelRes()),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}
