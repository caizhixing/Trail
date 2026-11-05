package io.github.caizhixing.trail.debug.viewer

import android.content.ContextWrapper
import io.github.caizhixing.trail.TrailImpl
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavior 5.6: frozen notification contract — channel id literal
 * `trail_viewer`, manifest declares POST_NOTIFICATIONS (D-6) plus the
 * non-exported viewer activity, and TrailImpl routes `init` through the
 * injectable [ViewerNotifier] seam.
 */
class ViewerNotificationTest {

    @After
    fun restoreDefaultNotifier() {
        TrailImpl.viewerNotifier = AndroidViewerNotifier
    }

    @Test
    fun channelIdLiteralIsFrozen() {
        assertEquals("trail_viewer", VIEWER_NOTIFICATION_CHANNEL_ID)
    }

    @Test
    fun manifestDeclaresPostNotificationsAndNonExportedViewerActivity() {
        val manifest = manifestFile().readText()

        assertTrue(
            "Android 13+ POST_NOTIFICATIONS must be declared (D-6)",
            manifest.contains("android.permission.POST_NOTIFICATIONS"),
        )
        assertTrue(
            "viewer activity is declared with its full class name",
            manifest.contains("io.github.caizhixing.trail.debug.viewer.TrailViewerActivity"),
        )
        assertTrue(
            "viewer activity is not exported",
            manifest.contains("android:exported=\"false\""),
        )
    }

    @Test
    fun defaultNotifierIsTheAndroidImplementation() {
        assertSame(AndroidViewerNotifier, TrailImpl.viewerNotifier)
    }

    @Test
    fun notifyViewerEntryRoutesThroughTheInjectableNotifier() {
        val received = mutableListOf<android.content.Context>()
        TrailImpl.viewerNotifier = ViewerNotifier { context -> received.add(context) }
        val context = ContextWrapper(null)

        TrailImpl.notifyViewerEntry(context)

        assertEquals(listOf(context), received)
    }

    private fun manifestFile(): File {
        val candidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("trail-debug/src/main/AndroidManifest.xml"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("trail-debug AndroidManifest.xml not found from ${File(".").absolutePath}")
    }
}
