package io.github.caizhixing.trail.sample

import io.github.caizhixing.trail.Trail
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/** Module-level tag constant per TRAIL_CONVENTIONS.md section 1. */
private const val TRAIL_TAG = "sample"

/**
 * Seeding carrier for the withTrace R8 experiment (issue #2 behavior 2.11, D-1)
 * and the issue #8 seeding showcase: withTrace cannot be constant-gated
 * (DECISIONS.md "withTrace 为非 inline suspend 壳"), so this call site stays
 * live in the production variant and is what the dex scan reasons about. The
 * event/flow calls inside the block ARE inline-gated and vanish from the
 * production build once the gate folds to false. Uses only kotlin-stdlib
 * coroutine machinery (startCoroutine), no coroutines library dependency.
 */
internal object SampleTraceSeed {

    fun start() {
        val entry: suspend () -> Unit = {
            Trail.withTrace("trail_sample_trace_scope") {
                Trail.event(TRAIL_TAG, "trail_sample_event_inside_trace") { mapOf("depth" to "with_trace") }
                // Trace attribution demo: the ambient trace id is resolvable
                // anywhere inside the block (trace follows the coroutine).
                Trail.event(TRAIL_TAG, "trail_sample_event_trace_attributed") {
                    mapOf("trace_id" to (Trail.currentTraceId() ?: "unresolved"))
                }
                Trail.flow(TRAIL_TAG, "trail_sample_flow_trace_completed") { mapOf("scope" to "with_trace") }
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
