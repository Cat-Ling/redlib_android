package com.redlib.wrapper.agents

import com.redlib.wrapper.bus.EventBus
import com.redlib.wrapper.events.*
import com.redlib.wrapper.utils.execute
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.util.UUID
import kotlinx.coroutines.flow.onCompletion

data class RunRequest(
    val binaryPath: String,
    val args: List<String> = emptyList(),
    val envProfileName: String? = null,
    val workingDir: String = "/tmp",
    val pty: Boolean = false
)

class RunAgent {

    fun runBinary(request: RunRequest): Flow<RedlibEvent> = flow {
        val id = UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()

        try {
            emit(RunStarted(id, null)) // PID is unknown in this mock

            val command = listOf(request.binaryPath) + request.args
            val process = execute(command)

            coroutineScope {
                val stdoutJob = launch {
                    process.inputStream.bufferedReader().forEachLine { line ->
                        emit(RunLine(id, "stdout", line))
                    }
                }

                val stderrJob = launch {
                    process.errorStream.bufferedReader().forEachLine { line ->
                        emit(RunLine(id, "stderr", line))
                    }
                }

                stdoutJob.join()
                stderrJob.join()
            }

            val exitCode = process.waitFor()
            val durationMs = System.currentTimeMillis() - startTime

            if (exitCode == 0) {
                val result = RunResult(
                    id = id,
                    exitCode = exitCode,
                    durationMs = durationMs,
                    stdoutSummary = "Process completed successfully.",
                    stderrSummary = null,
                    logsPath = "/files/redlib/logs/run-$id.log"
                )
                emit(result)
            } else {
                throw ProcessFailedException("Process exited with code $exitCode")
            }

        } catch (e: Throwable) {
            val reason = if (e is ProcessFailedException) "process_failed" else "unknown_error"
            val failureEvent = RunFailed(
                id = id,
                reason = reason,
                stderrSample = e.message
            )
            emit(failureEvent)
        }
    }
}

class ProcessFailedException(message: String) : Exception(message)
