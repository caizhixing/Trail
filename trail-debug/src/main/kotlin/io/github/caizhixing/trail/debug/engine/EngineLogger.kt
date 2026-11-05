package io.github.caizhixing.trail.debug.engine

/** Minimal logging port for engine internals (queue overflow, IO failures). */
internal fun interface EngineLogger {
    fun warn(message: String)
}
