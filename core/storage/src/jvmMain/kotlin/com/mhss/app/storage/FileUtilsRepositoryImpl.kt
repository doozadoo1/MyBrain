package com.mhss.app.storage

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Named

/**
 * Desktop implementation: storage "URIs" are plain absolute filesystem paths.
 * Semantics mirror the Android [StorageManagerImpl] (name sanitizing,
 * unique-file loops, failure results) but on java.io/java.nio instead of SAF.
 */
@Factory(binds = [FileUtilsRepository::class])
class FileUtilsRepositoryImpl(
    @Named("ioDispatcher") private val ioDispatcher: CoroutineDispatcher
) : FileUtilsRepository {

    override suspend fun takePersistablePermission(uri: String) {
        // No permission model on desktop: plain filesystem access.
    }

    override suspend fun getPathFromUri(uri: String): String = uri
}
