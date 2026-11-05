package io.github.caizhixing.trail.debug.engine

import java.util.concurrent.atomic.AtomicBoolean

/** Logs a message only on the first invocation (behavior 3.3: warn once, no spam). */
internal class OnceWarner(private val logger: EngineLogger) {

    private val armed = AtomicBoolean(false)

    fun warn(message: String) {
        if (armed.compareAndSet(false, true)) {
            logger.warn(message)
        }
    }
}
