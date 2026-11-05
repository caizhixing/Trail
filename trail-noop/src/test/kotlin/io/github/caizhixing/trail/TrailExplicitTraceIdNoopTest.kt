package io.github.caizhixing.trail

import io.github.caizhixing.trail.model.Audience
import io.github.caizhixing.trail.model.Level
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Behavior 7.0 (ruling A, noop side): the explicit traceId overload is an
 * empty body that never touches its arguments — the TraceProbeMap probe
 * breaks reads == 0 if any read path (lookup / iteration / size) is reached,
 * and dumpRecent covers the "records nothing" half. The probe class is
 * renamed (vs. NoopTrailBehaviorTest.ReadCountingMap) because two same-named
 * file-private classes in one package do not resolve independently.
 */
class TrailExplicitTraceIdNoopTest {

    @Test
    fun explicitTraceIdOverloadNeverReadsAttrsAndRecordsNothing() {
        val attrs = TraceProbeMap()

        Trail.event("network", "trail_test_noop_explicit_trace", "t_0123456789abcdef", Audience.AI, Level.INFO, attrs)
        Trail.event("network", "trail_test_noop_explicit_trace_null", null, Audience.AI, Level.WARN, attrs)

        assertEquals(0, attrs.reads)
        assertEquals("", Trail.dumpRecent(10))
    }
}

/**
 * Same observability probe as in NoopTrailBehaviorTest: every read path of
 * the Map increments [reads].
 */
private class TraceProbeMap : Map<String, String> {

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
