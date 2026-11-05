package io.github.caizhixing.trail.sample

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import io.github.caizhixing.trail.Trail
import io.github.caizhixing.trail.TrailGate

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "sample activity created")
        if (TrailGate.ENABLED) {
            Trail.init(this)
        }
        seedTrailCallSites()
        setContentView(TextView(this).apply { text = "Trail sample" })
    }

    private fun seedTrailCallSites() {
        Trail.event("sample", "trail_sample_created") { mapOf("source" to "main_activity") }
        Trail.flow("sample", "trail_sample_flow_started")
        Trail.event("sample", "trail_sample_event_map_overload", mapOf("style" to "map"))
        Trail.flow("sample", "trail_sample_flow_map_overload", mapOf("style" to "map"))
        LegacyJava.onScreenShown("main")
        LegacyJava.onDataLoaded(2)
        SampleTraceSeed.start()
    }

    private companion object {
        const val TAG = "TrailSample"
    }
}
