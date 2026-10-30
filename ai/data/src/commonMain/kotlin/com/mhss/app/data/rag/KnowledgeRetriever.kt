package com.mhss.app.data.rag

import com.mhss.app.data.formatDateTimeForLLM
import com.mhss.app.database.dao.BookmarkDao
import com.mhss.app.database.dao.DiaryDao
import com.mhss.app.database.dao.KnowledgeChunkDao
import com.mhss.app.database.dao.NoteDao
import com.mhss.app.database.entity.KnowledgeChunkEntity
import kotlinx.serialization.Serializable
import org.koin.core.annotation.Single
import kotlin.math.sqrt

/**
 * Hybrid retrieval over the knowledge index:
 *  - keyword recall via FTS4 (BM25 ranks)
 *  - semantic recall via cosine similarity over stored embeddings
 *
 * Both rank lists are fused with Reciprocal Rank Fusion, then each candidate
 * is validated against its live source (dropped when deleted, flagged stale
 * when the note/diary/bookmark changed after indexing).
 */
@Single
class KnowledgeRetriever(
    private val knowledgeChunkDao: KnowledgeChunkDao,
    private val noteDao: NoteDao,
    private val diaryDao: DiaryDao,
    private val bookmarkDao: BookmarkDao,
    private val embeddingServiceResolver: EmbeddingServiceResolver
) {

    @Serializable
    data class KnowledgeHit(
        val type: String,
        val sourceId: String,
        val title: String,
        val text: String,
        val date: String,
        val url: String? = null,
        val stale: Boolean = false
    )

    private data class ScoredChunk(
        val chunk: KnowledgeChunkEntity,
        val rank: Int,
        val stale: Boolean
    )

    suspend fun retrieve(
        query: String,
        topK: Int = KnowledgeSettings.DEFAULT_TOP_K
    ): List<KnowledgeHit> = runCatching {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        // 1) Fused candidate scores (RRF over both recall paths)
        val fused = HashMap<String, Float>()

        buildFtsMatchQuery(trimmed)?.let { matchQuery ->
            knowledgeChunkDao.searchFts(matchQuery, SEARCH_CANDIDATES)
                .forEachIndexed { index, row ->
                    fused.merge(row.id, rrfScore(index)) { acc, value -> acc + value }
                }
        }

        embeddingServiceResolver.resolve()?.let { service ->
            service.embed(listOf(trimmed))
                ?.firstOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.let { queryVector ->
                    rankByCosine(queryVector).forEach { (id, score) ->
                        fused.merge(id, score) { acc, value -> acc + value }
                    }
                }
        }

        if (fused.isEmpty()) return emptyList()

        val orderedIds = fused.entries
            .sortedByDescending { it.value }
            .map { it.key }

        val candidates = knowledgeChunkDao.getChunksByIds(orderedIds.take(CANDIDATE_POOL))
        if (candidates.isEmpty()) return emptyList()

        // 2) Validate candidates against the live sources
        val noteIds = candidates.filter { it.sourceType == KnowledgeSource.NOTE.id }.map { it.sourceId }
        val entryIds =
            candidates.filter { it.sourceType == KnowledgeSource.DIARY_ENTRY.id }.map { it.sourceId }
        val bookmarkIds =
            candidates.filter { it.sourceType == KnowledgeSource.BOOKMARK.id }.map { it.sourceId }

        val notes = if (noteIds.isEmpty()) emptyMap()
        else noteDao.getNotesByIds(noteIds).associateBy { it.id }
        val entries = if (entryIds.isEmpty()) emptyMap()
        else diaryDao.getEntriesByIds(entryIds).associateBy { it.id }
        val bookmarks = if (bookmarkIds.isEmpty()) emptyMap()
        else bookmarkDao.getBookmarksByIds(bookmarkIds).associateBy { it.id }

        val rankById = orderedIds.withIndex().associate { (index, id) -> id to index }

        val scored = candidates
            .mapNotNull { chunk ->
                when (chunk.sourceType) {
                    KnowledgeSource.NOTE.id -> {
                        val note = notes[chunk.sourceId] ?: return@mapNotNull null
                        ScoredChunk(
                            chunk = chunk,
                            rank = rankById[chunk.id] ?: Int.MAX_VALUE,
                            stale = note.updatedDate != chunk.sourceUpdatedDate
                        )
                    }
                    KnowledgeSource.DIARY_ENTRY.id -> {
                        val entry = entries[chunk.sourceId] ?: return@mapNotNull null
                        ScoredChunk(
                            chunk = chunk,
                            rank = rankById[chunk.id] ?: Int.MAX_VALUE,
                            stale = entry.updatedDate != chunk.sourceUpdatedDate
                        )
                    }
                    KnowledgeSource.BOOKMARK.id -> {
                        val bookmark = bookmarks[chunk.sourceId] ?: return@mapNotNull null
                        ScoredChunk(
                            chunk = chunk,
                            rank = rankById[chunk.id] ?: Int.MAX_VALUE,
                            stale = bookmark.updatedDate != chunk.sourceUpdatedDate
                        )
                    }
                    else -> null
                }
            }
            .sortedBy { it.rank }
            .take(topK)

        scored.map { candidate ->
            KnowledgeHit(
                type = candidate.chunk.sourceType.toDisplayName(),
                sourceId = candidate.chunk.sourceId,
                title = candidate.chunk.title,
                text = candidate.chunk.text.take(KnowledgeSettings.MAX_HIT_TEXT_LENGTH),
                date = candidate.chunk.sourceUpdatedDate.formatDateTimeForLLM(),
                url = bookmarks[candidate.chunk.sourceId]?.url
                    ?.takeIf { candidate.chunk.sourceType == KnowledgeSource.BOOKMARK.id },
                stale = candidate.stale
            )
        }
    }.getOrDefault(emptyList())

    private suspend fun rankByCosine(queryVector: List<Float>): List<Pair<String, Float>> =
        knowledgeChunkDao.getEmbeddedChunks()
            .mapNotNull { chunk ->
                val vector = chunk.embedding?.toFloatList()
                if (vector.isNullOrEmpty()) return@mapNotNull null
                val similarity = cosine(queryVector, vector)
                if (similarity >= SIMILARITY_FLOOR) chunk.id to similarity else null
            }
            .sortedByDescending { it.second }
            .take(SEARCH_CANDIDATES)
            .mapIndexed { index, (id, _) -> id to rrfScore(index) }

    private fun rrfScore(rank: Int, k: Int = RRF_K): Float = 1f / (k + rank + 1)

    private fun cosine(a: List<Float>, b: List<Float>): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator > 0f) dot / denominator else 0f
    }

    private fun String.toDisplayName(): String =
        KnowledgeSource.entries.firstOrNull { it.id == this }?.displayName
            ?: this.replace('_', ' ')

    companion object {
        private const val SEARCH_CANDIDATES = 24
        private const val CANDIDATE_POOL = 40
        private const val RRF_K = 60
        private const val SIMILARITY_FLOOR = 0.2f

        /**
         * Builds a safe FTS4 query: each term becomes a prefix query and the
         * terms are OR-ed together so a hit on any word counts; BM25 ranking
         * sorts the rest.
         */
        fun buildFtsMatchQuery(query: String): String? {
            val terms = query.split(Regex("[^\\p{L}\\p{N}]+"))
                .filter { it.length > 1 }
                .distinctBy { it.lowercase() }
                .take(10)
            if (terms.isEmpty()) return null
            return terms.joinToString(" OR ") { "${it.lowercase()}*" }
        }
    }
}
