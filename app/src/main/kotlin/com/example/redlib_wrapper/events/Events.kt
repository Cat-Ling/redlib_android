package com.example.redlib_wrapper.events

sealed interface AppEvent {
    val id: String
    val timestamp: Long
}

sealed interface UpdateEvent : AppEvent
data class UpdateStarted(override val id: String, val url: String, override val timestamp: Long) : UpdateEvent
data class UpdateProgress(override val id: String, val phase: String, val bytesDownloaded: Long, val totalBytes: Long?, val percent: Double?, override val timestamp: Long) : UpdateEvent
data class UpdateExtracted(override val id: String, val tempPath: String, val entries: List<String>, override val timestamp: Long) : UpdateEvent
data class UpdateSanityCheck(override val id: String, val success: Boolean, val versionOutput: String?, val notes: String?, override val timestamp: Long) : UpdateEvent
data class UpdateCompleted(override val id: String, val installedPath: String, val checksum: String?, val version: String, override val timestamp: Long) : UpdateEvent
data class UpdateFailed(override val id: String, val reason: String, val errorLog: String?, override val timestamp: Long) : UpdateEvent
data class RollbackPerformed(override val id: String, val restoredPath: String, override val timestamp: Long) : UpdateEvent

sealed interface RunEvent : AppEvent
data class RunStarted(override val id: String, val pid: Long?, override val timestamp: Long) : RunEvent
data class RunLine(override val id: String, val stream: String, val text: String, override val timestamp: Long) : RunEvent
data class RunStatus(override val id: String, val exitCode: Int?, val status: String, override val timestamp: Long) : RunEvent
data class RunResult(override val id: String, val exitCode: Int, val durationMs: Long, val stdoutSummary: String?, val stderrSummary: String?, val logsPath: String, override val timestamp: Long) : RunEvent
data class RunFailed(override val id: String, val reason: String, val stderrSample: String?, override val timestamp: Long) : RunEvent

sealed interface DiagnosticsEvent : AppEvent
data class DiagnosticsCollected(override val id: String, val bundlePath: String, val size: Long, val scrubbed: Boolean, override val timestamp: Long) : DiagnosticsEvent
data class DiagnosticsCollectionFailed(override val id: String, val reason: String, val partialBundlePath: String?, override val timestamp: Long) : DiagnosticsEvent
