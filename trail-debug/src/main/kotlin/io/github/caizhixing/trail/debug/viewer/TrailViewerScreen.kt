package io.github.caizhixing.trail.debug.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Stateful host: collects the ViewModel state and routes to stateless
 * content. All business state stays in the ViewModel (behavior 5.8).
 */
@Composable
internal fun TrailViewerScreen(viewModel: ViewerViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val current = state) {
        ViewerUiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is ViewerUiState.Content -> current.selected?.let { event ->
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
        )
    }
}
