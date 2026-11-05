package io.github.caizhixing.trail

import io.github.caizhixing.trail.model.Audience
import io.github.caizhixing.trail.model.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Behavior 7.0 (ruling A, debug side): the explicit traceId overload must
 * deliver the given traceId verbatim into the enqueued LogEvent — the value
 * overrides the ambient context lookup, null records null. The shell-phase
 * ambient lookup is always null, so override-vs-ambient differentiation is
 * owned by the #4 trace engine tests; this file locks the passthrough half.
 */
class TrailExplicitTraceIdTest {

    @Before
    fun resetCollector() {
        TrailImpl.recentEvents.clear()
    }

    @Test
    fun explicitTraceIdOverloadRecordsEventWithGivenTraceId() {
        Trail.event(
            "network",
            "trail_test_explicit_trace",
            "t_0123456789abcdef",
            Audience.AI,
            Level.INFO,
            mapOf("method" to "GET"),
        )

        val event = TrailImpl.recentEvents.snapshot().single()
        assertEquals("t_0123456789abcdef", event.traceId)
        assertEquals("network", event.tag)
        assertEquals("trail_test_explicit_trace", event.name)
        assertEquals(Audience.AI, event.audience)
        assertEquals(Level.INFO, event.level)
        assertEquals(mapOf("method" to "GET"), event.attrs)
    }

    @Test
    fun explicitTraceIdOverloadRecordsNullTraceIdWhenGivenNull() {
        Trail.event("network", "trail_test_explicit_trace_null", null, Audience.AI, Level.WARN, emptyMap())

        val event = TrailImpl.recentEvents.snapshot().single()
        assertNull(event.traceId)
        assertEquals(Level.WARN, event.level)
        assertEquals(emptyMap<String, String>(), event.attrs)
    }
}
