package io.github.caizhixing.trail

import io.github.caizhixing.trail.model.Audience
import io.github.caizhixing.trail.model.Level
import io.github.caizhixing.trail.model.LogEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class InMemoryEventCollectorTest {

    @Test
    fun keepsMostRecentEventsUpToCapacity() {
        val collector = InMemoryEventCollector(capacity = 3)

        for (index in 0 until 5) {
            collector.add(eventOf("trail_test_event_$index"))
        }

        val snapshot = collector.snapshot()
        assertEquals(listOf("trail_test_event_2", "trail_test_event_3", "trail_test_event_4"), snapshot.map { it.name })
    }

    @Test
    fun snapshotBelowCapacityKeepsInsertionOrder() {
        val collector = InMemoryEventCollector(capacity = 3)

        collector.add(eventOf("trail_test_first"))
        collector.add(eventOf("trail_test_second"))

        assertEquals(listOf("trail_test_first", "trail_test_second"), collector.snapshot().map { it.name })
    }

    @Test
    fun clearDropsEverything() {
        val collector = InMemoryEventCollector()
        collector.add(eventOf("trail_test_before_clear"))
        collector.clear()

        assertEquals(emptyList<LogEvent>(), collector.snapshot())
    }

    private fun eventOf(name: String): LogEvent =
        LogEvent(
            ts = 0L,
            traceId = null,
            tag = "widget",
            name = name,
            audience = Audience.AI,
            level = Level.INFO,
            attrs = emptyMap(),
            tid = null,
        )
}
