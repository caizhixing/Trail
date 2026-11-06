package io.github.caizhixing.trail.sample

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import io.github.caizhixing.trail.Trail
import io.github.caizhixing.trail.TrailGate
import io.github.caizhixing.trail.model.Audience
import io.github.caizhixing.trail.model.Level

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "sample activity created")
        // Non-inline entry point: arguments evaluate eagerly, so it follows the
        // same guard rule as Java call sites (TRAIL_CONVENTIONS.md 5.2, rule 2).
        if (TrailGate.ENABLED) {
            Trail.init(this)
        }
        seedTrailCallSites()
        setContentView(TextView(this).apply { text = "Trail sample" })
    }

    private fun seedTrailCallSites() {
        // Inline + constant gate: eliminated at compile time, no guard needed.
        Trail.event(TRAIL_TAG, "trail_sample_created") { mapOf("source" to "main_activity") }
        Trail.flow(TRAIL_TAG, "trail_sample_flow_started")
        Trail.event(TRAIL_TAG, "trail_sample_explicit_level_warn", Audience.AI, Level.WARN) {
            mapOf("demo" to "explicit_overload")
        }
        // Map overloads evaluate eagerly — guarded like Java (conventions 5.2 rule 2).
        if (TrailGate.ENABLED) {
            Trail.event(TRAIL_TAG, "trail_sample_event_map_overload", mapOf("style" to "map"))
            Trail.flow(TRAIL_TAG, "trail_sample_flow_map_overload", mapOf("style" to "map"))
        }
        LegacyJava.onScreenShown("main")
        LegacyJava.onDataLoaded(2)
        LegacyJava.onCallbackInvoked("main")
        SampleTraceSeed.start()
    }

    private companion object {
        const val TAG = "TrailSample"

        /** Module-level tag constant per TRAIL_CONVENTIONS.md section 1. */
        const val TRAIL_TAG = "sample"
    }
}
