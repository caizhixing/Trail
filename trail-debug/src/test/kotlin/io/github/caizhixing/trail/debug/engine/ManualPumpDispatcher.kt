package io.github.caizhixing.trail.debug.engine

import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext

/**
 * Test fake: queued dispatch instead of immediate execution. Lets tests prove
 * the calling thread performs no IO before the pump (behavior 3.1) and then
 * drain the persist consumer deterministically (behavior 3.12).
 */
class ManualPumpDispatcher : CoroutineDispatcher() {

    private val pending = ArrayDeque<Runnable>()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        pending.addLast(block)
    }

    fun pump() {
        while (pending.isNotEmpty()) {
            pending.removeFirst().run()
        }
    }
}
