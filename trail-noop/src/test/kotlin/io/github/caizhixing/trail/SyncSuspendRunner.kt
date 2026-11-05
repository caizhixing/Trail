package io.github.caizhixing.trail

import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * trail-noop has no coroutines test dependency, so suspend tests run on the
 * bare kotlin-stdlib coroutine machinery: withTrace is a pass-through shell
 * with no suspension point, so the block always completes synchronously.
 */
internal class SyncSuspendRunner<T> : Continuation<T> {

    override val context: CoroutineContext = EmptyCoroutineContext

    var outcome: Result<T>? = null
        private set

    override fun resumeWith(result: Result<T>) {
        outcome = result
    }
}

internal fun <T> runSuspendBlock(block: suspend () -> T): Result<T> {
    val runner = SyncSuspendRunner<T>()
    block.startCoroutine(runner)
    return checkNotNull(runner.outcome) { "suspend block did not complete synchronously" }
}
