package io.github.caizhixing.trail.debug.trace

import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.asContextElement

/**
 * Thread-side resolution of the active trace id for the non-suspending
 * [io.github.caizhixing.trail.Trail.currentTraceId] entry point.
 *
 * Coroutine traces win over manual bridges: `withTrace` mirrors its id into a
 * dedicated ThreadLocal through a [ThreadContextElement], so the value follows
 * the coroutine across thread hops and is restored when the scope exits. As
 * long as that mirror holds a value it shadows the manual stack entirely.
 * [beginManualTrace] (callback / Java bridge) pushes onto a per-thread manual
 * stack, consulted only when no coroutine trace is active.
 */
internal object TraceBridge {

    private val coroutineTraceId: ThreadLocal<String?> = ThreadLocal.withInitial { null }

    private val manualStack: ThreadLocal<ArrayDeque<ManualFrame>> =
        object : ThreadLocal<ArrayDeque<ManualFrame>>() {
            override fun initialValue(): ArrayDeque<ManualFrame> = ArrayDeque()
        }

    /**
     * Context element binding [traceId] to the coroutine: the mirror ThreadLocal
     * is set on every thread the coroutine runs on and restored on exit.
     */
    fun coroutineElement(traceId: String): ThreadContextElement<String?> =
        coroutineTraceId.asContextElement(value = traceId)

    fun currentTraceId(): String? =
        coroutineTraceId.get() ?: manualFrames().lastOrNull()?.traceId

    fun beginManualTrace(): AutoCloseable {
        val frame = ManualFrame(traceId = TraceIds.newId())
        manualFrames().addLast(frame)
        return AutoCloseable { manualFrames().remove(frame) }
    }

    // ThreadLocal.get() is a platform type; initialValue guarantees non-null.
    private fun manualFrames(): ArrayDeque<ManualFrame> = requireNotNull(manualStack.get())

    private class ManualFrame(val traceId: String)
}
