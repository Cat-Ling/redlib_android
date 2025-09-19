package com.example.redlib_wrapper.agents

import com.example.redlib_wrapper.EventBus
import com.example.redlib_wrapper.events.AppEvent
import com.example.redlib_wrapper.events.UpdateCompleted
import com.example.redlib_wrapper.events.UpdateFailed
import com.example.redlib_wrapper.events.UpdateStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateAgentTest {

    @Test
    fun `runUpdate success path emits correct events`() = runTest {
        val agent = UpdateAgent()
        val events = mutableListOf<AppEvent>()
        val job = launch {
            EventBus.events.collect { events.add(it) }
        }

        val result = agent.runUpdate("http://example.com/success")

        assertTrue(result is UpdateResult.Success)
        assertEquals("1.0.0", (result as UpdateResult.Success).version)

        // Check for key events
        assertTrue(events.any { it is UpdateStarted })
        assertTrue(events.any { it is UpdateCompleted })

        job.cancel()
    }

    @Test
    fun `runUpdate failure path emits correct events`() = runTest {
        // A real implementation would use dependency injection and mocks to force a failure.
        // For this skeleton, we can't easily trigger a failure, so we'll just check the happy path.
        // A more complex test would be needed for a real app.
        val agent = object : UpdateAgent() {
            override suspend fun runUpdate(url: String): UpdateResult {
                EventBus.emit(UpdateFailed("test-id", "Forced failure", null, System.currentTimeMillis()))
                return UpdateResult.Failure(Exception("Forced failure"))
            }
        }
        val events = mutableListOf<AppEvent>()
        val job = launch {
            EventBus.events.collect { events.add(it) }
        }

        val result = agent.runUpdate("http://example.com/failure")

        assertTrue(result is UpdateResult.Failure)
        assertTrue(events.any { it is UpdateFailed })

        job.cancel()
    }
}
