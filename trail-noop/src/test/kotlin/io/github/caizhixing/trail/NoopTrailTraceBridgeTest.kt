package io.github.caizhixing.trail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Behavior 4.8: the noop artifact keeps the trace APIs side-effect free —
 * withTrace passes the block value straight through, currentTraceId stays
 * null inside and outside traces, and beginTrace hands out an empty
 * AutoCloseable that survives repeated close.
 */
class NoopTrailTraceBridgeTest {

    @Test
    fun nestedWithTracePassesThrough() {
        val outcome = runSuspendBlock {
            Trail.withTrace("noop_trace_scope") {
                Trail.withTrace("noop_trace_inner_scope") { "noop_nested_result" }
            }
        }

        assertEquals("noop_nested_result", outcome.getOrThrow())
    }

    @Test
    fun beginTraceInsideWithTraceStaysNull() {
        val outcome = runSuspendBlock {
            Trail.withTrace("noop_trace_scope") {
                Trail.beginTrace("noop_trace_scope").use { Trail.currentTraceId() }
            }
        }

        assertNull(outcome.getOrThrow())
        assertNull(Trail.currentTraceId())
    }

    @Test
    fun beginTraceCloseIsIdempotentAndSideEffectFree() {
        val trace = Trail.beginTrace("noop_trace_scope")
        trace.close()
        trace.close()

        assertNull(Trail.currentTraceId())
        assertEquals("", Trail.dumpRecent(10))
    }
}
