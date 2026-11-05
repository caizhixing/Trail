package io.github.caizhixing.trail

import io.github.caizhixing.trail.model.Audience
import io.github.caizhixing.trail.model.Level

/**
 * Internal compile-only stub (never published, never on a runtime classpath).
 * Signatures must stay JVM-identical to the real artifacts in trail-debug and
 * trail-noop: trail-okhttp compiles against these and at runtime dispatches to
 * whichever impl artifact the app classpath carries (same FQN).
 * Every function here is deliberately non-inline: an inline stub would bake the
 * stub constant values into consumer bytecode (DECISIONS.md "trail-spi 内部桩工件").
 */
object Trail {

    @JvmStatic
    fun event(tag: String, name: String, attrs: Map<String, String>) {
    }

    @JvmStatic
    fun event(tag: String, name: String, audience: Audience, level: Level, attrs: Map<String, String>) {
    }

    /**
     * Explicit traceId entry (PRD §2.3, contract revision A / issue #7 ruling):
     * lets non-coroutine callers such as the okhttp interceptor attribute an
     * event to the trace captured at the request call site. The stub stays
     * JVM-identical to the debug/noop facades and records nothing.
     */
    @JvmStatic
    fun event(
        tag: String,
        name: String,
        traceId: String?,
        audience: Audience,
        level: Level,
        attrs: Map<String, String>,
    ) {
    }

    @JvmStatic
    fun flow(tag: String, name: String, attrs: Map<String, String>) {
    }

    @JvmStatic
    suspend fun <T> withTrace(name: String, block: suspend () -> T): T = block()

    @JvmStatic
    fun currentTraceId(): String? = null

    @JvmStatic
    fun dumpRecent(count: Int): String = ""
}
