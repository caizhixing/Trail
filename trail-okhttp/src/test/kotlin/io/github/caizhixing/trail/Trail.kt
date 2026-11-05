package io.github.caizhixing.trail

import io.github.caizhixing.trail.model.Audience
import io.github.caizhixing.trail.model.Level
import kotlin.random.Random

/**
 * Test-only same-FQN facade for :trail-okhttp unit tests.
 *
 * trail-okhttp is a pure JVM module compiling against the trail-spi stub with
 * compileOnly; the stub jar is not on the test runtime classpath, so JVM
 * dispatch resolves these static calls to this object. Its JVM signatures are
 * identical to the SPI_SHARED_SUBSET locked by the three-module signature
 * tests, and its behavior mirrors the observable debug contract for exactly
 * the surface this module uses: explicit-traceId events are recorded
 * verbatim; withTrace scopes a generated `t_` + 16-hex id (P-7) that
 * currentTraceId reads back; no semantic forks (the block name is never used
 * as an id). When the trail-debug engine lands (#3/#4), the debug-side
 * integration stays covered by its own module tests and the #8 sample wiring.
 */
object Trail {

    data class RecordedEvent(
        val tag: String,
        val name: String,
        val traceId: String?,
        val audience: Audience,
        val level: Level,
        val attrs: Map<String, String>,
    )

    private val lock = Any()
    private val recorded = mutableListOf<RecordedEvent>()
    private val traceStack = ArrayDeque<String>()

    fun recordedEvents(): List<RecordedEvent> = synchronized(lock) { recorded.toList() }

    fun reset() {
        synchronized(lock) {
            recorded.clear()
            traceStack.clear()
        }
    }

    @JvmStatic
    fun event(tag: String, name: String, attrs: Map<String, String>) {
        record(tag, name, null, Audience.AI, Level.INFO, attrs)
    }

    @JvmStatic
    fun event(tag: String, name: String, audience: Audience, level: Level, attrs: Map<String, String>) {
        record(tag, name, null, audience, level, attrs)
    }

    @JvmStatic
    fun event(
        tag: String,
        name: String,
        traceId: String?,
        audience: Audience,
        level: Level,
        attrs: Map<String, String>,
    ) {
        record(tag, name, traceId, audience, level, attrs)
    }

    @JvmStatic
    fun flow(tag: String, name: String, attrs: Map<String, String>) {
        record(tag, name, null, Audience.HUMAN, Level.INFO, attrs)
    }

    @JvmStatic
    suspend fun <T> withTrace(name: String, block: suspend () -> T): T {
        val traceId = newTraceId()
        synchronized(lock) { traceStack.addLast(traceId) }
        try {
            return block()
        } finally {
            synchronized(lock) { traceStack.removeLastOrNull() }
        }
    }

    @JvmStatic
    fun currentTraceId(): String? = synchronized(lock) { traceStack.lastOrNull() }

    @JvmStatic
    fun dumpRecent(count: Int): String = ""

    private fun record(
        tag: String,
        name: String,
        traceId: String?,
        audience: Audience,
        level: Level,
        attrs: Map<String, String>,
    ) {
        synchronized(lock) { recorded += RecordedEvent(tag, name, traceId, audience, level, attrs) }
    }

    private fun newTraceId(): String {
        val hex = "0123456789abcdef"
        val suffix = buildString { repeat(16) { append(hex[Random.nextInt(hex.length)]) } }
        return "t_$suffix"
    }
}
