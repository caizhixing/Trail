package io.github.caizhixing.trail.debug.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.caizhixing.trail.debug.export.ExportSharePayload
import io.github.caizhixing.trail.debug.export.ResultState

/**
 * Stateful host: collects the ViewModel state and routes to stateless
 * content. All business state stays in the ViewModel (behavior 5.8). A ready
 * export payload (issue #6) is handed to [shareLauncher] once — the screen
 * never touches Context or intents itself; the Activity owns the system
 * share-sheet call.
 */
@Composable
internal fun TrailViewerScreen(
    viewModel: ViewerViewModel,
    shareLauncher: (ExportSharePayload) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val current = state) {
        ViewerUiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is ViewerUiState.Content -> {
            LaunchedEffect(current.export) {
                val export = current.export
                if (export is ResultState.Success) {
                    shareLauncher(export.data)
                    viewModel.onShareConsumed()
                }
            }
            current.selected?.let { event ->
                ViewerDetailContent(
                    event = event,
                    onBack = viewModel::onDetailDismissed,
                    onTraceIdSelected = viewModel::onTraceIdSelected,
                )
            } ?: ViewerListContent(
                state = current,
                onRefresh = viewModel::onRefresh,
                onLoadMore = viewModel::onLoadMore,
                onConciseViewToggled = viewModel::onConciseViewToggled,
                onAudienceToggled = viewModel::onAudienceToggled,
                onLevelToggled = viewModel::onLevelToggled,
                onTagsChanged = viewModel::onTagsChanged,
                onKeywordChanged = viewModel::onKeywordChanged,
                onTimeRangeChanged = viewModel::onTimeRangeChanged,
                onTraceIdSelected = viewModel::onTraceIdSelected,
                onFiltersCleared = viewModel::onFiltersCleared,
                onEventSelected = viewModel::onEventSelected,
                onExportRequested = viewModel::onExportRequested,
            )
        }
    }
}
