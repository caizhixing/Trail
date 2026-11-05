package io.github.caizhixing.trail.debug.viewer

import io.github.caizhixing.trail.debug.export.ExportSharePayload
import io.github.caizhixing.trail.debug.export.ResultState
import io.github.caizhixing.trail.model.LogEvent

/**
 * Single immutable view state exposed through a StateFlow (behavior 5.7).
 * All business state — filters, paging progress, selection — lives here and
 * in the ViewModel; composables stay stateless.
 */
internal sealed interface ViewerUiState {

    data object Loading : ViewerUiState

    data class Content(
        /** Filtered, timestamp-descending events currently displayed. */
        val events: List<LogEvent>,
        val filter: EventFilter,
        val conciseView: Boolean,
        /** True while an older file page is being merged. */
        val loadingOlder: Boolean,
        /** True when the merged memory ∪ file source is exhausted. */
        val endReached: Boolean,
        /** Non-null while the detail pane is open (full attrs, no truncation). */
        val selected: LogEvent?,
        /**
         * Latest export action (issue #6): null until the first export is
         * requested, Loading while the file is written, Success with the share
         * payload for the screen to launch the system share sheet, Error with
         * a message on failure.
         */
        val export: ResultState<ExportSharePayload>? = null,
    ) : ViewerUiState
}
