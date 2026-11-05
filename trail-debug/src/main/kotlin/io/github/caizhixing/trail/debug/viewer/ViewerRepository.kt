package io.github.caizhixing.trail.debug.viewer

import io.github.caizhixing.trail.debug.engine.TrailSnapshotStore
import io.github.caizhixing.trail.model.LogEvent

/**
 * Viewer-side data access seam: the ViewModel never touches the engine
 * snapshot store directly — tests inject fakes, production binds the live
 * engine store (P-2: memory ring ∪ file pages).
 */
internal interface ViewerRepository {

    /** Live ring contents, oldest-first (may be empty before init). */
    fun memoryEvents(): List<LogEvent>

    /** Up to [limit] decoded history events starting at line [offset]. */
    fun fileEvents(offset: Int, limit: Int): List<LogEvent>

    /** Total retained history lines. */
    fun fileEventCount(): Int
}

/**
 * Production repository over the engine snapshot surface. A null store
 * (Trail not initialized) behaves as an empty source, never crashes the
 * viewer.
 */
internal class SnapshotViewerRepository(
    private val store: TrailSnapshotStore?,
) : ViewerRepository {

    override fun memoryEvents(): List<LogEvent> = store?.memoryEvents().orEmpty()

    override fun fileEvents(offset: Int, limit: Int): List<LogEvent> =
        store?.fileEvents(offset, limit).orEmpty()

    override fun fileEventCount(): Int = store?.fileEventCount() ?: 0
}
