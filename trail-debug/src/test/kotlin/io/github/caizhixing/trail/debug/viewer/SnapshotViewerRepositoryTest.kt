package io.github.caizhixing.trail.debug.viewer

import io.github.caizhixing.trail.debug.engine.TrailSnapshotStore
import io.github.caizhixing.trail.model.Audience
import io.github.caizhixing.trail.model.Level
import io.github.caizhixing.trail.model.LogEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The production repository wrapper delegates to the engine snapshot store
 * (P-2) and tolerates a null store (Trail not initialized) as an empty
 * source instead of crashing the viewer.
 */
class SnapshotViewerRepositoryTest {

    private fun event(ts: Long): LogEvent = LogEvent(
        ts = ts,
        traceId = null,
        tag = "widget",
        name = "e$ts",
        audience = Audience.BOTH,
        level = Level.INFO,
        attrs = emptyMap(),
        tid = null,
    )

    private class RecordingStore(
        val memory: List<LogEvent>,
        val file: List<LogEvent>,
    ) : TrailSnapshotStore {
        val fileCalls = mutableListOf<Pair<Int, Int>>()

        override fun memoryEvents(): List<LogEvent> = memory

        override fun fileEvents(offset: Int, limit: Int): List<LogEvent> {
            fileCalls.add(offset to limit)
            return file.drop(offset).take(limit)
        }

        override fun fileEventCount(): Int = file.size

        override fun fileCount(): Int = 1
    }

    @Test
    fun nullStoreBehavesAsEmptySource() {
        val repository = SnapshotViewerRepository(store = null)

        assertTrue(repository.memoryEvents().isEmpty())
        assertTrue(repository.fileEvents(0, 200).isEmpty())
        assertEquals(0, repository.fileEventCount())
    }

    @Test
    fun delegatesMemoryAndFileReadsToTheStore() {
        val store = RecordingStore(
            memory = listOf(event(20), event(21)),
            file = listOf(event(10), event(11), event(20), event(21)),
        )
        val repository = SnapshotViewerRepository(store = store)

        assertEquals(listOf(20L, 21L), repository.memoryEvents().map { it.ts })
        assertEquals(listOf(11L, 20L), repository.fileEvents(1, 2).map { it.ts })
        assertEquals(listOf(1 to 2), store.fileCalls)
        assertEquals(4, repository.fileEventCount())
    }
}
