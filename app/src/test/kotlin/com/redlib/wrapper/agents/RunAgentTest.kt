package com.redlib.wrapper.agents

import com.redlib.wrapper.events.RedlibEvent
import com.redlib.wrapper.events.RunFailed
import com.redlib.wrapper.events.RunLine
import com.redlib.wrapper.events.RunResult
import com.redlib.wrapper.events.RunStarted
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class RunAgentTest {

    private val runAgent = RunAgent()

    @Test
    fun `runBinary successful`() = runTest {
        val request = RunRequest(binaryPath = "/bin/redlib", args = listOf("--version"))

        val events: List<RedlibEvent> = runAgent.runBinary(request).toList()

        assertTrue(events.first() is RunStarted)
        assertTrue(events.last() is RunResult)

        val runLine = events.find { it is RunLine && it.stream == "stdout" } as RunLine?
        assertEquals("redlib version 1.2.3", runLine?.text?.trim())

        val resultEvent = events.last() as RunResult
        assertEquals(0, resultEvent.exitCode)
    }

    @Test
    fun `runBinary fails`() = runTest {
        // We simulate a failure by having the mock executor return a non-zero exit code
        // Our current mock does this for unknown commands
        val request = RunRequest(binaryPath = "/bin/unknown_command")

        val events: List<RedlibEvent> = runAgent.runBinary(request).toList()

        assertTrue(events.first() is RunStarted)
        assertTrue(events.last() is RunFailed)

        val runLine = events.find { it is RunLine && it.stream == "stderr" } as RunLine?
        assertEquals("Error: command not found: /bin/unknown_command", runLine?.text?.trim())

        val resultEvent = events.last() as RunFailed
        assertEquals("process_failed", resultEvent.reason)
    }
}
