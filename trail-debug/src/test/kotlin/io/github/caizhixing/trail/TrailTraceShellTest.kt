package io.github.caizhixing.trail

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class TrailTraceShellTest {

    @Before
    fun resetCollector() {
        TrailImpl.recentEvents.clear()
    }

    @Test
    fun withTraceShellReturnsBlockValue() = runBlocking {
        val result = Trail.withTrace("trail_test_trace_scope") { "trail_block_result" }

        assertEquals("trail_block_result", result)
    }

    @Test
    fun withTraceShellPropagatesBlockExceptionUnchanged() = runBlocking {
        val boom = IllegalStateException("trail_boom")
        val thrown = runCatching {
            Trail.withTrace<String>("trail_test_trace_scope") { throw boom }
        }.exceptionOrNull()

        assertSame(boom, thrown)
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
    fun currentTraceIdIsNullInShellPhase() {
        assertNull(Trail.currentTraceId())
    }

    @Test
    fun currentTraceIdStaysNullInsideShellWithTrace() = runBlocking {
        val seen = Trail.withTrace("trail_test_trace_scope") { Trail.currentTraceId() }

        assertNull(seen)
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
    fun beginTraceShellIsNoOpCloseable() {
        Trail.beginTrace("trail_test_trace_scope").use {
            assertNull(Trail.currentTraceId())
        }
        assertNull(Trail.currentTraceId())
    }
}
