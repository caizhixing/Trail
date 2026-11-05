package io.github.caizhixing.trail

import io.github.caizhixing.trail.model.Audience
import io.github.caizhixing.trail.model.Level
import io.github.caizhixing.trail.model.TrailConfig

object Trail {

    @JvmStatic
    fun init(context: android.content.Context, config: TrailConfig = TrailConfig()) {
        TrailImpl.init(context, config)
    }

    inline fun event(tag: String, name: String, attrs: () -> Map<String, String> = { emptyMap() }) {
        if (TrailGate.ENABLED) {
            TrailImpl.event(TrailImpl.buildEvent(tag, name, Audience.AI, Level.INFO, attrs()))
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
            TrailImpl.event(TrailImpl.buildEvent(tag, name, audience, level, attrs()))
        }
    }

    inline fun flow(tag: String, name: String, attrs: () -> Map<String, String> = { emptyMap() }) {
        if (TrailGate.ENABLED) {
            TrailImpl.event(TrailImpl.buildEvent(tag, name, Audience.HUMAN, Level.INFO, attrs()))
        }
    }

    @JvmStatic
    fun event(tag: String, name: String, attrs: Map<String, String>) {
        if (TrailGate.ENABLED) {
            TrailImpl.event(TrailImpl.buildEvent(tag, name, Audience.AI, Level.INFO, attrs))
        }
    }

    @JvmStatic
    fun event(tag: String, name: String, audience: Audience, level: Level, attrs: Map<String, String>) {
        if (TrailGate.ENABLED) {
            TrailImpl.event(TrailImpl.buildEvent(tag, name, audience, level, attrs))
        }
    }

    @JvmStatic
    fun flow(tag: String, name: String, attrs: Map<String, String>) {
        if (TrailGate.ENABLED) {
            TrailImpl.event(TrailImpl.buildEvent(tag, name, Audience.HUMAN, Level.INFO, attrs))
        }
    }

    @JvmStatic
    suspend fun <T> withTrace(name: String, block: suspend () -> T): T = TrailImpl.withTrace(name, block)

    @JvmStatic
    fun currentTraceId(): String? = TrailImpl.currentTraceId()

    @JvmStatic
    fun beginTrace(name: String): AutoCloseable = TrailImpl.beginTrace(name)

    @JvmStatic
    fun dumpRecent(count: Int): String = TrailImpl.dumpRecent(count)
}
