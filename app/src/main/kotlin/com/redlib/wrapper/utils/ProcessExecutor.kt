package com.redlib.wrapper.utils

import java.io.InputStream

/**
 * A mock representation of a `java.lang.Process` to be used in environments
 * where real process execution is not possible.
 */
class MockProcess(
    private val stdout: String,
    private val stderr: String,
    private val exitCode: Int
) {
    val inputStream: InputStream
        get() = stdout.byteInputStream()

    val errorStream: InputStream
        get() = stderr.byteInputStream()

    fun waitFor(): Int {
        return exitCode
    }

    fun destroy() {
        // No-op for this mock
    }
}

/**
 * Executes a command by returning a [MockProcess] that simulates its execution.
 * This is a stand-in for `java.lang.ProcessBuilder` for testing or environment-limited scenarios.
 *
 * @param command The command to "execute".
 * @return A [MockProcess] instance that simulates the result of the command.
 */
fun execute(command: List<String>): MockProcess {
    val commandStr = command.joinToString(" ")

    return when {
        "redlib" in commandStr && "--version" in commandStr -> {
            MockProcess("redlib version 1.2.3\n", "", 0)
        }
        "redlib" in commandStr -> {
            MockProcess("redlib is running...\n", "", 0)
        }
        else -> {
            MockProcess("", "Error: command not found: ${command.firstOrNull()}\n", 127)
        }
    }
}
