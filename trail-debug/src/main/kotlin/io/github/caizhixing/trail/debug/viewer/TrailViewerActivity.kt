package io.github.caizhixing.trail.debug.viewer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.caizhixing.trail.TrailImpl
import kotlinx.coroutines.Dispatchers

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
                TrailViewerScreen(viewModel = viewerViewModel)
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
                )
                @Suppress("UNCHECKED_CAST")
                return viewModel as T
            }
        }

    companion object {

        /** Explicit-intent factory; the viewer is never addressable implicitly. */
        fun intent(context: Context): Intent = Intent(context, TrailViewerActivity::class.java)
    }
}
