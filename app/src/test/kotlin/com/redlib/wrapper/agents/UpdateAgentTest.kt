package com.redlib.wrapper.agents

import com.redlib.wrapper.bus.EventBus
import com.redlib.wrapper.events.*
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class UpdateAgentTest {

    private lateinit var updateAgent: UpdateAgent
    private val testDir = Paths.get("/app/test_tmp")
    private val sourceFile = testDir.resolve("redlib_source")

    @Before
    fun setUp() {
        updateAgent = UpdateAgent()
        mockkObject(EventBus)

        // Create a dummy source file for the test
        Files.createDirectories(testDir)
        Files.write(sourceFile, "redlib version 1.2.3".toByteArray())
    }

    @After
    fun tearDown() {
        unmockkObject(EventBus)
        // Clean up the test directory
        Files.walk(testDir)
            .sorted(Comparator.reverseOrder())
            .map { it.toFile() }
            .forEach { it.delete() }
    }

    @Test
    fun `runUpdate successful`() = runTest {
        val events = mutableListOf<RedlibEvent>()
        coEvery { EventBus.emit(capture(events)) } just Runs

        val result = updateAgent.runUpdate(sourceFile.toString())

        assertTrue(result is UpdateAgent.UpdateResult.Success)
        assertEquals("1.2.3", (result as UpdateAgent.UpdateResult.Success).version)

        // Verify file system state
        val finalPath = Paths.get("/app/redlib_current/redlib_source")
        assertTrue(Files.exists(finalPath))
        val content = String(Files.readAllBytes(finalPath))
        assertEquals("redlib version 1.2.3", content)

        // Verify events
        assertTrue(events.any { it is UpdateCompleted })
    }

    @Test
    fun `runUpdate fails on sanity check`() = runTest {
        // Create a bad source file
        Files.write(sourceFile, "this is not a valid redlib file".toByteArray())

        val events = mutableListOf<RedlibEvent>()
        coEvery { EventBus.emit(capture(events)) } just Runs

        val result = updateAgent.runUpdate(sourceFile.toString())

        assertTrue(result is UpdateAgent.UpdateResult.Failure)
        assertTrue((result as UpdateAgent.UpdateResult.Failure).error is SanityFailedException)

        // Verify file system state
        val finalPath = Paths.get("/app/redlib_current/redlib_source")
        assertTrue(Files.notExists(finalPath))

        // Verify events
        assertTrue(events.any { it is UpdateFailed && it.reason == "sanity_failed" })
        assertTrue(events.none { it is UpdateCompleted })
    }
}
