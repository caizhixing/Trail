package io.github.caizhixing.trail.debug.trace

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Coroutine-context carrier of the active trace id. Attached by `withTrace`
 * and inherited by every child coroutine through structured concurrency, so
 * `launch` / `async` / `withContext` / Flow operator chains all observe the
 * surrounding trace without extra plumbing.
 */
internal class TraceContext(
    val traceId: String,
) : AbstractCoroutineContextElement(Key) {

    companion object Key : CoroutineContext.Key<TraceContext>
}
