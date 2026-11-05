package io.github.caizhixing.trail

import io.github.caizhixing.trail.model.LogEvent

/**
 * Thread-safe stub collector keeping the most recent events for test assertions.
 * Replaced by the real engine in issue #3.
 */
internal class InMemoryEventCollector(private val capacity: Int = DEFAULT_CAPACITY) {

    private val events = ArrayDeque<LogEvent>()

    fun add(event: LogEvent) {
        synchronized(events) {
            events.addLast(event)
            while (events.size > capacity) {
                events.removeFirst()
            }
        }
    }

    fun snapshot(): List<LogEvent> {
        return synchronized(events) {
            events.toList()
        }
    }

    fun clear() {
        synchronized(events) {
            events.clear()
        }
    }

    private companion object {
        const val DEFAULT_CAPACITY = 2000
    }
}
