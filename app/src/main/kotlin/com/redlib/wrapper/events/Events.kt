package com.redlib.wrapper.events

import java.sql.Timestamp

// A base event class to help with type safety and serialization
sealed class RedlibEvent(val timestamp: Long = System.currentTimeMillis())

// UpdateAgent Events
data class UpdateStarted(
    val id: String,
    val url: String
) : RedlibEvent()

data class UpdateProgress(
    val id: String,
    val phase: String,
    val bytesDownloaded: Long,
    val totalBytes: Long?,
    val percent: Float?
) : RedlibEvent()

data class UpdateExtracted(
    val id: String,
    val tempPath: String,
    val entries: List<String>
) : RedlibEvent()

data class UpdateSanityCheck(
    val id: String,
    val success: Boolean,
    val versionOutput: String?,
    val notes: String?
) : RedlibEvent()

data class UpdateCompleted(
    val id: String,
    val installedPath: String,
    val checksum: String?,
    val version: String
) : RedlibEvent()

data class UpdateFailed(
    val id: String,
    val reason: String,
    val errorLog: String?
) : RedlibEvent()

data class RollbackPerformed(
    val id: String,
    val restoredPath: String
) : RedlibEvent()

// RunAgent Events
data class RunStarted(
    val id: String,
    val pid: Long?
) : RedlibEvent()

data class RunLine(
    val id: String,
    val stream: String, // "stdout" or "stderr"
    val text: String
) : RedlibEvent()

data class RunStatus(
    val id: String,
    val exitCode: Int?,
    val status: String // "running", "stopped", "killed"
) : RedlibEvent()

data class RunResult(
    val id: String,
    val exitCode: Int,
    val durationMs: Long,
    val stdoutSummary: String?,
    val stderrSummary: String?,
    val logsPath: String?
) : RedlibEvent()

data class RunFailed(
    val id: String,
    val reason: String,
    val stderrSample: String?
) : RedlibEvent()

// DiagnosticsAgent Events
data class DiagnosticsCollected(
    val id: String,
    val bundlePath: String,
    val size: Long,
    val scrubbed: Boolean
) : RedlibEvent()

data class DiagnosticsCollectionFailed(
    val id: String,
    val reason: String,
    val partialBundlePath: String?
) : RedlibEvent()
