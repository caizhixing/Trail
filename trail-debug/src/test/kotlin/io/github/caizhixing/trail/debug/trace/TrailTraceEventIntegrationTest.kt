package io.github.caizhixing.trail.debug.trace

import io.github.caizhixing.trail.Trail
import io.github.caizhixing.trail.TrailImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Behavior 4.5: events emitted inside a trace carry its id; events outside any
 * trace carry null. Asserted against the collector that issue #3 will swap for
 * the real engine — the field path (buildEvent → currentTraceId) stays fixed.
 */
class TrailTraceEventIntegrationTest {

    @Before
    fun resetCollector() {
        TrailImpl.recentEvents.clear()
    }

    @Test
    fun eventsInsideTraceCarryTraceIdAndOutsideStayNull() = runBlocking {
        Trail.event("widget", "trail_test_outside_before")
        val insideId: String? = Trail.withTrace("trace_scope_under_test") {
            val expected = Trail.currentTraceId()
            Trail.event("widget", "trail_test_inside_first")
            Trail.event("widget", "trail_test_inside_second")
            expected
        }
        Trail.event("widget", "trail_test_outside_after")

        val events = TrailImpl.recentEvents.snapshot()
        assertEquals(4, events.size)
        assertNull(
            "event emitted before the trace must have null traceId",
            events.first { it.name == "trail_test_outside_before" }.traceId,
        )
        assertNull(
            "event emitted after the trace must have null traceId",
            events.first { it.name == "trail_test_outside_after" }.traceId,
        )
        val insideEvents = events.filter { it.name.startsWith("trail_test_inside") }
        assertEquals(2, insideEvents.size)
        assertNotNull(insideId)
        insideEvents.forEach { event ->
            assertEquals(
                "event traceId must equal the block-scoped currentTraceId",
                insideId,
                event.traceId,
            )
        }
    }

    @Test
    fun tracelessEventsStayNull() {
        Trail.event("widget", "trail_test_traceless")

        assertNull(TrailImpl.recentEvents.snapshot().single().traceId)
    }
}
