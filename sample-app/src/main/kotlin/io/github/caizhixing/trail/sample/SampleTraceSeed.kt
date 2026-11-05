package io.github.caizhixing.trail.sample

import io.github.caizhixing.trail.Trail
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * Seeding carrier for the withTrace R8 experiment (issue #2 behavior 2.11, D-1):
 * withTrace cannot be constant-gated (DECISIONS.md "withTrace 为非 inline suspend 壳"),
 * so this call site stays live in the production variant and is what the dex scan
 * reasons about. Uses only kotlin-stdlib coroutine machinery (startCoroutine),
 * no coroutines library dependency.
 */
internal object SampleTraceSeed {

    fun start() {
        val entry: suspend () -> Unit = {
            Trail.withTrace("trail_sample_trace_scope") {
                Trail.event("sample", "trail_sample_event_inside_trace") { mapOf("depth" to "with_trace") }
                LegacyJava.onTraceBlockEntered()
            }
        }
        entry.startCoroutine(object : Continuation<Unit> {
            override val context: CoroutineContext = EmptyCoroutineContext

            override fun resumeWith(result: Result<Unit>) {
            }
        })
    }
}
