package com.example.redlib_wrapper

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A real app would set content view here
        // setContentView(R.layout.activity_main)

        // Example of how UIAgent would listen to events
        lifecycleScope.launch {
            EventBus.events.collect { event ->
                Log.d("MainActivity", "Received event: $event")
            }
        }

        // Example of triggering the agents
        // This would normally be done by user interaction
        lifecycleScope.launch {
            // val updateAgent = com.example.redlib_wrapper.agents.UpdateAgent()
            // updateAgent.runUpdate("http://example.com/redlib.tar.gz")

            // val runAgent = com.example.redlib_wrapper.agents.RunAgent()
            // val request = com.example.redlib_wrapper.agents.RunRequest(
            //     binaryPath = "/data/data/com.example.redlib_wrapper/files/redlib",
            //     args = listOf("--version"),
            //     envProfileName = "default",
            //     workingDir = "/data/data/com.example.redlib_wrapper/files",
            //     pty = false,
            //     runMode = "foreground"
            // )
            // runAgent.runBinary(request)
        }
    }
}
