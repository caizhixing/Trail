package io.github.caizhixing.trail.sample

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "sample activity created")
        setContentView(TextView(this).apply { text = "Trail sample" })
    }

    private companion object {
        const val TAG = "TrailSample"
    }
}
