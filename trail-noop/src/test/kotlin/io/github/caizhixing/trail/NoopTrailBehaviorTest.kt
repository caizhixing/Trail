package io.github.caizhixing.trail

import io.github.caizhixing.trail.model.Audience
import io.github.caizhixing.trail.model.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NoopTrailBehaviorTest {

    @Test
    fun eventLambdaAttrsNeverEvaluated() {
        var evaluations = 0

        Trail.event("widget", "trail_test_noop_event") {
            evaluations++
            mapOf("never" to "happens")
        }
        Trail.event("widget", "trail_test_noop_event_explicit", Audience.BOTH, Level.WARN) {
            evaluations++
            mapOf("never" to "happens")
        }
        Trail.flow("search", "trail_test_noop_flow") {
            evaluations++
            mapOf("never" to "happens")
        }

        assertEquals(0, evaluations)
    }

    @Test
    fun mapOverloadsAreEmptyBodiesAndDoNotCrash() {
        Trail.event("widget", "trail_test_noop_map", mapOf("k" to "v"))
        Trail.event("widget", "trail_test_noop_map_explicit", Audience.HUMAN, Level.ERROR, mapOf("k" to "v"))
        Trail.flow("search", "trail_test_noop_flow_map", mapOf("k" to "v"))

        assertTrue(true)
    }

    @Test
    fun withTracePassesBlockValueThrough() {
        val outcome = runSuspendBlock {
            Trail.withTrace("trail_test_noop_trace") { "trail_noop_result" }
        }

        assertEquals("trail_noop_result", outcome.getOrThrow())
    }

    @Test
    fun withTracePropagatesBlockExceptionUnchanged() {
        val boom = IllegalStateException("trail_noop_boom")
        val outcome = runSuspendBlock {
            Trail.withTrace<String>("trail_test_noop_trace") { throw boom }
        }

        assertTrue(outcome.isFailure)
        assertSame(boom, outcome.exceptionOrNull())
    }

    @Test
    fun currentTraceIdIsNull() {
        assertNull(Trail.currentTraceId())
    }

    @Test
    fun currentTraceIdStaysNullInsideWithTrace() {
        val seen = runSuspendBlock {
            Trail.withTrace("trail_test_noop_trace") { Trail.currentTraceId() }
        }

        assertNull(seen.getOrThrow())
    }

    @Test
    fun dumpRecentReturnsEmptyStringForAnyCount() {
        assertEquals("", Trail.dumpRecent(10))
        assertEquals("", Trail.dumpRecent(0))
        assertEquals("", Trail.dumpRecent(-3))
    }

    @Test
    fun beginTraceIsNoOpCloseable() {
        Trail.beginTrace("trail_test_noop_trace").use {
            assertNull(Trail.currentTraceId())
        }
        assertNull(Trail.currentTraceId())
    }
}
