package io.github.caizhixing.trail.debug.engine

import android.util.Log

/**
 * Device-side defaults for the engine seams. Host JVM unit tests never touch
 * these: they inject fakes for the logger / echo sink / device provider, so
 * the android.util stubs are only exercised on a real device.
 */
internal object AndroidBridges {

    internal val logcatLogger: EngineLogger = EngineLogger { message ->
        Log.w(TAG, message)
    }

    internal val logcatEchoSink: (String) -> Unit = { line ->
        Log.d(TAG, line)
    }

    internal val deviceModelProvider: () -> String? = {
        runCatching { android.os.Build.MODEL }.getOrNull()
    }

    private const val TAG = "Trail"
}
