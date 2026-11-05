package io.github.caizhixing.trail.debug.viewer

import io.github.caizhixing.trail.model.Audience
import io.github.caizhixing.trail.model.Level
import io.github.caizhixing.trail.model.LogEvent
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ViewModel orchestration (behaviors 5.1 / 5.3 / 5.4 / 5.7) with both the
 * UI and IO dispatchers injected as `Unconfined` plus fake repository /
 * preferences — the whole viewer business logic, including the paging walk,
 * runs synchronously on the JVM (DECISIONS.md「测试栈」).
 */
class ViewerViewModelTest {

    private class FakeViewerRepository(
        var memory: List<LogEvent> = emptyList(),
        var file: List<LogEvent> = emptyList(),
    ) : ViewerRepository {
        val fileCalls = mutableListOf<Pair<Int, Int>>()
        var diskReads = 0
        var onDiskRead: () -> Unit = {}
        var failNextFileRead = false

        override fun memoryEvents(): List<LogEvent> = memory

        override fun fileEvents(offset: Int, limit: Int): List<LogEvent> {
            fileCalls.add(offset to limit)
            diskReads++
            onDiskRead()
            if (failNextFileRead) {
                failNextFileRead = false
                throw IOException("simulated history read failure")
            }
            return file.drop(offset).take(limit)
        }

        override fun fileEventCount(): Int {
            diskReads++
            onDiskRead()
            return file.size
        }
    }

    private class FakePreferences : ViewerPreferences {
        var stored: Boolean? = null
        var writeCount = 0

        override fun conciseView(): Boolean? = stored

        override fun setConciseView(value: Boolean) {
            stored = value
            writeCount++
        }
    }

    private fun event(
        ts: Long,
        tag: String = "widget",
        name: String = "e$ts",
        audience: Audience = Audience.BOTH,
        level: Level = Level.INFO,
        traceId: String? = null,
    ): LogEvent = LogEvent(
        ts = ts,
        traceId = traceId,
        tag = tag,
        name = name,
        audience = audience,
        level = level,
        attrs = emptyMap(),
        tid = null,
    )

    private fun newViewModel(
        repository: FakeViewerRepository,
        preferences: ViewerPreferences = FakePreferences(),
    ): ViewerViewModel = ViewerViewModel(
        repository,
        preferences,
        Dispatchers.Unconfined,
        Dispatchers.Unconfined,
    )

    private fun content(state: ViewerUiState): ViewerUiState.Content =
        state as ViewerUiState.Content

    @Test
    fun firstLaunchDefaultsToConciseHumanAndBothOnly() = runBlocking {
        val repository = FakeViewerRepository(
            memory = listOf(
                event(100, audience = Audience.HUMAN),
                event(200, audience = Audience.AI),
                event(300, audience = Audience.BOTH),
            ),
        )
        val viewModel = newViewModel(repository)

        val state = content(viewModel.uiState.value)

        assertTrue(state.conciseView)
        assertEquals(setOf(Audience.HUMAN, Audience.BOTH), state.filter.audiences)
        assertEquals("AI events hidden, newest first", listOf(300L, 100L), state.events.map { it.ts })
        assertFalse(state.loadingOlder)
    }

    @Test
    fun rememberedFullViewShowsEveryAudience() = runBlocking {
        val preferences = FakePreferences().apply { stored = false }
        val repository = FakeViewerRepository(
            memory = listOf(
                event(100, audience = Audience.HUMAN),
                event(200, audience = Audience.AI),
            ),
        )
        val viewModel = newViewModel(repository, preferences)

        val state = content(viewModel.uiState.value)

        assertFalse(state.conciseView)
        assertEquals(emptySet<Audience>(), state.filter.audiences)
        assertEquals(listOf(200L, 100L), state.events.map { it.ts })
    }

    @Test
    fun rememberedConciseChoiceRestores() = runBlocking {
        val preferences = FakePreferences().apply { stored = true }
        val repository = FakeViewerRepository(
            memory = listOf(event(100, audience = Audience.AI)),
        )
        val viewModel = newViewModel(repository, preferences)

        assertTrue(content(viewModel.uiState.value).conciseView)
        assertTrue(content(viewModel.uiState.value).events.isEmpty())
    }

    @Test
    fun togglingConciseViewIsRemembered() = runBlocking {
        val preferences = FakePreferences()
        val viewModel = newViewModel(
            FakeViewerRepository(memory = listOf(event(100))),
            preferences,
        )

        viewModel.onConciseViewToggled(false)

        val full = content(viewModel.uiState.value)
        assertFalse(full.conciseView)
        assertEquals(emptySet<Audience>(), full.filter.audiences)
        assertEquals(false, preferences.stored)

        viewModel.onConciseViewToggled(true)

        val concise = content(viewModel.uiState.value)
        assertTrue(concise.conciseView)
        assertEquals(setOf(Audience.HUMAN, Audience.BOTH), concise.filter.audiences)
        assertEquals(true, preferences.stored)
    }

    @Test
    fun audienceChipSwitchIsSessionScopedAndRestartRestoresConcise() = runBlocking {
        val preferences = FakePreferences()
        val repository = FakeViewerRepository(
            memory = listOf(
                event(100, audience = Audience.HUMAN),
                event(200, audience = Audience.AI),
            ),
        )
        val viewModel = newViewModel(repository, preferences)

        viewModel.onAudienceToggled(Audience.AI)

        val state = content(viewModel.uiState.value)
        assertFalse("manual audience edit leaves concise mode for this session", state.conciseView)
        assertEquals(setOf(Audience.HUMAN, Audience.BOTH, Audience.AI), state.filter.audiences)
        assertEquals(listOf(200L, 100L), state.events.map { it.ts })

        viewModel.onAudienceToggled(Audience.HUMAN)
        assertEquals(setOf(Audience.BOTH, Audience.AI), content(viewModel.uiState.value).filter.audiences)
        assertEquals(listOf(200L), content(viewModel.uiState.value).events.map { it.ts })

        assertNull("chips never write preferences", preferences.stored)
        assertEquals(0, preferences.writeCount)

        val restarted = newViewModel(repository, preferences)
        val restored = content(restarted.uiState.value)
        assertTrue("restart falls back to the persisted choice: still concise", restored.conciseView)
        assertEquals(setOf(Audience.HUMAN, Audience.BOTH), restored.filter.audiences)
        assertEquals("AI hidden again after restart", listOf(100L), restored.events.map { it.ts })
    }

    @Test
    fun levelToggleFiltersTheDisplayedList() = runBlocking {
        val repository = FakeViewerRepository(
            memory = listOf(
                event(100, level = Level.INFO),
                event(200, level = Level.ERROR),
            ),
        )
        val viewModel = newViewModel(repository)

        viewModel.onLevelToggled(Level.ERROR)

        assertEquals(setOf(Level.ERROR), content(viewModel.uiState.value).filter.levels)
        assertEquals(listOf(200L), content(viewModel.uiState.value).events.map { it.ts })

        viewModel.onLevelToggled(Level.ERROR)
        assertEquals(emptySet<Level>(), content(viewModel.uiState.value).filter.levels)
        assertEquals(2, content(viewModel.uiState.value).events.size)
    }

    @Test
    fun tagFilterMatchesExactly() = runBlocking {
        val repository = FakeViewerRepository(
            memory = listOf(event(100, tag = "payment"), event(200, tag = "widget")),
        )
        val viewModel = newViewModel(repository)

        viewModel.onTagsChanged(setOf("payment"))

        assertEquals(listOf(100L), content(viewModel.uiState.value).events.map { it.ts })

        viewModel.onTagsChanged(emptySet())
        assertEquals(2, content(viewModel.uiState.value).events.size)
    }

    @Test
    fun keywordFilterSearchesTagAndNameAndBlankClears() = runBlocking {
        val repository = FakeViewerRepository(
            memory = listOf(
                event(100, tag = "payment", name = "charged"),
                event(200, tag = "widget", name = "rendered"),
            ),
        )
        val viewModel = newViewModel(repository)

        viewModel.onKeywordChanged("charged")

        assertEquals(listOf(100L), content(viewModel.uiState.value).events.map { it.ts })

        viewModel.onKeywordChanged("   ")

        assertEquals("blank keyword lifts the restriction", 2, content(viewModel.uiState.value).events.size)
        assertNull(content(viewModel.uiState.value).filter.keyword)
    }

    @Test
    fun timeRangeFilterIsInclusive() = runBlocking {
        val repository = FakeViewerRepository(
            memory = listOf(event(100), event(200), event(300)),
        )
        val viewModel = newViewModel(repository)

        viewModel.onTimeRangeChanged(from = 200L, to = 300L)

        assertEquals(listOf(300L, 200L), content(viewModel.uiState.value).events.map { it.ts })
    }

    @Test
    fun traceIdSelectionFiltersAndClearingRestores() = runBlocking {
        val repository = FakeViewerRepository(
            memory = listOf(
                event(100, traceId = "t_a"),
                event(200, traceId = "t_b"),
                event(300, traceId = "t_a"),
            ),
        )
        val viewModel = newViewModel(repository)

        viewModel.onTraceIdSelected("t_a")

        val traced = content(viewModel.uiState.value)
        assertEquals("t_a", traced.filter.traceId)
        assertEquals(listOf(300L, 100L), traced.events.map { it.ts })
        assertNull("detail closes when a trace is pinned", traced.selected)

        viewModel.onTraceIdSelected(null)

        val restored = content(viewModel.uiState.value)
        assertNull(restored.filter.traceId)
        assertEquals(3, restored.events.size)
    }

    @Test
    fun eventSelectionOpensAndDismissesDetail() = runBlocking {
        val target = event(100)
        val repository = FakeViewerRepository(memory = listOf(target, event(200)))
        val viewModel = newViewModel(repository)

        viewModel.onEventSelected(target)
        assertSame(target, content(viewModel.uiState.value).selected)

        viewModel.onDetailDismissed()
        assertNull(content(viewModel.uiState.value).selected)
    }

    @Test
    fun loadMoreMergesNewestFilePageWithDedupeThenExhausts() = runBlocking {
        val repository = FakeViewerRepository(
            memory = listOf(event(30), event(31), event(32)),
            file = listOf(event(11), event(12), event(30), event(31), event(32)),
        )
        val viewModel = newViewModel(repository)
        assertEquals("first screen is memory only", listOf(32L, 31L, 30L), content(viewModel.uiState.value).events.map { it.ts })

        viewModel.onLoadMore()

        val merged = content(viewModel.uiState.value)
        assertEquals("memory ∪ file page deduped, ts desc", listOf(32L, 31L, 30L, 12L, 11L), merged.events.map { it.ts })
        assertFalse(merged.loadingOlder)
        assertFalse(merged.endReached)
        assertEquals(listOf(0 to 200), repository.fileCalls)

        viewModel.onLoadMore()

        assertTrue("offset 0 consumed → history exhausted", content(viewModel.uiState.value).endReached)
        assertEquals("no further file reads once exhausted", 1, repository.fileCalls.size)
    }

    @Test
    fun loadMoreWalksOlderPagesWhenAPageIsFullyDuplicated() = runBlocking {
        val file = (0 until 450).map { event(it.toLong()) }
        val repository = FakeViewerRepository(
            memory = (250 until 450).map { event(it.toLong()) },
            file = file,
        )
        val viewModel = newViewModel(repository)
        assertEquals(200, content(viewModel.uiState.value).events.size)

        viewModel.onLoadMore()

        val firstMerge = content(viewModel.uiState.value)
        assertEquals(
            "the newest file page fully duplicates the ring; the walk continues to the next older page",
            400,
            firstMerge.events.size,
        )
        assertEquals(449L, firstMerge.events.first().ts)
        assertEquals(50L, firstMerge.events.last().ts)
        assertFalse(firstMerge.endReached)
        assertEquals(listOf(250 to 200, 50 to 200), repository.fileCalls)

        viewModel.onLoadMore()
        assertEquals("final partial page completes the history", 450, content(viewModel.uiState.value).events.size)
        assertFalse(content(viewModel.uiState.value).endReached)

        viewModel.onLoadMore()
        assertTrue(content(viewModel.uiState.value).endReached)
        assertEquals(
            "the clamped tail page reads only the unconsumed lines",
            listOf(250 to 200, 50 to 200, 0 to 50),
            repository.fileCalls,
        )
    }

    @Test
    fun loadMoreWithFullyOverlappingHistoryEndsImmediatelyWithoutGrowth() = runBlocking {
        val file = (0 until 5).map { event(it.toLong()) }
        val repository = FakeViewerRepository(
            memory = file,
            file = file,
        )
        val viewModel = newViewModel(repository)

        viewModel.onLoadMore()

        val state = content(viewModel.uiState.value)
        assertEquals("pure duplicates add nothing", 5, state.events.size)
        assertTrue("the walk reached offset 0 and exhausted", state.endReached)
        assertFalse(state.loadingOlder)
    }

    @Test
    fun emptyMemoryWithFileHistoryLoadsOnDemand() = runBlocking {
        val repository = FakeViewerRepository(
            memory = emptyList(),
            file = listOf(event(10), event(11)),
        )
        val viewModel = newViewModel(repository)
        assertTrue(content(viewModel.uiState.value).events.isEmpty())
        assertFalse(content(viewModel.uiState.value).endReached)

        viewModel.onLoadMore()

        assertEquals(listOf(11L, 10L), content(viewModel.uiState.value).events.map { it.ts })
    }

    @Test
    fun noSourceAtAllEndsReachableImmediately() = runBlocking {
        val repository = FakeViewerRepository()
        val viewModel = newViewModel(repository)

        val state = content(viewModel.uiState.value)

        assertTrue(state.endReached)
        assertTrue(state.events.isEmpty())

        viewModel.onLoadMore()
        assertTrue("guard: nothing further to load", repository.fileCalls.isEmpty())
    }

    @Test
    fun loadMoreExposesLoadingOlderWhileReadingAndResetsAfterCompletion() = runBlocking {
        val repository = FakeViewerRepository(
            memory = listOf(event(30)),
            file = listOf(event(11), event(30)),
        )
        val viewModel = newViewModel(repository)
        val loadingOlderDuringReads = mutableListOf<Boolean>()
        repository.onDiskRead = {
            loadingOlderDuringReads.add(content(viewModel.uiState.value).loadingOlder)
        }

        viewModel.onLoadMore()

        assertEquals(
            "every history read happens while loadingOlder is observably true",
            listOf(true, true),
            loadingOlderDuringReads,
        )
        val state = content(viewModel.uiState.value)
        assertFalse("the flag clears once the page is merged", state.loadingOlder)
        assertEquals(listOf(30L, 11L), state.events.map { it.ts })
    }

    @Test
    fun loadMoreIsIgnoredWhileAnOlderPageLoadIsInFlight() = runBlocking {
        val repository = FakeViewerRepository(
            memory = listOf(event(30)),
            file = listOf(event(11), event(30)),
        )
        val viewModel = newViewModel(repository)
        val baselineReads = repository.diskReads
        var reentrantTriggers = 0
        repository.onDiskRead = {
            reentrantTriggers++
            viewModel.onLoadMore()
        }

        viewModel.onLoadMore()

        assertEquals(2, reentrantTriggers)
        assertEquals("guarded re-entrant triggers perform no extra disk reads", baselineReads + 2, repository.diskReads)
        assertFalse(content(viewModel.uiState.value).loadingOlder)
    }

    @Test
    fun loadMoreResetsLoadingOlderWhenAFileReadFailsAndRetriesSucceed() = runBlocking {
        val repository = FakeViewerRepository(
            memory = listOf(event(30)),
            file = listOf(event(11), event(30)),
        )
        val viewModel = newViewModel(repository)

        repository.failNextFileRead = true
        viewModel.onLoadMore()

        val failed = content(viewModel.uiState.value)
        assertFalse("a failed read still clears the loading flag", failed.loadingOlder)
        assertFalse(failed.endReached)
        assertEquals("nothing merged from the failed read", listOf(30L), failed.events.map { it.ts })

        viewModel.onLoadMore()

        assertEquals(
            "retry resumes from the uncommitted offset",
            listOf(30L, 11L),
            content(viewModel.uiState.value).events.map { it.ts },
        )
    }

    @Test
    fun sameMillisecondDuplicatesInAFilePageAreAllDisplayed() = runBlocking {
        val duplicate = event(10)
        val repository = FakeViewerRepository(
            memory = listOf(event(50)),
            file = listOf(duplicate, duplicate),
        )
        val viewModel = newViewModel(repository)

        viewModel.onLoadMore()

        val state = content(viewModel.uiState.value)
        assertEquals(
            "same-millisecond repeated emissions in one file page are all shown",
            listOf(50L, 10L, 10L),
            state.events.map { it.ts },
        )
    }

    @Test
    fun refreshPullsNewMemoryEventsAndKeepsLoadedHistory() = runBlocking {
        val repository = FakeViewerRepository(
            memory = listOf(event(30)),
            file = listOf(event(11), event(30)),
        )
        val viewModel = newViewModel(repository)
        viewModel.onLoadMore()
        assertEquals(listOf(30L, 11L), content(viewModel.uiState.value).events.map { it.ts })

        repository.memory = listOf(event(30), event(31))
        viewModel.onRefresh()

        assertEquals(
            "newest event on top, previously loaded file page kept",
            listOf(31L, 30L, 11L),
            content(viewModel.uiState.value).events.map { it.ts },
        )
    }

    @Test
    fun clearFiltersResetsDimensionsButKeepsConciseAudienceMode() = runBlocking {
        val repository = FakeViewerRepository(
            memory = listOf(
                event(100, audience = Audience.HUMAN, level = Level.ERROR),
                event(200, audience = Audience.BOTH),
            ),
        )
        val viewModel = newViewModel(repository)
        viewModel.onKeywordChanged("e100")
        viewModel.onLevelToggled(Level.ERROR)

        viewModel.onFiltersCleared()

        val state = content(viewModel.uiState.value)
        assertNull(state.filter.keyword)
        assertEquals(emptySet<Level>(), state.filter.levels)
        assertTrue("concise default still restricts audiences after clear", state.conciseView)
        assertEquals(setOf(Audience.HUMAN, Audience.BOTH), state.filter.audiences)
        assertEquals(2, state.events.size)
    }
}
