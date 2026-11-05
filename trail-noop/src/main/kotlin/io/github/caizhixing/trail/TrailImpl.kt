package io.github.caizhixing.trail

import io.github.caizhixing.trail.model.LogEvent

/**
 * No-op facade: every member is an empty body. The inline call sites fold away
 * entirely before R8 even runs, so this object never reaches a production dex.
 */
@PublishedApi
internal object TrailImpl {

    @PublishedApi
    internal fun event(e: LogEvent) {
    }

    internal suspend fun <T> withTrace(name: String, block: suspend () -> T): T = block()
}
