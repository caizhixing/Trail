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

    /**
     * Cross-source identity (main-agent ruling ④): one emission lives both
     * in the ring and in the file history, so the overlap is collapsed by
     * these six fields. Same-millisecond duplicate emissions are distinct
     * history lines and are never collapsed against each other.
     */
    private data class EventIdentity(
        val ts: Long,
        val tag: String,
        val name: String,
        val attrs: Map<String, String>,
        val tid: String?,
        val traceId: String?,
    )

    private fun LogEvent.identity(): EventIdentity =
        EventIdentity(ts = ts, tag = tag, name = name, attrs = attrs, tid = tid, traceId = traceId)

    /**
     * Memory ring ∪ accumulated file pages as one `ts`-descending list.
     * Every ring event is kept; a file event is dropped only when the same
     * emission is already covered by the ring (cross-source dedupe by
     * [EventIdentity]). The file side is never deduped against itself —
     * same-millisecond repeated emissions inside the history are all
     * retained. Equal-timestamp events keep insertion order via the
     * stable sort.
     */
    fun mergeSources(memory: List<LogEvent>, file: List<LogEvent>): List<LogEvent> {
        if (file.isEmpty()) return memory.sortedByDescending(LogEvent::ts)
        val ringIdentities = memory.mapTo(HashSet()) { it.identity() }
        return (memory + file.filter { it.identity() !in ringIdentities })
            .sortedByDescending(LogEvent::ts)
    }

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

    /**
     * Read length for the page starting at [offset]: a full page, or less
     * when clamping to 0 would otherwise re-read lines the previous page
     * already covered. The walk must never load the same history line twice
     * — ruling ④ keeps file-internal duplicates, so a re-read line would be
     * displayed as if it were a duplicate same-millisecond emission. Must be
     * computed against the offset valid *before* the walk commits [offset].
     */
    fun pageLimitFor(offset: Int, currentOffset: Int?): Int =
        if (currentOffset == null) PAGE_SIZE else minOf(PAGE_SIZE, currentOffset - offset)
}
