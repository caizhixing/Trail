package io.github.caizhixing.trail.debug.viewer

import io.github.caizhixing.trail.model.Audience
import io.github.caizhixing.trail.model.Level
import io.github.caizhixing.trail.model.LogEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Behavior 5.3: memory ring ∪ file page paging math as pure domain
 * functions — ts-descending unification, dedupe, page-size 200 boundaries
 * (empty set, below one page, exactly one page, above).
 */
class ViewerPagingTest {

    private fun event(ts: Long, name: String = "e$ts"): LogEvent = LogEvent(
        ts = ts,
        traceId = null,
        tag = "widget",
        name = name,
        audience = Audience.BOTH,
        level = Level.INFO,
        attrs = emptyMap(),
        tid = null,
    )

    @Test
    fun pageSizeIsFrozenAt200() {
        assertEquals(200, ViewerPaging.PAGE_SIZE)
    }

    @Test
    fun initialPageFlipsOldestFirstMemoryToNewestFirst() {
        val memory = listOf(event(100), event(300), event(200))

        val page = ViewerPaging.initialPage(memory)

        assertEquals(listOf(300L, 200L, 100L), page.map { it.ts })
    }

    @Test
    fun initialPageHandlesEmptyMemory() {
        assertEquals(emptyList<LogEvent>(), ViewerPaging.initialPage(emptyList()))
    }

    @Test
    fun mergeDeduplicatesMemoryAndFileOverlapAndSortsDescending() {
        val memory = listOf(event(30), event(31))
        val filePage = listOf(event(11), event(12), event(30), event(31))

        val merged = ViewerPaging.mergeOlderPage(loaded = memory, filePage = filePage)

        assertEquals(listOf(31L, 30L, 12L, 11L), merged.map { it.ts })
        assertEquals("duplicates collapse", 4, merged.size)
    }

    @Test
    fun mergeHandlesEmptySides() {
        assertEquals(emptyList<LogEvent>(), ViewerPaging.mergeOlderPage(emptyList(), emptyList()))
        val onlyMemory = listOf(event(5))
        assertEquals(listOf(5L), ViewerPaging.mergeOlderPage(onlyMemory, emptyList()).map { it.ts })
        assertEquals(listOf(5L), ViewerPaging.mergeOlderPage(emptyList(), onlyMemory).map { it.ts })
    }

    @Test
    fun mergeKeepsEqualTimestampInsertionOrder() {
        val memory = listOf(event(10, "mem-a"), event(10, "mem-b"))

        val merged = ViewerPaging.mergeOlderPage(loaded = memory, filePage = emptyList())

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
}
