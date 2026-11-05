package io.github.caizhixing.trail.debug.viewer

import androidx.lifecycle.ViewModel
import io.github.caizhixing.trail.model.Audience
import io.github.caizhixing.trail.model.Level
import io.github.caizhixing.trail.model.LogEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Viewer orchestration (behaviors 5.1 / 5.3 / 5.4 / 5.7). The merged source
 * (memory ring ∪ file pages) is loaded incrementally; the displayed list is
 * the merged set narrowed by the active six-dimension filter. Dispatcher is
 * constructor-injected (JUnit4 tests pass `Unconfined`, DECISIONS.md「测试栈」).
 */
internal class ViewerViewModel(
    private val repository: ViewerRepository,
    private val preferences: ViewerPreferences,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private var mergedEvents: List<LogEvent> = emptyList()
    private var fileOffset: Int? = null
    private var endReached = false
    private var loadingOlder = false
    private var conciseView = ConciseViewPolicy.DEFAULT_CONCISE
    private var filter = EventFilter()
    private var selected: LogEvent? = null

    private val _uiState = MutableStateFlow<ViewerUiState>(ViewerUiState.Loading)
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    init {
        // Behavior 5.1 (P-1): first launch defaults to concise; an explicit
        // earlier choice is remembered and restored.
        conciseView = ConciseViewPolicy.initialConcise(preferences.conciseView())
        filter = EventFilter(audiences = ConciseViewPolicy.audiencesFor(conciseView))
        scope.launch { reload() }
    }

    /** Re-reads the memory ring, keeping any file pages already merged in. */
    fun onRefresh() {
        scope.launch { reload() }
    }

    /** Pagination trigger from the list (behavior 5.3 / 5.8). */
    fun onLoadMore() {
        if (loadingOlder || endReached) {
            return
        }
        loadingOlder = true
        publish()
        var grew = false
        while (!grew) {
            val offset = ViewerPaging.nextFileOffset(fileOffset, repository.fileEventCount())
            if (offset == null) {
                endReached = true
                break
            }
            fileOffset = offset
            val merged = ViewerPaging.mergeOlderPage(
                loaded = mergedEvents,
                filePage = repository.fileEvents(offset, ViewerPaging.PAGE_SIZE),
            )
            if (merged.size > mergedEvents.size) {
                mergedEvents = merged
                grew = true
            }
            // A fully duplicated page (already covered by the memory ring)
            // keeps walking older pages until something new appears.
        }
        loadingOlder = false
        publish()
    }

    /** Behavior 5.4: focusing an event's trace pins the trace filter. */
    fun onTraceIdSelected(traceId: String?) {
        filter = filter.copy(traceId = traceId)
        selected = null
        publish()
    }

    fun onEventSelected(event: LogEvent) {
        selected = event
        publish()
    }

    fun onDetailDismissed() {
        selected = null
        publish()
    }

    /** Behavior 5.1: toggling concise view applies and remembers the choice. */
    fun onConciseViewToggled(enabled: Boolean) {
        conciseView = enabled
        preferences.setConciseView(enabled)
        filter = filter.copy(audiences = ConciseViewPolicy.audiencesFor(enabled))
        publish()
    }

    /**
     * Manual audience edits always leave concise mode ("switch to full view
     * is remembered"); the chip set then behaves as a plain multi-select.
     */
    fun onAudienceToggled(audience: Audience) {
        val base = if (conciseView) ConciseViewPolicy.audiencesFor(true) else filter.audiences
        val next = if (audience in base) base - audience else base + audience
        conciseView = false
        preferences.setConciseView(false)
        filter = filter.copy(audiences = next)
        publish()
    }

    fun onLevelToggled(level: Level) {
        val next = if (level in filter.levels) filter.levels - level else filter.levels + level
        filter = filter.copy(levels = next)
        publish()
    }

    fun onTagsChanged(tags: Set<String>) {
        filter = filter.copy(tags = tags)
        publish()
    }

    fun onKeywordChanged(keyword: String) {
        val normalized = keyword.trim()
        filter = filter.copy(keyword = normalized.ifEmpty { null })
        publish()
    }

    fun onTimeRangeChanged(from: Long?, to: Long?) {
        filter = filter.copy(timeRange = TimeRange(from = from, to = to))
        publish()
    }

    fun onFiltersCleared() {
        filter = EventFilter(audiences = ConciseViewPolicy.audiencesFor(conciseView))
        publish()
    }

    override fun onCleared() {
        scope.cancel()
    }

    private suspend fun reload() {
        val fresh = ViewerPaging.initialPage(repository.memoryEvents())
        mergedEvents = if (mergedEvents.isEmpty()) {
            fresh
        } else {
            ViewerPaging.mergeOlderPage(loaded = fresh, filePage = mergedEvents)
        }
        if (repository.fileEventCount() == 0) {
            endReached = true
        }
        publish()
    }

    private fun publish() {
        _uiState.value = ViewerUiState.Content(
            events = mergedEvents.filter(filter::matches),
            filter = filter,
            conciseView = conciseView,
            loadingOlder = loadingOlder,
            endReached = endReached,
            selected = selected,
        )
    }
}
