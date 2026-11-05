package io.github.caizhixing.trail.debug.viewer

import io.github.caizhixing.trail.model.LogEvent

/**
 * Pure merge/paging math for the memory-ring ∪ file-page data source
 * (behavior 5.3, P-2). The first screen comes from the in-memory ring; as
 * the user scrolls towards older events, file pages (newest-first walk of
 * the on-disk history) are merged in. Everything is unified as one
 * timestamp-descending list.
 */
object ViewerPaging {

    const val PAGE_SIZE: Int = 200

    /** First screen: the memory ring (oldest-first) flipped to newest-first. */
    fun initialPage(memory: List<LogEvent>): List<LogEvent> =
        memory.sortedByDescending(LogEvent::ts)

    /**
     * Union of already-loaded events and a newly loaded file page, deduped by
     * event equality (memory and file overlap on persisted events) and kept
     * in `ts`-descending order. Equal-timestamp events keep insertion order
     * via the stable sort.
     */
    fun mergeOlderPage(loaded: List<LogEvent>, filePage: List<LogEvent>): List<LogEvent> =
        (loaded + filePage).distinct().sortedByDescending(LogEvent::ts)

    /**
     * Next history offset for the newest-first file walk, or `null` when the
     * history is exhausted / empty. [fileEvents] reads forward from `offset`
     * (line 0 = oldest), so paging older means walking offsets downwards:
     * start at `fileCount - PAGE_SIZE` (clamped to 0) and step by [PAGE_SIZE]
     * until offset 0 has been consumed.
     */
    fun nextFileOffset(currentOffset: Int?, fileCount: Int): Int? = when {
        currentOffset == null -> if (fileCount <= 0) null else maxOf(0, fileCount - PAGE_SIZE)
        currentOffset <= 0 -> null
        else -> maxOf(0, currentOffset - PAGE_SIZE)
    }
}
