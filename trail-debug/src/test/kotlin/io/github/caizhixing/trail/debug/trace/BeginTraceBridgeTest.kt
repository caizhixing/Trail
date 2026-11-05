package io.github.caizhixing.trail.debug.trace

import io.github.caizhixing.trail.Trail
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavior 4.6: beginTrace pushes a fresh id onto the per-thread bridge,
 * close restores the previous value, use works, and an active coroutine trace
 * always shadows manual frames.
 */
class BeginTraceBridgeTest {

    /** 行为 4.6 · begin→新 id，close→恢复 null，use 语法可用 */
    @Test
    fun beginTraceGivesFreshIdAndUseClosesIt() {
        assertNull(Trail.currentTraceId())

        Trail.beginTrace("bridge_trace_scope").use {
            val id = requireNotNull(Trail.currentTraceId())

            assertTrue("expected t_ + 16 hex chars, got: $id", TRAIL_ID_FORMAT.matches(id))
        }

        assertNull(Trail.currentTraceId())
    }

    /** 行为 4.6 · 嵌套 beginTrace 栈式恢复 */
    @Test
    fun nestedBeginTracesRestoreStackOrder() {
        val outer = Trail.beginTrace("bridge_trace_scope")
        try {
            val outerId = requireNotNull(Trail.currentTraceId())
            val inner = Trail.beginTrace("bridge_trace_scope")
            try {
                val innerId = requireNotNull(Trail.currentTraceId())

                assertTrue(
                    "nested beginTrace must mint a fresh id, both were $innerId",
                    innerId != outerId,
                )
            } finally {
                inner.close()
            }

            assertEquals("closing the inner frame must restore the outer id", outerId, Trail.currentTraceId())
        } finally {
            outer.close()
        }

        assertNull(Trail.currentTraceId())
    }

    /** 行为 4.6 · 协程 trace 优先，ThreadLocal 不参与 */
    @Test
    fun coroutineTraceShadowsManualBeginTrace() = runBlocking {
        val manual = Trail.beginTrace("bridge_trace_scope")
        try {
            val manualId = requireNotNull(Trail.currentTraceId())

            val coroutineId = Trail.withTrace("trace_scope_under_test") {
                val inside = requireNotNull(Trail.currentTraceId())

                assertTrue("coroutine trace must shadow the manual id", inside != manualId)

                val nestedManual = Trail.beginTrace("bridge_trace_scope")
                try {
                    assertEquals(
                        "manual frame opened inside the coroutine must not shadow either",
                        inside,
                        Trail.currentTraceId(),
                    )
                } finally {
                    nestedManual.close()
                }
                inside
            }

            assertTrue("coroutine scope must mint its own id", coroutineId != manualId)
            assertEquals("manual frame must survive the coroutine scope", manualId, Trail.currentTraceId())
        } finally {
            manual.close()
        }

        assertNull(Trail.currentTraceId())
    }

    /** 行为 4.6 · close 幂等（重复 close 不抛、状态不变） */
    @Test
    fun beginTraceCloseIsIdempotent() {
        val trace = Trail.beginTrace("bridge_trace_scope")
        assertNotNull(Trail.currentTraceId())
        trace.close()
        trace.close()

        assertNull(Trail.currentTraceId())
    }

    private companion object {
        // DECISIONS.md P-7: traceId = t_ + 16 hex
        val TRAIL_ID_FORMAT = Regex("t_[0-9a-f]{16}")
    }
}
