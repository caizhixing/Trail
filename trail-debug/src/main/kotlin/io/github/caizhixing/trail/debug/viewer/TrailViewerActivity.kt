package io.github.caizhixing.trail.debug.viewer

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.caizhixing.trail.TrailImpl
import io.github.caizhixing.trail.debug.export.AndroidExportSharer
import io.github.caizhixing.trail.debug.export.SnapshotTrailEventExporter
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * In-app event viewer entry (behavior 5.9). Declared `exported="false"` in
 * the manifest; only reachable via the notification's explicit PendingIntent
 * or the [intent] factory.
 */
class TrailViewerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = SharedPreferencesViewerPreferences(this)
        setContent {
            MaterialTheme {
                val viewerViewModel: ViewerViewModel = viewModel(
                    factory = viewerFactory(preferences),
                )
                TrailViewerScreen(
                    viewModel = viewerViewModel,
                    shareLauncher = { payload -> AndroidExportSharer.launch(this, payload) },
                )
            }
        }
    }

    private fun viewerFactory(preferences: ViewerPreferences): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repository = SnapshotViewerRepository(TrailImpl.snapshotStore())
                val viewModel = ViewerViewModel(
                    repository = repository,
                    preferences = preferences,
                    dispatcher = Dispatchers.Main.immediate,
                    exporter = SnapshotTrailEventExporter(
                        store = TrailImpl.snapshotStore(),
                        exportsDir = File(cacheDir, EXPORTS_DIR_NAME),
                        appProvider = { packageName },
                        versionProvider = { resolveVersionName() },
                        deviceProvider = { Build.MODEL },
                    ),
                )
                @Suppress("UNCHECKED_CAST")
                return viewModel as T
            }
        }

    private fun resolveVersionName(): String? =
        try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) {
            null
        }

    companion object {

        /** Explicit-intent factory; the viewer is never addressable implicitly. */
        fun intent(context: Context): Intent = Intent(context, TrailViewerActivity::class.java)

        private const val EXPORTS_DIR_NAME = "trail_exports"
    }
}
