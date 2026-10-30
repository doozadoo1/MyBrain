package com.mhss.app.data.rag

import com.mhss.app.database.dao.BookmarkDao
import com.mhss.app.database.dao.DiaryDao
import com.mhss.app.database.dao.KnowledgeChunkDao
import com.mhss.app.database.dao.NoteDao
import com.mhss.app.database.entity.KnowledgeChunkEntity
import com.mhss.app.database.entity.KnowledgeChunkFtsEntity
import com.mhss.app.database.entity.toBookmark
import com.mhss.app.database.entity.toDiaryEntry
import com.mhss.app.database.entity.toNote
import com.mhss.app.domain.model.Bookmark
import com.mhss.app.domain.model.DiaryEntry
import com.mhss.app.domain.model.Note
import org.koin.core.annotation.Single

/**
 * Builds and maintains the knowledge index used for retrieval.
 *
 * Sources (notes, diary entries, bookmarks) are chunked, embedded (when an
 * embedding provider is configured) and stored in the `knowledge_chunks` +
 * `knowledge_chunks_fts` tables. Indexing never throws: a failure to embed
 * only degrades that chunk to keyword-only retrieval.
 */
@Single
class KnowledgeIndexer(
    private val knowledgeChunkDao: KnowledgeChunkDao,
    private val noteDao: NoteDao,
    private val diaryDao: DiaryDao,
    private val bookmarkDao: BookmarkDao,
    private val embeddingServiceResolver: EmbeddingServiceResolver
) {

    data class ReindexStats(
        val notes: Int = 0,
        val diaryEntries: Int = 0,
        val bookmarks: Int = 0,
        val chunks: Long,
        val embeddedChunks: Long
    )

    /** Cheap counts for the settings screen, without touching the source tables. */
    suspend fun stats(): ReindexStats? = runCatching {
        ReindexStats(
            chunks = knowledgeChunkDao.countChunks(),
            embeddedChunks = knowledgeChunkDao.countEmbedded()
        )
    }.getOrNull()

    suspend fun indexNote(note: Note) = runCatching {
        index(
            source = KnowledgeSource.NOTE,
            sourceId = note.id,
            title = note.title,
            body = note.content,
            sourceUpdatedDate = note.updatedDate
        )
    }

    suspend fun indexDiaryEntry(entry: DiaryEntry) = runCatching {
        index(
            source = KnowledgeSource.DIARY_ENTRY,
            sourceId = entry.id,
            title = entry.title,
            body = "Mood: ${entry.mood.name}\n${entry.content}",
            sourceUpdatedDate = entry.updatedDate
        )
    }

    suspend fun indexBookmark(bookmark: Bookmark) = runCatching {
        index(
            source = KnowledgeSource.BOOKMARK,
            sourceId = bookmark.id,
            title = bookmark.title.ifBlank { bookmark.url },
            body = buildString {
                append(bookmark.title)
                if (bookmark.description.isNotBlank()) {
                    append("\n")
                    append(bookmark.description)
                }
                append("\n")
                append(bookmark.url)
            },
            sourceUpdatedDate = bookmark.updatedDate
        )
    }

    suspend fun removeNote(noteId: String) = removeSource(KnowledgeSource.NOTE, noteId)

    suspend fun removeDiaryEntry(entryId: String) =
        removeSource(KnowledgeSource.DIARY_ENTRY, entryId)

    suspend fun removeBookmark(bookmarkId: String) =
        removeSource(KnowledgeSource.BOOKMARK, bookmarkId)

    suspend fun removeSource(source: KnowledgeSource, sourceId: String) = runCatching {
        knowledgeChunkDao.replaceSourceChunks(
            sourceType = source.id,
            sourceId = sourceId,
            chunks = emptyList(),
            ftsRows = emptyList()
        )
    }

    /** Full rebuild: clears the index and re-indexes every note/diary/bookmark. */
    suspend fun reindexAll(): ReindexStats? = runCatching {
        knowledgeChunkDao.clearAll()
        val notes = noteDao.getAllFullNotes()
        val entries = diaryDao.getAllFullEntries()
        val bookmarks = bookmarkDao.getAllFullBookmarks()
        notes.forEach { indexNote(it.toNote()) }
        entries.forEach { indexDiaryEntry(it.toDiaryEntry()) }
        bookmarks.forEach { indexBookmark(it.toBookmark()) }
        ReindexStats(
            notes = notes.size,
            diaryEntries = entries.size,
            bookmarks = bookmarks.size,
            chunks = knowledgeChunkDao.countChunks(),
            embeddedChunks = knowledgeChunkDao.countEmbedded()
        )
    }.getOrNull()

    suspend fun clearAll() = runCatching { knowledgeChunkDao.clearAll() }

    /**
     * Diff-maintenance pass: indexes sources that are new/changed compared to the
     * index and removes chunks of deleted sources. Cheap enough to run after
     * every debounced DB change notification.
     */
    suspend fun indexOutdated(): Int = runCatching {
        var reindexed = 0

        val liveNotes = noteDao.getNoteIndexStates().associate { it.id to it.updatedDate }
        val indexedNotes =
            knowledgeChunkDao.getIndexedSources(KnowledgeSource.NOTE.id)
                .associate { it.sourceId to it.sourceUpdatedDate }
        reindexed += reconcile(
            live = liveNotes,
            indexed = indexedNotes,
            type = KnowledgeSource.NOTE,
            fetch = { noteDao.getNote(it)?.toNote() }
        )

        val liveEntries = diaryDao.getEntryIndexStates().associate { it.id to it.updatedDate }
        val indexedEntries =
            knowledgeChunkDao.getIndexedSources(KnowledgeSource.DIARY_ENTRY.id)
                .associate { it.sourceId to it.sourceUpdatedDate }
        reindexed += reconcile(
            live = liveEntries,
            indexed = indexedEntries,
            type = KnowledgeSource.DIARY_ENTRY,
            fetch = { diaryDao.getEntry(it)?.toDiaryEntry() }
        )

        val liveBookmarks = bookmarkDao.getBookmarkIndexStates().associate { it.id to it.updatedDate }
        val indexedBookmarks =
            knowledgeChunkDao.getIndexedSources(KnowledgeSource.BOOKMARK.id)
                .associate { it.sourceId to it.sourceUpdatedDate }
        reindexed += reconcile(
            live = liveBookmarks,
            indexed = indexedBookmarks,
            type = KnowledgeSource.BOOKMARK,
            fetch = { bookmarkDao.getBookmark(it)?.toBookmark() }
        )

        reindexed
    }.getOrDefault(0)

    private suspend fun reconcile(
        live: Map<String, Long>,
        indexed: Map<String, Long>,
        type: KnowledgeSource,
        fetch: suspend (String) -> Any?
    ): Int {
        var count = 0
        live.forEach { (id, updatedAt) ->
            if (indexed[id] != updatedAt) {
                fetch(id)?.let { source ->
                    when (type) {
                        KnowledgeSource.NOTE -> (source as Note).let { indexNote(it) }
                        KnowledgeSource.DIARY_ENTRY -> (source as DiaryEntry).let { indexDiaryEntry(it) }
                        KnowledgeSource.BOOKMARK -> (source as Bookmark).let { indexBookmark(it) }
                    }
                    count++
                }
            }
        }
        indexed.keys.filter { it !in live }.forEach {
            removeSource(type, it)
            count++
        }
        return count
    }

    private suspend fun index(
        source: KnowledgeSource,
        sourceId: String,
        title: String,
        body: String,
        sourceUpdatedDate: Long
    ) {
        val chunks = TextChunker.chunk(title = title, content = body)
        if (chunks.isEmpty()) {
            knowledgeChunkDao.replaceSourceChunks(source.id, sourceId, emptyList(), emptyList())
            return
        }

        // Reuse stored embeddings when the chunk text did not change
        val existing = knowledgeChunkDao.getChunksBySource(source.id, sourceId)
            .associateBy { it.chunkIndex }

        val rows = chunks.map { chunk ->
            val reuse = existing[chunk.index]
                ?.takeIf { it.text == chunk.text }
                ?.embedding
            KnowledgeChunkEntity(
                id = chunkId(source.id, sourceId, chunk.index),
                sourceType = source.id,
                sourceId = sourceId,
                chunkIndex = chunk.index,
                title = title,
                text = chunk.text,
                sourceUpdatedDate = sourceUpdatedDate,
                embedding = reuse
            )
        }
        val rowsWithEmbeddings = embedMissing(rows)

        knowledgeChunkDao.replaceSourceChunks(
            sourceType = source.id,
            sourceId = sourceId,
            chunks = rowsWithEmbeddings,
            ftsRows = rowsWithEmbeddings.map {
                KnowledgeChunkFtsEntity(id = it.id, title = it.title, text = it.text)
            }
        )
    }

    /**
     * Fills in embeddings for rows missing one, in batches. Returns the rows
     * unchanged from the first failed/unavailable batch onwards so a dead
     * provider doesn't stall indexing (chunks stay FTS-only).
     */
    private suspend fun embedMissing(
        rows: List<KnowledgeChunkEntity>
    ): List<KnowledgeChunkEntity> {
        val service = embeddingServiceResolver.resolve() ?: return rows

        val out = rows.toMutableList()
        val toEmbed = out.withIndex().filter { it.value.embedding == null }
        for (group in toEmbed.chunked(KnowledgeSettings.EMBED_BATCH_SIZE)) {
            val vectors = service.embed(group.map { it.value.text }) ?: break
            for (indexed in group.withIndex()) {
                vectors.getOrNull(indexed.index)
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { vector ->
                        out[indexed.value.index] = indexed.value.value.copy(
                            embedding = vector.toFloatBytes()
                        )
                    }
            }
        }
        return out
    }

    internal companion object {
        fun chunkId(sourceType: String, sourceId: String, chunkIndex: Int) =
            "$sourceType#$sourceId#$chunkIndex"
    }
}

/** Little-endian float vector <-> blob conversion (platform-independent). */
internal fun List<Float>.toFloatBytes(): ByteArray {
    val bytes = ByteArray(size * 4)
    forEachIndexed { index, value ->
        val bits = value.toRawBits()
        bytes[index * 4] = (bits and 0xFF).toByte()
        bytes[index * 4 + 1] = ((bits shr 8) and 0xFF).toByte()
        bytes[index * 4 + 2] = ((bits shr 16) and 0xFF).toByte()
        bytes[index * 4 + 3] = ((bits shr 24) and 0xFF).toByte()
    }
    return bytes
}

internal fun ByteArray.toFloatList(): List<Float> {
    if (size % 4 != 0) return emptyList()
    val result = ArrayList<Float>(size / 4)
    for (offset in 0 until size step 4) {
        val bits = ((this[offset].toInt() and 0xFF)) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16) or
            ((this[offset + 3].toInt() and 0xFF) shl 24)
        result.add(Float.fromBits(bits))
    }
    return result
}
