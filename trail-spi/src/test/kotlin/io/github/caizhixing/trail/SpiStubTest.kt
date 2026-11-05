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
            "event(java.lang.String,java.lang.String,java.lang.String,io.github.caizhixing.trail.model.Audience,io.github.caizhixing.trail.model.Level,java.util.Map)",
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
    fun mapOverloadStubsNeverReadAttrsAndRecordNothing() {
        val attrs = ReadCountingMap()

        Trail.event("widget", "trail_test_spi_map", attrs)
        Trail.event("widget", "trail_test_spi_map_explicit", Audience.BOTH, Level.WARN, attrs)
        Trail.flow("search", "trail_test_spi_flow_map", attrs)

        assertEquals(0, attrs.reads)
        assertEquals("", Trail.dumpRecent(10))
    }

    @Test
    fun explicitTraceIdOverloadStubNeverReadsAttrsAndRecordsNothing() {
        val attrs = ReadCountingMap()

        Trail.event("network", "trail_test_spi_explicit_trace", "t_0123456789abcdef", Audience.AI, Level.INFO, attrs)
        Trail.event("network", "trail_test_spi_explicit_trace_null", null, Audience.AI, Level.WARN, attrs)

        assertEquals(0, attrs.reads)
        assertEquals("", Trail.dumpRecent(10))
    }

    private fun signaturesOf(clazz: Class<*>): Set<String> =
        clazz.methods
            .filter { it.declaringClass == clazz }
            .map { method -> method.name + "(" + method.parameterTypes.joinToString(",") { type -> type.name } + ")" }
            .toSet()
}

/**
 * Observability probe for the Map-overload stubs: every read path of the Map
 * increments [reads], so a stub that touches its argument in any way (lookup /
 * iteration / size) breaks the reads == 0 assertion instead of passing
 * vacuously. The dumpRecent assertion covers the "records nothing" half.
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
