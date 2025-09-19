package com.redlib.wrapper.agents

import com.redlib.wrapper.bus.EventBus
import com.redlib.wrapper.events.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.UUID

class UpdateAgent {

    sealed class UpdateResult {
        data class Success(val version: String) : UpdateResult()
        data class Failure(val error: Throwable) : UpdateResult()
    }

    suspend fun runUpdate(sourcePath: String): UpdateResult = coroutineScope {
        val id = UUID.randomUUID().toString()
        val tmpDir = Paths.get("/app/redlib_tmp_install/$id")
        val finalDir = Paths.get("/app/redlib_current")

        EventBus.emit(UpdateStarted(id, sourcePath))
        try {
            Files.createDirectories(tmpDir)

            // 1. Fetch (by copying)
            val sourceFile = Paths.get(sourcePath)
            val tmpFile = tmpDir.resolve(sourceFile.fileName)
            Files.copy(sourceFile, tmpFile)
            EventBus.emit(UpdateProgress(id, "fetch", Files.size(tmpFile), Files.size(tmpFile), 100.0f))

            // 2. Verify (skipping, as it's a local file)
            EventBus.emit(UpdateProgress(id, "verify", Files.size(tmpFile), Files.size(tmpFile), 100.0f))

            // 3. Extract (skipping, not a tarball)
            EventBus.emit(UpdateProgress(id, "extract", Files.size(tmpFile), Files.size(tmpFile), 100.0f))
            EventBus.emit(UpdateExtracted(id, tmpDir.toString(), listOf(tmpFile.fileName.toString())))

            // 4. Sanity Check (by reading file content)
            val content = String(Files.readAllBytes(tmpFile))
            if (!content.contains("redlib version")) {
                throw SanityFailedException("Sanity check failed: version string not found.")
            }
            val version = content.split(" ").last().trim()
            val sanityCheckResult = UpdateSanityCheck(id, true, version, "Sanity check passed")
            EventBus.emit(sanityCheckResult)

            // 5. Swap (atomic move)
            val installedPath = finalDir.resolve(tmpFile.fileName)
            Files.move(tmpFile, installedPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            EventBus.emit(UpdateProgress(id, "swap", Files.size(installedPath), Files.size(installedPath), 100.0f))

            // 6. Record metadata and complete
            EventBus.emit(UpdateCompleted(id, installedPath.toString(), "sha256-mock-checksum", version))

            UpdateResult.Success(version)
        } catch (e: Throwable) {
            val reason = if (e is SanityFailedException) "sanity_failed" else "unknown_error"
            EventBus.emit(UpdateFailed(id, reason, e.message))
            UpdateResult.Failure(e)
        } finally {
            // Clean up temp directory
            try {
                Files.walk(tmpDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Paths::toFile)
                    .forEach(File::delete)
            } catch (e: Exception) {
                // log cleanup error
            }
        }
    }
}

class SanityFailedException(message: String) : Exception(message)
