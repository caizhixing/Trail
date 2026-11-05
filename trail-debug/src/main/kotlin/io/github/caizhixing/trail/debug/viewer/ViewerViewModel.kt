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
import kotlinx.coroutines.withContext

/**
 * Viewer orchestration (behaviors 5.1 / 5.3 / 5.4 / 5.7). The merged source
 * (memory ring ∪ file pages) is loaded incrementally; the displayed list is
 * the merged set narrowed by the active six-dimension filter. Both the UI
 * dispatcher and the IO dispatcher are constructor-injected (JUnit4 tests
 * pass `Unconfined` for each, DECISIONS.md「测试栈」).
 */
internal class ViewerViewModel(
    private val repository: ViewerRepository,
    private val preferences: ViewerPreferences,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private var memorySnapshot: List<LogEvent> = emptyList()
    private var loadedFileEvents: List<LogEvent> = emptyList()
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

    /**
     * Pagination trigger from the list (behavior 5.3 / 5.8). Runs as a
     * coroutine so every history read happens on the IO dispatcher — never
     * on the calling (UI) thread — and `loadingOlder` stays observably true
     * for the whole read (review r1 blocker).
     */
    fun onLoadMore() {
        if (loadingOlder || endReached) {
            return
        }
        loadingOlder = true
        publish()
        scope.launch {
            try {
                walkOlderFilePages()
            } catch (_: Exception) {
                // A failed history read keeps the merged list as-is; the
                // flag reset below makes the failure observable and the
                // uncommitted page retryable.
            } finally {
                loadingOlder = false
                publish()
            }
        }
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
     * Audience chips are a session-scoped temporary switch (main-agent
     * ruling ②): the in-memory filter changes and concise mode is left for
     * this session, but nothing is persisted — only the explicit
     * concise/full switch writes preferences, so a fresh viewer restores
     * the persisted choice.
     */
    fun onAudienceToggled(audience: Audience) {
        val base = if (conciseView) ConciseViewPolicy.audiencesFor(true) else filter.audiences
        val next = if (audience in base) base - audience else base + audience
        conciseView = false
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
        // The ring lives in memory; only the history count touches disk.
        memorySnapshot = repository.memoryEvents()
        if (withContext(ioDispatcher) { repository.fileEventCount() } == 0) {
            endReached = true
        }
        mergedEvents = ViewerPaging.mergeSources(memorySnapshot, loadedFileEvents)
        publish()
    }

    /**
     * Newest-first walk of the on-disk history with all reads dispatched to
     * [ioDispatcher]. A fully ring-covered page (nothing new after the
     * cross-source dedupe) keeps the walk going until something new appears.
     */
    private suspend fun walkOlderFilePages() {
        var grew = false
        while (!grew) {
            val fileCount = withContext(ioDispatcher) { repository.fileEventCount() }
            val offset = ViewerPaging.nextFileOffset(fileOffset, fileCount)
            if (offset == null) {
                endReached = true
                return
            }
            val page = withContext(ioDispatcher) {
                repository.fileEvents(offset, ViewerPaging.pageLimitFor(offset, fileOffset))
            }
            // Commit the offset only after a successful read so a failed
            // page can be retried without skipping history lines.
            fileOffset = offset
            if (page.isNotEmpty()) {
                loadedFileEvents = loadedFileEvents + page
            }
            val merged = ViewerPaging.mergeSources(memorySnapshot, loadedFileEvents)
            grew = merged.size > mergedEvents.size
            mergedEvents = merged
        }
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
