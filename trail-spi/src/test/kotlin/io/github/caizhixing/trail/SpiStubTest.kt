package io.github.caizhixing.trail

import io.github.caizhixing.trail.model.Audience
import io.github.caizhixing.trail.model.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavior 2.9 (spi side): the stub must expose exactly the JVM signatures that
 * trail-debug and trail-noop provide for the non-inline subset (Map overloads +
 * withTrace + currentTraceId + dumpRecent), so trail-okhttp compiled against
 * this stub resolves identically on an impl classpath.
 */
class SpiStubTest {

    @Test
    fun trailStubClassLoads() {
        assertNotNull(Class.forName("io.github.caizhixing.trail.Trail"))
    }

    @Test
    fun trailGateStubClassLoads() {
        assertNotNull(Class.forName("io.github.caizhixing.trail.TrailGate"))
    }

    @Test
    fun stubExposesOnlyTheSharedNonInlineJvmSignatures() {
        val signatures = signaturesOf(Trail::class.java)

        val expected = setOf(
            "event(java.lang.String,java.lang.String,java.util.Map)",
            "event(java.lang.String,java.lang.String,io.github.caizhixing.trail.model.Audience,io.github.caizhixing.trail.model.Level,java.util.Map)",
            "flow(java.lang.String,java.lang.String,java.util.Map)",
            "withTrace(java.lang.String,kotlin.jvm.functions.Function1,kotlin.coroutines.Continuation)",
            "currentTraceId()",
            "dumpRecent(int)",
        )

        assertEquals(expected, signatures)
    }

    @Test
    fun withTraceStubPassesBlockValueThrough() {
        val outcome = runSuspendBlock {
            Trail.withTrace("trail_test_spi_trace") { "trail_spi_result" }
        }

        assertEquals("trail_spi_result", outcome.getOrThrow())
    }

    @Test
    fun withTraceStubPropagatesBlockExceptionUnchanged() {
        val boom = IllegalStateException("trail_spi_boom")
        val outcome = runSuspendBlock {
            Trail.withTrace<String>("trail_test_spi_trace") { throw boom }
        }

        assertTrue(outcome.isFailure)
        assertSame(boom, outcome.exceptionOrNull())
    }

    @Test
    fun currentTraceIdStubIsNull() {
        assertNull(Trail.currentTraceId())
    }

    @Test
    fun dumpRecentStubIsEmptyString() {
        assertEquals("", Trail.dumpRecent(7))
    }

    @Test
    fun mapOverloadStubsDoNotCrash() {
        Trail.event("widget", "trail_test_spi_map", mapOf("k" to "v"))
        Trail.event("widget", "trail_test_spi_map_explicit", Audience.BOTH, Level.WARN, mapOf("k" to "v"))
        Trail.flow("search", "trail_test_spi_flow_map", mapOf("k" to "v"))

        assertTrue(true)
    }

    private fun signaturesOf(clazz: Class<*>): Set<String> =
        clazz.methods
            .filter { it.declaringClass == clazz }
            .map { method -> method.name + "(" + method.parameterTypes.joinToString(",") { type -> type.name } + ")" }
            .toSet()
}
