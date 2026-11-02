package com.mhss.app.data.repository

import com.mhss.app.domain.model.BackupFrequency
import com.mhss.app.domain.repository.BackupScheduler
import org.koin.core.annotation.Factory

/**
 * Desktop stub of [BackupScheduler]: the Android implementation enqueues a
 * WorkManager periodic job, which does not exist on the JVM. Automatic
 * backups are out of scope for the desktop port (phase 1); manual
 * backup/restore via the folder picker still works.
 */
@Factory(binds = [BackupScheduler::class])
class BackupSchedulerImplJvm : BackupScheduler {

    override suspend fun scheduleBackup(
        folderUri: String,
        frequency: BackupFrequency,
        frequencyAmount: Int
    ) { /* no-op on desktop */ }

    override suspend fun cancelBackup() { /* no-op on desktop */ }
}
