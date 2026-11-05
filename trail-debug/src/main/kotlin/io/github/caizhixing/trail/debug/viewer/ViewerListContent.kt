package io.github.caizhixing.trail.debug.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.caizhixing.trail.debug.export.ExportScope
import io.github.caizhixing.trail.debug.export.ExportSharePayload
import io.github.caizhixing.trail.debug.export.ResultState
import io.github.caizhixing.trail.model.Audience
import io.github.caizhixing.trail.model.Level
import io.github.caizhixing.trail.model.LogEvent

/**
 * Stateless event list with the six-dimension filter panel, the export scope
 * entries (issue #6, minimal UI: three buttons) and the paging trigger
 * (behaviors 5.2 / 5.8 / 6.x). Scroll position is UI-local state; every
 * piece of business state arrives via [state] and leaves via callbacks.
 */
@Composable
internal fun ViewerListContent(
    state: ViewerUiState.Content,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onConciseViewToggled: (Boolean) -> Unit,
    onAudienceToggled: (Audience) -> Unit,
    onLevelToggled: (Level) -> Unit,
    onTagsChanged: (Set<String>) -> Unit,
    onKeywordChanged: (String) -> Unit,
    onTimeRangeChanged: (Long?, Long?) -> Unit,
    onTraceIdSelected: (String?) -> Unit,
    onFiltersCleared: () -> Unit,
    onEventSelected: (LogEvent) -> Unit,
    onExportRequested: (ExportScope) -> Unit,
) {
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(nearEnd) {
        if (nearEnd) onLoadMore()
    }
    // Empty first screen with history available: kick the file paging.
    LaunchedEffect(state.events.isEmpty(), state.endReached, state.loadingOlder) {
        if (state.events.isEmpty() && !state.endReached && !state.loadingOlder) {
            onLoadMore()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ViewerFilterPanel(
            state = state,
            onRefresh = onRefresh,
            onConciseViewToggled = onConciseViewToggled,
            onAudienceToggled = onAudienceToggled,
            onLevelToggled = onLevelToggled,
            onTagsChanged = onTagsChanged,
            onKeywordChanged = onKeywordChanged,
            onTimeRangeChanged = onTimeRangeChanged,
            onTraceIdSelected = onTraceIdSelected,
            onFiltersCleared = onFiltersCleared,
            onExportRequested = onExportRequested,
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(count = state.events.size, key = { index -> index }) { index ->
                ViewerEventRow(
                    event = state.events[index],
                    onClick = { onEventSelected(state.events[index]) },
                    onTraceIdSelected = onTraceIdSelected,
                )
            }
            if (state.loadingOlder) {
                item(key = "loading-older") {
                    Text(
                        text = "loading earlier events…",
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                    )
                }
            }
            if (state.endReached) {
                item(key = "end-reached") {
                    Text(
                        text = "no earlier events",
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewerEventRow(
    event: LogEvent,
    onClick: () -> Unit,
    onTraceIdSelected: (String?) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(text = "${event.level} · ${event.tag}/${event.name}")
                Text(text = event.audience.name)
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(text = TIME_FORMAT.format(event.ts), fontFamily = FontFamily.Monospace)
                event.traceId?.let { traceId ->
                    TextButton(onClick = { onTraceIdSelected(traceId) }) {
                        Text(text = traceId, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            if (event.attrs.isNotEmpty()) {
                val preview = event.attrs.entries.take(ATTR_PREVIEW_COUNT).joinToString(" ") {
                    "${it.key}=${it.value}"
                }
                Text(text = preview)
            }
        }
    }
}

@Composable
private fun ViewerFilterPanel(
    state: ViewerUiState.Content,
    onRefresh: () -> Unit,
    onConciseViewToggled: (Boolean) -> Unit,
    onAudienceToggled: (Audience) -> Unit,
    onLevelToggled: (Level) -> Unit,
    onTagsChanged: (Set<String>) -> Unit,
    onKeywordChanged: (String) -> Unit,
    onTimeRangeChanged: (Long?, Long?) -> Unit,
    onTraceIdSelected: (String?) -> Unit,
    onFiltersCleared: () -> Unit,
    onExportRequested: (ExportScope) -> Unit,
) {
    val filter = state.filter
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "concise view")
            Switch(checked = state.conciseView, onCheckedChange = onConciseViewToggled)
            TextButton(onClick = onRefresh) { Text(text = "refresh") }
            TextButton(onClick = onFiltersCleared) { Text(text = "clear filters") }
        }
        filter.traceId?.let { traceId ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "trace: $traceId", fontFamily = FontFamily.Monospace)
                TextButton(onClick = { onTraceIdSelected(null) }) { Text(text = "clear") }
            }
        }
        OutlinedTextField(
            value = filter.keyword.orEmpty(),
            onValueChange = onKeywordChanged,
            label = { Text(text = "keyword (tag / name / attr values)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = filter.tags.singleOrNull().orEmpty(),
            onValueChange = { tag ->
                onTagsChanged(if (tag.isBlank()) emptySet() else setOf(tag))
            },
            label = { Text(text = "tag (exact)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Audience.entries.forEach { audience ->
                FilterChip(
                    selected = audience in filter.audiences,
                    onClick = { onAudienceToggled(audience) },
                    label = { Text(text = audience.name) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Level.entries.forEach { level ->
                FilterChip(
                    selected = level in filter.levels,
                    onClick = { onLevelToggled(level) },
                    label = { Text(text = level.name) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = filter.timeRange.from?.toString().orEmpty(),
                onValueChange = { text -> onTimeRangeChanged(text.toLongOrNull(), filter.timeRange.to) },
                label = { Text(text = "from ts(ms)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = filter.timeRange.to?.toString().orEmpty(),
                onValueChange = { text -> onTimeRangeChanged(filter.timeRange.from, text.toLongOrNull()) },
                label = { Text(text = "to ts(ms)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        ExportScopeEntries(
            filter = filter,
            export = state.export,
            onExportRequested = onExportRequested,
        )
    }
}

/**
 * Export menu (issue #6, minimal): one entry per scope — the current filter,
 * the last [RECENT_EXPORT_HOURS] hours and the pinned trace. The trace entry
 * only appears while a trace is pinned; export progress/failure is a single
 * status line, the ready payload is consumed by the share launcher.
 */
@Composable
private fun ExportScopeEntries(
    filter: EventFilter,
    export: ResultState<ExportSharePayload>?,
    onExportRequested: (ExportScope) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { onExportRequested(ExportScope.CurrentFilter(filter)) }) {
            Text(text = "export: filtered")
        }
        TextButton(onClick = { onExportRequested(ExportScope.RecentHours(RECENT_EXPORT_HOURS)) }) {
            Text(text = "export: last ${RECENT_EXPORT_HOURS}h")
        }
    }
    filter.traceId?.let { traceId ->
        TextButton(onClick = { onExportRequested(ExportScope.Trace(traceId)) }) {
            Text(text = "export: this trace")
        }
    }
    when (export) {
        is ResultState.Loading -> Text(text = "exporting…")
        is ResultState.Error -> Text(text = export.message)
        is ResultState.Success -> Unit
        null -> Unit
    }
}

private const val LOAD_MORE_THRESHOLD = 10
private const val ATTR_PREVIEW_COUNT = 2
private const val RECENT_EXPORT_HOURS = 1
private val TIME_FORMAT = java.text.SimpleDateFormat("MM-dd HH:mm:ss.SSS", java.util.Locale.US)
