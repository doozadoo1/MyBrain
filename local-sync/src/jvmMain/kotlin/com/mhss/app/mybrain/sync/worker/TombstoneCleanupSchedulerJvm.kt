package com.mhss.app.mybrain.sync.worker

import com.mhss.app.mybrain.sync.domain.CleanupDeletedEntitiesUseCase
import com.mhss.app.mybrain.sync.domain.TombstoneCleanupScheduler
import com.mhss.app.mybrain.sync.util.TOMBSTONE_CLEANUP_INTERVAL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.annotation.Factory
import java.util.concurrent.atomic.AtomicBoolean

/**
 * No WorkManager on desktop: run the cleanup in a background coroutine while
 * the app is alive.
 */
@Factory(binds = [TombstoneCleanupScheduler::class])
class TombstoneCleanupSchedulerJvm(
    private val cleanupDeletedEntities: CleanupDeletedEntitiesUseCase
) : TombstoneCleanupScheduler {

    private val started = AtomicBoolean(false)

    override fun schedule() {
        if (!started.compareAndSet(false, true)) return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            while (true) {
                delay(TOMBSTONE_CLEANUP_INTERVAL)
                runCatching { cleanupDeletedEntities() }
            }
        }
    }
}
