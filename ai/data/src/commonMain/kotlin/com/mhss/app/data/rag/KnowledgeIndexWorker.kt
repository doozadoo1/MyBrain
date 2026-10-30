package com.mhss.app.data.rag

import com.mhss.app.database.sync.LocalChangeObserver
import org.koin.core.annotation.Single

/**
 * Keeps the knowledge index in sync with the database.
 *
 * All mutating repositories notify [LocalChangeObserver] (UI edits, AI tool
 * writes and incoming sync merges), so observing it with the conflated channel
 * coalesces bursts. Each notification triggers one cheap diff pass that only
 * re-embeds sources whose `updated_date` changed since indexing.
 *
 * `observeForever` never returns; run it on an application-scoped coroutine and
 * cancel it when the scope dies.
 */
@Single
class KnowledgeIndexWorker(
    private val indexer: KnowledgeIndexer,
    private val changeObserver: LocalChangeObserver
) {

    suspend fun observeForever() {
        changeObserver.changes.collect {
            runCatching { indexer.indexOutdated() }
        }
    }
}
