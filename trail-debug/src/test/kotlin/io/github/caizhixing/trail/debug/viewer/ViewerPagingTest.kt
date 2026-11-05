package io.github.caizhixing.trail.debug.viewer

import io.github.caizhixing.trail.model.Audience
import io.github.caizhixing.trail.model.Level
import io.github.caizhixing.trail.model.LogEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Behavior 5.3: memory ring ∪ file page paging math as pure domain
 * functions — ts-descending unification, source-aware dedupe (ruling ④:
 * cross-source overlap collapses by identity, file-internal duplicates
 * stay), page-size 200 boundaries (empty set, below one page, exactly one
 * page, above).
 */
class ViewerPagingTest {

    private fun event(
        ts: Long,
        name: String = "e$ts",
        audience: Audience = Audience.BOTH,
        level: Level = Level.INFO,
    ): LogEvent = LogEvent(
        ts = ts,
        traceId = null,
        tag = "widget",
        name = name,
        audience = audience,
        level = level,
        attrs = emptyMap(),
        tid = null,
    )

    @Test
    fun pageSizeIsFrozenAt200() {
        assertEquals(200, ViewerPaging.PAGE_SIZE)
    }

    @Test
    fun mergeSortsTheMemorySideNewestFirstWhenNoFilePagesAreLoaded() {
        val memory = listOf(event(100), event(300), event(200))

        val merged = ViewerPaging.mergeSources(memory = memory, file = emptyList())

        assertEquals(listOf(300L, 200L, 100L), merged.map { it.ts })
    }

    @Test
    fun mergeHandlesEmptySides() {
        assertEquals(
            emptyList<LogEvent>(),
            ViewerPaging.mergeSources(memory = emptyList(), file = emptyList()),
        )
        val onlyMemory = listOf(event(5))
        assertEquals(listOf(5L), ViewerPaging.mergeSources(memory = onlyMemory, file = emptyList()).map { it.ts })
        assertEquals(listOf(5L), ViewerPaging.mergeSources(memory = emptyList(), file = onlyMemory).map { it.ts })
    }

    @Test
    fun mergeDeduplicatesMemoryAndFileOverlapAndSortsDescending() {
        val memory = listOf(event(30), event(31))
        val filePage = listOf(event(11), event(12), event(30), event(31))

        val merged = ViewerPaging.mergeSources(memory = memory, file = filePage)

        assertEquals(listOf(31L, 30L, 12L, 11L), merged.map { it.ts })
        assertEquals("cross-source duplicates collapse", 4, merged.size)
    }

    @Test
    fun mergeDeduplicatesCrossSourceByIdentityEvenWhenAudienceOrLevelDiffer() {
        val ringCopy = event(10, audience = Audience.HUMAN, level = Level.INFO)
        val persistedCopy = event(10, audience = Audience.AI, level = Level.ERROR)

        val merged = ViewerPaging.mergeSources(memory = listOf(ringCopy), file = listOf(persistedCopy))

        assertEquals(
            "identity = (ts, tag, name, attrs, tid, traceId); the ring copy wins",
            listOf(ringCopy),
            merged,
        )
    }

    @Test
    fun mergeKeepsSameMillisecondDuplicatesInsideTheFileSide() {
        val duplicate = event(10)
        val otherPageLine = event(11)

        val merged = ViewerPaging.mergeSources(
            memory = listOf(event(50)),
            file = listOf(otherPageLine, duplicate, duplicate),
        )

        assertEquals(listOf(50L, 11L, 10L, 10L), merged.map { it.ts })
        assertEquals(
            "same-millisecond repeated emissions in the history are all retained",
            2,
            merged.count { it.ts == 10L },
        )
    }

    @Test
    fun mergeKeepsEqualTimestampInsertionOrder() {
        val memory = listOf(event(10, "mem-a"), event(10, "mem-b"))

        val merged = ViewerPaging.mergeSources(memory = memory, file = emptyList())

        assertEquals(listOf("mem-a", "mem-b"), merged.map { it.name })
    }

    @Test
    fun nextFileOffsetEmptyHistoryIsExhaustedImmediately() {
        assertNull(ViewerPaging.nextFileOffset(currentOffset = null, fileCount = 0))
        assertNull(ViewerPaging.nextFileOffset(currentOffset = null, fileCount = -1))
    }

    @Test
    fun nextFileOffsetBelowOnePageSizeClampsToZero() {
        assertEquals(0, ViewerPaging.nextFileOffset(currentOffset = null, fileCount = 100))
    }

    @Test
    fun nextFileOffsetExactlyOnePageStartsAtZero() {
        assertEquals(0, ViewerPaging.nextFileOffset(currentOffset = null, fileCount = 200))
    }

    @Test
    fun nextFileOffsetJustAboveOnePageStartsAtOne() {
        assertEquals(1, ViewerPaging.nextFileOffset(currentOffset = null, fileCount = 201))
    }

    @Test
    fun nextFileOffsetWalksDownToZeroThenExhausts() {
        // 450 lines: 250 -> 50 -> 0 -> exhausted
        assertEquals(250, ViewerPaging.nextFileOffset(currentOffset = null, fileCount = 450))
        assertEquals(50, ViewerPaging.nextFileOffset(currentOffset = 250, fileCount = 450))
        assertEquals(0, ViewerPaging.nextFileOffset(currentOffset = 50, fileCount = 450))
        assertNull(ViewerPaging.nextFileOffset(currentOffset = 0, fileCount = 450))
    }

    @Test
    fun nextFileOffsetExactMultipleWalksWholePageSteps() {
        // 400 lines: 200 -> 0 -> exhausted
        assertEquals(200, ViewerPaging.nextFileOffset(currentOffset = null, fileCount = 400))
        assertEquals(0, ViewerPaging.nextFileOffset(currentOffset = 200, fileCount = 400))
        assertNull(ViewerPaging.nextFileOffset(currentOffset = 0, fileCount = 400))
    }

    @Test
    fun nextFileOffsetRemainderPageClampsInsteadOfSkipping() {
        assertEquals(0, ViewerPaging.nextFileOffset(currentOffset = 5, fileCount = 450))
    }

    @Test
    fun pageLimitForFirstPageIsAlwaysAFullPage() {
        assertEquals(200, ViewerPaging.pageLimitFor(offset = 250, currentOffset = null))
        assertEquals(200, ViewerPaging.pageLimitFor(offset = 0, currentOffset = null))
    }

    @Test
    fun pageLimitForUnclampedStepStaysAFullPage() {
        // 450 lines: 250 -> 50, full page apart
        assertEquals(200, ViewerPaging.pageLimitFor(offset = 50, currentOffset = 250))
    }

    @Test
    fun pageLimitForClampedTailReadsOnlyUnconsumedLines() {
        // Previous page covered [50, 250); the tail page reads [0, 50) so
        // no history line is ever loaded twice.
        assertEquals(50, ViewerPaging.pageLimitFor(offset = 0, currentOffset = 50))
        assertEquals(150, ViewerPaging.pageLimitFor(offset = 0, currentOffset = 150))
        assertEquals(200, ViewerPaging.pageLimitFor(offset = 0, currentOffset = 200))
    }
}
