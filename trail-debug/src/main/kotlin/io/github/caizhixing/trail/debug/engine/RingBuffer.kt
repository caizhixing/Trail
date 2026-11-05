package io.github.caizhixing.trail.debug.engine

import io.github.caizhixing.trail.model.LogEvent

/**
 * Fixed-capacity in-memory ring keeping the most recent events (behavior 3.2).
 * Pure class: no IO, no Android, no coroutines. Thread-safe via monitor.
 */
internal class RingBuffer(private val capacity: Int) {

    private val events = ArrayDeque<LogEvent>()

    fun add(event: LogEvent) {
        if (capacity <= 0) {
            return
        }
        synchronized(events) {
            events.addLast(event)
            while (events.size > capacity) {
                events.removeFirst()
            }
        }
    }

    /** Oldest-first snapshot of the retained events. */
    fun snapshot(): List<LogEvent> = synchronized(events) { events.toList() }

    fun size(): Int = synchronized(events) { events.size }

    fun clear() {
        synchronized(events) { events.clear() }
    }
}
