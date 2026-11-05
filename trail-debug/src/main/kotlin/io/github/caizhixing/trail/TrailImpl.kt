package io.github.caizhixing.trail

import android.content.Context
import io.github.caizhixing.trail.model.Audience
import io.github.caizhixing.trail.model.Level
import io.github.caizhixing.trail.model.LogEvent
import io.github.caizhixing.trail.model.TrailConfig

/**
 * Shell-phase facade backed by the in-memory stub collector; issue #3 replaces
 * the collector wiring with the real engine and issue #4 implements trace.
 */
@PublishedApi
internal object TrailImpl {

    internal val recentEvents = InMemoryEventCollector()

    internal fun init(context: Context, config: TrailConfig) {
        // Shell phase: engine lands in #3 (ring/file pipeline, masking, dumpRecent).
    }

    @PublishedApi
    internal fun buildEvent(
        tag: String,
        name: String,
        audience: Audience,
        level: Level,
        attrs: Map<String, String>,
    ): LogEvent =
        LogEvent(
            ts = System.currentTimeMillis(),
            traceId = currentTraceId(),
            tag = tag,
            name = name,
            audience = audience,
            level = level,
            attrs = attrs,
            tid = Thread.currentThread().name,
        )

    @PublishedApi
    internal fun event(e: LogEvent) {
        recentEvents.add(e)
    }

    internal suspend fun <T> withTrace(name: String, block: suspend () -> T): T = block()

    internal fun currentTraceId(): String? = null

    internal fun beginTrace(name: String): AutoCloseable = AutoCloseable { }

    internal fun dumpRecent(count: Int): String = ""
}
