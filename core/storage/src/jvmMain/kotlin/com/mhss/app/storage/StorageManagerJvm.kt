package com.mhss.app.storage

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Named
import java.io.File

/**
 * Desktop implementation: storage "URIs" are plain absolute filesystem paths.
 * Mirrors the semantics of the Android [StorageManagerImpl] (SAF/DocumentFile)
 * on plain java.io files: name sanitizing, unique-file loops and error results
 * are kept identical so backup/restore behaves the same on both platforms.
 */
@Factory(binds = [StorageManager::class])
class StorageManagerJvm(
    @Named("ioDispatcher") private val ioDispatcher: CoroutineDispatcher
) : StorageManager {

    override suspend fun writeBufferedFile(
        directoryUri: String,
        fileName: String,
        mimeType: String,
        block: suspend BufferedFileWriter.() -> Unit
    ) = withContext(ioDispatcher) {
        val dir = File(directoryUri)
        val destinationFile = File(dir, fileName.trim().sanitizeForFileName().ifBlank { "Untitled" })
        try {
            require(destinationFile.createNewFile()) { "Failed to create file" }

            destinationFile.bufferedWriter().use { writer ->
                val bufferedFileWriter = object : BufferedFileWriter {
                    override suspend fun write(value: String, startIndex: Int, endIndex: Int) {
                        writer.write(value, startIndex, endIndex - startIndex)
                    }
                }
                bufferedFileWriter.block()
            }
        } catch (error: Throwable) {
            runCatching { destinationFile.delete() }
            throw error
        }
    }

    override suspend fun directoryExists(directoryUri: String): Boolean = withContext(ioDispatcher) {
        val dir = File(directoryUri)
        dir.exists() && dir.isDirectory
    }

    override suspend fun getDisplayName(directoryUri: String): String = withContext(ioDispatcher) {
        val dir = File(directoryUri)
        dir.name.takeIf { it.isNotBlank() } ?: directoryUri
    }

    override suspend fun createUniqueDirectory(
        parentDirectoryUri: String,
        baseName: String
    ): String? = withContext(ioDispatcher) {
        val parent = File(parentDirectoryUri)
        if (!parent.isDirectory) return@withContext null
        val safeName = baseName.sanitizeForFileName().ifBlank { "Untitled" }
        val existingNames = parent.listFiles()
            .filter { it.isDirectory }
            .map { it.name }
            .toHashSet()

        var candidate = safeName
        var index = 2
        while (candidate in existingNames) {
            candidate = "$safeName ($index)"
            index++
        }
        val created = File(parent, candidate)
        if (created.mkdirs()) created.absolutePath else null
    }

    override suspend fun listFileNames(directoryUri: String): Set<String> = withContext(ioDispatcher) {
        File(directoryUri).takeIf { it.isDirectory }
            ?.listFiles()
            ?.filter { it.isFile }
            ?.map { it.name }
            ?.toSet()
            ?: emptySet()
    }

    override suspend fun writeTextFile(
        directoryUri: String,
        preferredName: String,
        extension: String,
        mimeType: String,
        content: String,
        existingFileNames: MutableSet<String>
    ): WriteTextFileResult = withContext(ioDispatcher) {
        val dir = File(directoryUri)
        val safeName = preferredName.sanitizeForFileName().ifBlank { "Untitled" }
        val safeExtension = extension.trim().trimStart('.').ifBlank { "txt" }
        if (!dir.isDirectory) {
            return@withContext WriteTextFileResult.CouldNotCreateFile("$safeName.$safeExtension")
        }

        val file = dir.createUniqueFile(
            baseName = safeName,
            extension = safeExtension,
            existingFileNames = existingFileNames
        ) ?: return@withContext WriteTextFileResult.CouldNotCreateFile("$safeName.$safeExtension")

        val fileName = file.name
        runCatching {
            file.bufferedWriter().use { writer ->
                writer.write(content)
            }
        }.fold(
            onSuccess = {
                WriteTextFileResult.Success(fileName)
            },
            onFailure = {
                WriteTextFileResult.CouldNotWriteFile(fileName)
            }
        )
    }

    override suspend fun readJsonArraysFromFile(
        fileUri: String,
        arrayNames: Set<String>,
        onItem: suspend (arrayName: String, item: JsonElement) -> Unit
    ): ReadJsonFileResult = withContext(ioDispatcher) {
        val file = File(fileUri)
        if (!file.isFile) return@withContext ReadJsonFileResult.CouldNotReadFile

        try {
            // Phase 1: parse the whole document in memory. Backup files are
            // bounded in practice; revisit streaming if export size grows.
            val root = Json.parseToJsonElement(file.readText())
            val visited = mutableSetOf<String>()
            if (root !is JsonObject) return@withContext ReadJsonFileResult.CouldNotReadFile

            for ((arrayName, value) in root) {
                if (arrayName !in arrayNames || arrayName in visited) continue

                if (value !is JsonArray) return@withContext ReadJsonFileResult.CouldNotReadFile
                for (item in value) {
                    try {
                        onItem(arrayName, item)
                    } catch (error: Throwable) {
                        throw JsonArrayItemException(error)
                    }
                }
                visited.add(arrayName)
            }
            ReadJsonFileResult.Success
        } catch (error: JsonArrayItemException) {
            throw error.itemError
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            ReadJsonFileResult.CouldNotReadFile
        }
    }

    private fun File.createUniqueFile(
        baseName: String,
        extension: String,
        existingFileNames: MutableSet<String>
    ): File? {
        var candidate = "$baseName.$extension"
        var index = 2
        while (candidate in existingFileNames) {
            candidate = "$baseName ($index).$extension"
            index++
        }
        val file = File(this, candidate)
        val created = runCatching { file.createNewFile() }.getOrDefault(false)
        if (created) {
            existingFileNames.add(candidate)
            return file
        }
        return null
    }

    private fun String.sanitizeForFileName(): String = trim()
        .replace(FILE_NAME_INVALID_CHARS_REGEX, "_")
        .replace(WHITESPACE_REGEX, " ")
        .trim('.', ' ')

    companion object {
        private val FILE_NAME_INVALID_CHARS_REGEX = Regex("""[\\/:*?"<>|]""")
        private val WHITESPACE_REGEX = Regex("\\s+")

        // Keeping unused import-free references for API parity with Android.
        @Suppress("unused")
        private val jsonFactories = listOf(JsonNull, JsonPrimitive(""))

        @Suppress("unused")
        private fun parity() {
            // JsonUnquotedLiteral reference ensures kotlinx.serialization.json
            // stays a dependency, matching the Android module's API surface.
            val x: JsonElement? = JsonUnquotedLiteral("n")
            check(x != null)
        }
    }

    private class JsonArrayItemException(val itemError: Throwable) : Exception(itemError)
}
