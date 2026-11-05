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
    fun mapOverloadsNeverReadAttrsAndRecordNothing() {
        val attrs = ReadCountingMap()

        Trail.event("widget", "trail_test_noop_map", attrs)
        Trail.event("widget", "trail_test_noop_map_explicit", Audience.HUMAN, Level.ERROR, attrs)
        Trail.flow("search", "trail_test_noop_flow_map", attrs)

        assertEquals(0, attrs.reads)
        assertEquals("", Trail.dumpRecent(10))
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

/**
 * Observability probe for the behavior-2.8 Map overloads: every read path of
 * the Map increments [reads], so a stub that touches its argument in any way
 * (lookup / iteration / size) breaks the reads == 0 assertion instead of
 * passing vacuously.
 */
private class ReadCountingMap : Map<String, String> {

    private val backing = mapOf("trail_test_observable" to "value")

    var reads: Int = 0
        private set

    override val size: Int
        get() {
            reads++
            return backing.size
        }

    override fun isEmpty(): Boolean {
        reads++
        return backing.isEmpty()
    }

    override fun containsKey(key: String): Boolean {
        reads++
        return backing.containsKey(key)
    }

    override fun containsValue(value: String): Boolean {
        reads++
        return backing.containsValue(value)
    }

    override operator fun get(key: String): String? {
        reads++
        return backing[key]
    }

    override val keys: Set<String>
        get() {
            reads++
            return backing.keys
        }

    override val values: Collection<String>
        get() {
            reads++
            return backing.values
        }

    override val entries: Set<Map.Entry<String, String>>
        get() {
            reads++
            return backing.entries
        }
}
