package com.mhss.app.data.tools

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolBase
import ai.koog.serialization.typeToken
import com.mhss.app.data.rag.KnowledgeIndexer
import com.mhss.app.data.nowMillis
import com.mhss.app.domain.model.DiaryEntry
import com.mhss.app.domain.model.Mood
import com.mhss.app.domain.use_case.AddDiaryEntryUseCase
import com.mhss.app.domain.use_case.GetDiaryEntryUseCase
import com.mhss.app.domain.use_case.SearchEntriesUseCase
import kotlinx.serialization.Serializable
import org.koin.core.annotation.Factory
import kotlin.uuid.Uuid

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
@Factory
class DiaryToolSet(
    private val addDiaryEntry: AddDiaryEntryUseCase,
    private val searchEntries: SearchEntriesUseCase,
    private val getDiaryEntry: GetDiaryEntryUseCase,
    private val knowledgeIndexer: KnowledgeIndexer
) {
    private val createDiaryEntryTool = object : Tool<CreateDiaryEntryArgs, DiaryEntryIdResult>(
        argsType = typeToken<CreateDiaryEntryArgs>(),
        resultType = typeToken<DiaryEntryIdResult>(),
        name = CREATE_DIARY_ENTRY_TOOL,
        description = "Create diary entry. Returns ID."
    ) {
        override suspend fun execute(args: CreateDiaryEntryArgs): DiaryEntryIdResult {
            val id = Uuid.generateV7().toString()
            val entry = DiaryEntry(
                title = args.title,
                content = args.content,
                createdDate = nowMillis(),
                updatedDate = nowMillis(),
                mood = args.mood,
                id = id
            )
            addDiaryEntry(entry)
            runCatching { knowledgeIndexer.indexDiaryEntry(entry) }
            return DiaryEntryIdResult(createdDiaryEntryId = id)
        }
    }

    private val searchDiaryEntriesTool = object : Tool<SearchDiaryEntriesArgs, SearchDiaryEntriesResult>(
        argsType = typeToken<SearchDiaryEntriesArgs>(),
        resultType = typeToken<SearchDiaryEntriesResult>(),
        name = SEARCH_DIARY_ENTRIES_TOOL,
        description = "Search diary entries by title/content (partial match, content truncated to 100 chars)."
    ) {
        override suspend fun execute(args: SearchDiaryEntriesArgs): SearchDiaryEntriesResult =
            SearchDiaryEntriesResult(searchEntries(args.query).map { it.toToolResult() })
    }

    private val getDiaryEntryTool = object : Tool<GetDiaryEntryArgs, DiaryEntryResult>(
        argsType = typeToken<GetDiaryEntryArgs>(),
        resultType = typeToken<DiaryEntryResult>(),
        name = GET_DIARY_ENTRY_TOOL,
        description = "Get diary entry by ID."
    ) {
        override suspend fun execute(args: GetDiaryEntryArgs): DiaryEntryResult =
            DiaryEntryResult(getDiaryEntry.invoke(args.id)?.toToolResult())
    }

    val tools: List<ToolBase<*, *>> = listOf(
        createDiaryEntryTool,
        searchDiaryEntriesTool,
        getDiaryEntryTool
    )
}

@Serializable
data class CreateDiaryEntryArgs(
    val title: String,
    val content: String,
    val mood: Mood
)

@Serializable
data class SearchDiaryEntriesArgs(val query: String)

@Serializable
data class GetDiaryEntryArgs(val id: String)

@Serializable
data class DiaryEntryIdResult(val createdDiaryEntryId: String)

@Serializable
data class SearchDiaryEntriesResult(val entries: List<DiaryEntryToolResult>)

@Serializable
data class DiaryEntryResult(val entry: DiaryEntryToolResult?)
