package io.github.caizhixing.trail.debug.engine

import io.github.caizhixing.trail.model.LogEvent
import org.junit.Assert.assertEquals
import org.junit.Test

/** Behavior 3.2: ring capacity semantics on the 0/1/N/N+1 boundaries. */
class RingBufferTest {

    @Test
    fun capacityZeroKeepsNothing() {
        val ring = RingBuffer(capacity = 0)

        ring.add(event("trail_test_one"))
        ring.add(event("trail_test_two"))

        assertEquals(emptyList<LogEvent>(), ring.snapshot())
        assertEquals(0, ring.size())
    }

    @Test
    fun capacityOneKeepsOnlyTheLatest() {
        val ring = RingBuffer(capacity = 1)

        ring.add(event("trail_test_one"))
        ring.add(event("trail_test_two"))

        assertEquals(listOf("trail_test_two"), ring.snapshot().map { it.name })
        assertEquals(1, ring.size())
    }

    @Test
    fun capacityNKeepsTheLastN() {
        val ring = RingBuffer(capacity = 3)

        for (index in 1..5) {
            ring.add(event("trail_test_$index"))
        }

        assertEquals(
            listOf("trail_test_3", "trail_test_4", "trail_test_5"),
            ring.snapshot().map { it.name },
        )
    }

    @Test
    fun capacityNWithNPlusOneInputKeepsTheLastN() {
        val ring = RingBuffer(capacity = 3)

        for (index in 1..4) {
            ring.add(event("trail_test_$index"))
        }

        assertEquals(
            listOf("trail_test_2", "trail_test_3", "trail_test_4"),
            ring.snapshot().map { it.name },
        )
    }

    @Test
    fun belowCapacityKeepsInsertionOrder() {
        val ring = RingBuffer(capacity = 8)

        ring.add(event("trail_test_first"))
        ring.add(event("trail_test_second"))

        assertEquals(
            listOf("trail_test_first", "trail_test_second"),
            ring.snapshot().map { it.name },
        )
    }

    @Test
    fun clearDropsEverything() {
        val ring = RingBuffer(capacity = 4)
        ring.add(event("trail_test_before_clear"))
        ring.clear()

        assertEquals(emptyList<LogEvent>(), ring.snapshot())
        assertEquals(0, ring.size())
    }

    private fun event(name: String): LogEvent =
        LogEvent(
            ts = 0L,
            traceId = null,
            tag = "widget",
            name = name,
            audience = io.github.caizhixing.trail.model.Audience.AI,
            level = io.github.caizhixing.trail.model.Level.INFO,
            attrs = emptyMap(),
            tid = null,
        )
}
