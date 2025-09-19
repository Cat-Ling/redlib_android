package com.example.redlib_wrapper.agents

import com.example.redlib_wrapper.EventBus
import com.example.redlib_wrapper.events.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.util.UUID

data class RunRequest(
    val binaryPath: String,
    val args: List<String>,
    val envProfileName: String,
    val workingDir: String,
    val pty: Boolean,
    val runMode: String
)

class RunAgent {

    @Suppress("UNUSED_PARAMETER")
    suspend fun runBinary(request: RunRequest) = coroutineScope {
        val id = UUID.randomUUID().toString()
        try {
            EventBus.emit(RunStarted(id, 1234L, System.currentTimeMillis()))

            // TODO: Implement actual process execution and stream reading
            // Emitting some dummy events for now
            EventBus.emit(RunLine(id, "stdout", "Starting redlib...", System.currentTimeMillis()))
            delay(100)
            EventBus.emit(RunLine(id, "stdout", "redlib started successfully.", System.currentTimeMillis()))
            delay(100)
            EventBus.emit(RunStatus(id, 0, "stopped", System.currentTimeMillis()))
            EventBus.emit(RunResult(id, 0, 200, "Starting redlib...", "", "/logs/run-1.log", System.currentTimeMillis()))

        } catch (e: Exception) {
            EventBus.emit(RunFailed(id, "Process execution failed", e.message, System.currentTimeMillis()))
        }
    }
}
