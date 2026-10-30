package com.mhss.app.data.tools

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolBase
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import com.mhss.app.data.rag.KnowledgeIndexer
import com.mhss.app.data.nowMillis
import com.mhss.app.domain.model.Note
import com.mhss.app.domain.use_case.CreateNoteFolderUseCase
import com.mhss.app.domain.use_case.GetNoteFolderUseCase
import com.mhss.app.domain.use_case.GetNoteUseCase
import com.mhss.app.domain.use_case.SearchNoteFoldersByNameUseCase
import com.mhss.app.domain.use_case.SearchNotesUseCase
import com.mhss.app.domain.use_case.UpsertNoteUseCase
import com.mhss.app.domain.use_case.UpsertNotesUseCase
import kotlinx.serialization.Serializable
import org.koin.core.annotation.Factory

@Factory
class NoteToolSet(
    private val upsertNote: UpsertNoteUseCase,
    private val upsertNotes: UpsertNotesUseCase,
    private val searchNotesByName: SearchNotesUseCase,
    private val getNote: GetNoteUseCase,
    private val createFolderUseCase: CreateNoteFolderUseCase,
    private val searchNoteFoldersByName: SearchNoteFoldersByNameUseCase,
    private val getNoteFolder: GetNoteFolderUseCase,
    private val knowledgeIndexer: KnowledgeIndexer
) {
    private val searchNotesTool = object : Tool<SearchNotesArgs, SearchNotesResult>(
        argsType = typeToken<SearchNotesArgs>(),
        resultType = typeToken<SearchNotesResult>(),
        name = SEARCH_NOTES_TOOL,
        description = "Search notes by title/content (partial match, content truncated to 100 chars). If the query is empty, returns all notes."
    ) {
        override suspend fun execute(args: SearchNotesArgs): SearchNotesResult =
            SearchNotesResult(searchNotesByName(args.query).map { it.toToolResult() })
    }

    private val createNoteTool = object : Tool<CreateNoteArgs, NoteIdResult>(
        argsType = typeToken<CreateNoteArgs>(),
        resultType = typeToken<NoteIdResult>(),
        name = CREATE_NOTE_TOOL,
        description = "Create a note. Returns ID."
    ) {
        override suspend fun execute(args: CreateNoteArgs): NoteIdResult {
            if (args.folderId != null) {
                runCatching { getNoteFolder(args.folderId) }.getOrNull()
                    ?: throw IllegalArgumentException("No folder found with ID: '${args.folderId}'. The note was not created.. folderId must be a valid ID. If you only have folder name, use the $SEARCH_NOTE_FOLDERS_TOOL tool to find the folder's ID or keep the folderId null to put in the root folder.")
            }
            val note = Note(
                title = args.title,
                content = args.content,
                folderId = args.folderId,
                pinned = args.pinned,
                createdDate = nowMillis(),
                updatedDate = nowMillis()
            )
            val createdNoteId = upsertNote(note)
            runCatching { knowledgeIndexer.indexNote(note) }
            return NoteIdResult(createdNoteId = createdNoteId)
        }
    }

    private val createMultipleNotesTool = object : Tool<CreateMultipleNotesArgs, NoteIdsResult>(
        argsType = typeToken<CreateMultipleNotesArgs>(),
        resultType = typeToken<NoteIdsResult>(),
        name = CREATE_MULTIPLE_NOTES_TOOL,
        description = "Create multiple notes. Returns IDs."
    ) {
        override suspend fun execute(args: CreateMultipleNotesArgs): NoteIdsResult {
            args.notes.forEach { input ->
                if (input.folderId != null) {
                    runCatching { getNoteFolder(input.folderId) }.getOrNull()
                        ?: throw IllegalArgumentException("No folder found with ID: '${input.folderId}'. folderId must be a valid ID. If you only have the folder name, use the $SEARCH_NOTE_FOLDERS_TOOL tool to find the folder's ID first. The notes were not created.")
                }
            }
            val noteModels = args.notes.map { input ->
                Note(
                    title = input.title,
                    content = input.content,
                    folderId = input.folderId,
                    pinned = input.pinned,
                    createdDate = nowMillis(),
                    updatedDate = nowMillis()
                )
            }
            return NoteIdsResult(createdNoteIds = upsertNotes(noteModels)).also { result ->
                runCatching {
                    noteModels.forEach { knowledgeIndexer.indexNote(it) }
                }
            }
        }
    }

    private val getNoteByIdTool = object : Tool<GetNoteByIdArgs, NoteResult>(
        argsType = typeToken<GetNoteByIdArgs>(),
        resultType = typeToken<NoteResult>(),
        name = GET_NOTE_BY_ID_TOOL,
        description = "Get full note by ID."
    ) {
        override suspend fun execute(args: GetNoteByIdArgs): NoteResult {
            return NoteResult(
                getNote(args.id)?.toToolResult()
                    ?: throw IllegalArgumentException("No note found with ID: '${args.id}'. id must be a valid ID. If you only have the title, use the $SEARCH_NOTES_TOOL tool first to find the ID. The operation did not proceed.")
            )
        }
    }

    private val searchFoldersTool = object : Tool<SearchFoldersArgs, SearchNoteFoldersResult>(
        argsType = typeToken<SearchFoldersArgs>(),
        resultType = typeToken<SearchNoteFoldersResult>(),
        name = SEARCH_NOTE_FOLDERS_TOOL,
        description = "Search folders by name (partial match). Returns folder IDs."
    ) {
        override suspend fun execute(args: SearchFoldersArgs): SearchNoteFoldersResult =
            SearchNoteFoldersResult(searchNoteFoldersByName(args.name).map { it.toToolResult() })
    }

    private val createFolderTool = object : Tool<CreateFolderArgs, CreateNoteFolderResult>(
        argsType = typeToken<CreateFolderArgs>(),
        resultType = typeToken<CreateNoteFolderResult>(),
        name = CREATE_NOTE_FOLDER_TOOL,
        description = "Create a note folder. Returns ID."
    ) {
        override suspend fun execute(args: CreateFolderArgs): CreateNoteFolderResult =
            CreateNoteFolderResult(folderId = createFolderUseCase(folderName = args.name))
    }

    val tools = listOf(
        searchNotesTool,
        createNoteTool,
        createMultipleNotesTool,
        getNoteByIdTool,
        searchFoldersTool,
        createFolderTool
    )
}

@Serializable
data class SearchNotesArgs(
    @property:LLMDescription("Search query") val query: String
)

@Serializable
data class CreateNoteArgs(
    val title: String,
    val content: String,
    @property:LLMDescription("Folder ID. If not provided, the note will be in the root folder. Use $SEARCH_NOTE_FOLDERS_TOOL to find an ID. Keep null if user didn't ask for specific folder.") val folderId: String? = null,
    val pinned: Boolean = false
)

@Serializable
data class CreateMultipleNotesArgs(val notes: List<NoteInput>)

@Serializable
data class GetNoteByIdArgs(val id: String)

@Serializable
data class SearchFoldersArgs(
    @property:LLMDescription("Folder name query") val name: String
)

@Serializable
data class CreateFolderArgs(
    @property:LLMDescription("Folder name") val name: String
)

@Serializable
data class NoteInput(
    val title: String,
    val content: String,
    @property:LLMDescription("Folder ID (null = root), Keep null if user didn't ask for specific folder.") val folderId: String? = null,
    val pinned: Boolean = false
)

@Serializable
data class SearchNoteFoldersResult(val folders: List<NoteFolderToolResult>)

@Serializable
data class SearchNotesResult(val notes: List<NoteToolResult>)

@Serializable
data class NoteIdResult(val createdNoteId: String)

@Serializable
data class NoteIdsResult(val createdNoteIds: List<String>)

@Serializable
data class NoteResult(val note: NoteToolResult)

@Serializable
data class CreateNoteFolderResult(val folderId: String)
