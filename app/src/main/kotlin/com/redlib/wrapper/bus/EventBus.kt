package com.redlib.wrapper.bus

import com.redlib.wrapper.events.RedlibEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A simple, application-wide event bus for decoupled communication between agents.
 * It uses a Kotlin SharedFlow to broadcast events.
 */
object EventBus {

    // A private mutable shared flow that is the backbone of the event bus.
    // Replays 0 events to new subscribers and allows for a buffer of 64 events.
    private val _events = MutableSharedFlow<RedlibEvent>(replay = 0, extraBufferCapacity = 64)

    /**
     * A publicly exposed SharedFlow that consumers can collect events from.
     * This is a read-only view of the private mutable flow.
     */
    val events = _events.asSharedFlow()

    /**
     * Emits an event to the event bus.
     * This function is suspending and will suspend if the buffer is full.
     *
     * @param event The event to emit.
     */
    suspend fun emit(event: RedlibEvent) {
        _events.emit(event)
    }
}
