package io.github.caizhixing.trail

import android.content.Context
import io.github.caizhixing.trail.model.Audience
import io.github.caizhixing.trail.model.Level
import io.github.caizhixing.trail.model.LogEvent
import io.github.caizhixing.trail.model.TrailConfig

object Trail {

    @JvmStatic
    fun init(context: Context, config: TrailConfig = TrailConfig()) {
    }

    inline fun event(tag: String, name: String, attrs: () -> Map<String, String> = { emptyMap() }) {
        if (TrailGate.ENABLED) {
            TrailImpl.event(
                LogEvent(
                    ts = System.currentTimeMillis(),
                    traceId = null,
                    tag = tag,
                    name = name,
                    audience = Audience.AI,
                    level = Level.INFO,
                    attrs = attrs(),
                    tid = Thread.currentThread().name,
                ),
            )
        }
    }

    inline fun event(
        tag: String,
        name: String,
        audience: Audience,
        level: Level,
        attrs: () -> Map<String, String> = { emptyMap() },
    ) {
        if (TrailGate.ENABLED) {
            TrailImpl.event(
                LogEvent(
                    ts = System.currentTimeMillis(),
                    traceId = null,
                    tag = tag,
                    name = name,
                    audience = audience,
                    level = level,
                    attrs = attrs(),
                    tid = Thread.currentThread().name,
                ),
            )
        }
    }

    inline fun flow(tag: String, name: String, attrs: () -> Map<String, String> = { emptyMap() }) {
        if (TrailGate.ENABLED) {
            TrailImpl.event(
                LogEvent(
                    ts = System.currentTimeMillis(),
                    traceId = null,
                    tag = tag,
                    name = name,
                    audience = Audience.HUMAN,
                    level = Level.INFO,
                    attrs = attrs(),
                    tid = Thread.currentThread().name,
                ),
            )
        }
    }

    @JvmStatic
    fun event(tag: String, name: String, attrs: Map<String, String>) {
    }

    @JvmStatic
    fun event(tag: String, name: String, audience: Audience, level: Level, attrs: Map<String, String>) {
    }

    @JvmStatic
    fun flow(tag: String, name: String, attrs: Map<String, String>) {
    }

    @JvmStatic
    suspend fun <T> withTrace(name: String, block: suspend () -> T): T = block()

    @JvmStatic
    fun currentTraceId(): String? = null

    @JvmStatic
    fun beginTrace(name: String): AutoCloseable = AutoCloseable { }

    @JvmStatic
    fun dumpRecent(count: Int): String = ""
}
