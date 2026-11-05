package io.github.caizhixing.trail

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Facade-level trace behavior. Named "Shell" from the issue #2 shell phase;
 * since issue #4 the trace APIs are live and the former shell assertions
 * (null id inside withTrace / no-op beginTrace) have graduated to real
 * behavior, with the full matrix covered in debug.trace.
 */
class TrailTraceShellTest {

    @Before
    fun resetCollector() {
        TrailImpl.resetForTest()
    }

    @Test
    fun withTraceShellReturnsBlockValue() = runBlocking {
        val result = Trail.withTrace("trail_test_trace_scope") { "trail_block_result" }

        assertEquals("trail_block_result", result)
    }

    @Test
    fun withTracePropagatesBlockExceptionUnchanged() = runBlocking {
        val boom = IllegalStateException("trail_boom")
        val thrown = runCatching {
            Trail.withTrace<String>("trail_test_trace_scope") { throw boom }
        }.exceptionOrNull()

        // kotlinx.coroutines stack-trace recovery copies the exception instance
        // at the withContext boundary; type and message stay identical.
        assertTrue(
            "block exception must pass through as IllegalStateException, was $thrown",
            thrown is IllegalStateException,
        )
        assertEquals("trail_boom", thrown?.message)
    }

    @Test
    fun withTraceShellSupportsSuspendingBlocks() = runBlocking {
        val result = Trail.withTrace("trail_test_trace_scope") {
            val inner = Trail.withTrace("trail_test_inner_scope") { "trail_inner" }
            "outer:" + inner
        }

        assertEquals("outer:trail_inner", result)
    }

    @Test
    fun currentTraceIdIsNullOutsideAnyTrace() {
        assertNull(Trail.currentTraceId())
    }

    @Test
    fun currentTraceIdAvailableInsideWithTrace() = runBlocking {
        val seen = Trail.withTrace("trail_test_trace_scope") { Trail.currentTraceId() }

        assertNotNull(seen)
        assertTrue("expected t_ + 16 hex chars, got: $seen", TRAIL_ID_FORMAT.matches(requireNotNull(seen)))
    }

    @Test
    fun dumpRecentReturnsEmptyStringInShellPhase() {
        Trail.event("widget", "trail_test_before_dump")

        assertEquals("", Trail.dumpRecent(10))
    }

    @Test
    fun dumpRecentEmptyForZeroAndNegativeCount() {
        assertEquals("", Trail.dumpRecent(0))
        assertEquals("", Trail.dumpRecent(-1))
    }

    @Test
    fun beginTraceOpensTraceIdUntilClosed() {
        Trail.beginTrace("trail_test_trace_scope").use {
            assertNotNull(Trail.currentTraceId())
        }
        assertNull(Trail.currentTraceId())
    }

    private companion object {
        // DECISIONS.md P-7: traceId = t_ + 16 hex
        val TRAIL_ID_FORMAT = Regex("t_[0-9a-f]{16}")
    }
}
