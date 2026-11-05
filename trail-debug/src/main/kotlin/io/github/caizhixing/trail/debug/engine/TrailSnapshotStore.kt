package io.github.caizhixing.trail.debug.engine

import io.github.caizhixing.trail.model.LogEvent

/**
 * Read-only snapshot surface for the in-app viewer (issue #5). The memory ring
 * is newest-last; file pages walk the on-disk history oldest-first by line
 * order, so `memoryEvents() ∪ fileEvents(...)` covers recent + historical data
 * (DECISIONS.md P-2). Implementations must tolerate concurrent appends.
 */
internal interface TrailSnapshotStore {

    /** Live ring contents, oldest-first. */
    fun memoryEvents(): List<LogEvent>

    /**
     * Up to [limit] decoded events starting at history line [offset]
     * (line 0 = oldest retained line). Corrupt lines are skipped.
     */
    fun fileEvents(offset: Int, limit: Int): List<LogEvent>

    /** Total retained history lines (for "has more pages" checks). */
    fun fileEventCount(): Int

    /** Number of managed jsonl files. */
    fun fileCount(): Int
}
