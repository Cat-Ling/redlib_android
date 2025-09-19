package com.example.redlib_wrapper.agents

import com.example.redlib_wrapper.EventBus
import com.example.redlib_wrapper.events.AppEvent
import com.example.redlib_wrapper.events.RunResult
import com.example.redlib_wrapper.events.RunStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class RunAgentTest {

    @Test
    fun `runBinary emits correct events`() = runTest {
        val agent = RunAgent()
        val events = mutableListOf<AppEvent>()
        val job = launch {
            EventBus.events.collect { events.add(it) }
        }

        val request = RunRequest(
            binaryPath = "/test/path",
            args = emptyList(),
            envProfileName = "test",
            workingDir = "/test",
            pty = false,
            runMode = "foreground"
        )
        agent.runBinary(request)

        assertTrue(events.any { it is RunStarted })
        assertTrue(events.any { it is RunResult })

        job.cancel()
    }
}
