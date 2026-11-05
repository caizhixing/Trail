package io.github.caizhixing.trail.debug.viewer

import android.content.Context

/**
 * Frozen notification contract (issue #5): channel id literal `trail_viewer`.
 */
internal const val VIEWER_NOTIFICATION_CHANNEL_ID: String = "trail_viewer"

/**
 * Injectable seam for the persistent viewer entry notification (behavior
 * 5.6). TrailImpl calls it once per `init`; tests inject fakes to assert the
 * wiring without touching framework classes.
 */
internal fun interface ViewerNotifier {
    fun showEntryNotification(context: Context)
}
