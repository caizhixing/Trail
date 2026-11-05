package io.github.caizhixing.trail.debug.engine

import io.github.caizhixing.trail.model.Audience
import io.github.caizhixing.trail.model.Level
import io.github.caizhixing.trail.model.LogEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.Writer

/** Behavior 3.8: rolling files, pruning, resume and paged reads (real temp dir). */
class JsonlFileStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun appendingExactlyToTheLimitDoesNotRoll() {
        val line = JsonlEncoder.encodeEvent(eventOf("trail_test_a"))
        val limit = line.toByteArray(Charsets.UTF_8).size + 1L
        val store = newStore(maxFileBytes = limit, maxFiles = 3)

        store.append(line)
        store.close()

        assertEquals(1, store.fileCount())
        assertEquals(listOf(line), linesOf(store))
    }

    @Test
    fun appendingBeyondTheLimitRollsToANewFile() {
        val line = JsonlEncoder.encodeEvent(eventOf("trail_test_a"))
        val limit = line.toByteArray(Charsets.UTF_8).size + 1L
        val store = newStore(maxFileBytes = limit, maxFiles = 3)

        store.append(line)
        store.append(JsonlEncoder.encodeEvent(eventOf("trail_test_b")))
        store.close()

        assertEquals(2, store.fileCount())
        assertEquals(
            listOf("trail_test_a", "trail_test_b"),
            store.readEvents(0, 10).map { it.name },
        )
    }

    @Test
    fun exceedingMaxFilesPrunesTheOldest() {
        val store = newStore(maxFileBytes = 64L, maxFiles = 2)

        for (index in 1..6) {
            store.append(JsonlEncoder.encodeEvent(eventOf("trail_test_$index")))
        }
        store.close()

        assertEquals(2, store.fileCount())
        val names = store.readEvents(0, 100).map { it.name }
        assertEquals(listOf("trail_test_5", "trail_test_6"), names)
        assertTrue(
            store.filesOldestFirst().none { file ->
                file.readText().contains("trail_test_1") || file.readText().contains("trail_test_2")
            },
        )
    }

    @Test
    fun newStoreResumesAfterTheHighestExistingIndex() {
        val existing = File(tmp.newFolder("trail-data"), "trail_0003.jsonl")
        existing.writeText(JsonlEncoder.encodeEvent(eventOf("trail_test_old")) + "\n")
        val store = JsonlFileStore(existing.parentFile, maxFileBytes = 1_000, maxFiles = 3)

        store.append(JsonlEncoder.encodeEvent(eventOf("trail_test_new")))
        store.close()

        assertEquals(listOf("trail_0003.jsonl", "trail_0004.jsonl"), store.filesOldestFirst().map { it.name })
    }

    @Test
    fun foreignFilesAreIgnored() {
        val dir = tmp.newFolder("trail-data")
        File(dir, "notes.txt").writeText("keep me")
        val store = JsonlFileStore(dir, maxFileBytes = 1_000, maxFiles = 3)

        store.append(JsonlEncoder.encodeEvent(eventOf("trail_test_a")))
        store.close()

        assertEquals(1, store.fileCount())
        assertTrue(File(dir, "notes.txt").exists())
    }

    @Test
    fun readEventsPagesAcrossFileBoundaries() {
        val store = newStore(maxFileBytes = smallRollLimit(), maxFiles = 5)
        for (index in 1..5) {
            store.append(JsonlEncoder.encodeEvent(eventOf("trail_test_$index")))
        }
        store.close()

        assertEquals(5, store.eventCount())
        assertEquals(
            listOf("trail_test_1", "trail_test_2", "trail_test_3", "trail_test_4"),
            store.readEvents(0, 4).map { it.name },
        )
        assertEquals(listOf("trail_test_3"), store.readEvents(2, 1).map { it.name })
        assertEquals(listOf("trail_test_4", "trail_test_5"), store.readEvents(3, 10).map { it.name })
    }

    @Test
    fun readEventsRejectsBadRangesWithEmptyResults() {
        val store = newStore(maxFileBytes = 1_000, maxFiles = 3)
        store.append(JsonlEncoder.encodeEvent(eventOf("trail_test_a")))
        store.close()

        assertEquals(emptyList<LogEvent>(), store.readEvents(0, 0))
        assertEquals(emptyList<LogEvent>(), store.readEvents(0, -1))
        assertEquals(emptyList<LogEvent>(), store.readEvents(-1, 5))
        assertEquals(emptyList<LogEvent>(), store.readEvents(99, 5))
    }

    @Test
    fun corruptLinesAreSkippedButKeepTheirPageSlot() {
        val dir = tmp.newFolder("trail-data")
        val good = JsonlEncoder.encodeEvent(eventOf("trail_test_good"))
        File(dir, "trail_0000.jsonl").writeText("{\"broken\":\n" + good + "\n")
        val store = JsonlFileStore(dir, maxFileBytes = 10_000, maxFiles = 3)

        assertEquals(2, store.eventCount())
        assertEquals(listOf("trail_test_good"), store.readEvents(0, 10).map { it.name })
        assertEquals(emptyList<LogEvent>(), store.readEvents(0, 1))
        assertEquals(listOf("trail_test_good"), store.readEvents(1, 1).map { it.name })
    }

    @Test
    fun sinkFailureWarnsAndAppendStaysNonFatal() {
        val warnings = mutableListOf<String>()
        val store = JsonlFileStore(
            dir = tmp.newFolder("trail-data"),
            maxFileBytes = 1_000,
            maxFiles = 3,
            sinkFactory = { throw IOException("disk on fire") },
            ioWarner = { warnings.add(it) },
        )

        store.append(JsonlEncoder.encodeEvent(eventOf("trail_test_a")))
        store.append(JsonlEncoder.encodeEvent(eventOf("trail_test_b")))
        store.close()

        assertEquals(0, store.fileCount())
        assertEquals(2, warnings.size)
        assertTrue(warnings.all { it.contains("open failed") })
    }

    @Test
    fun unusableDirWarnsAndDrops() {
        val blocker = tmp.newFile("blocker")
        blocker.writeText("this is a file, not a directory")
        val warnings = mutableListOf<String>()
        val store = JsonlFileStore(
            dir = blocker,
            maxFileBytes = 1_000,
            maxFiles = 3,
            ioWarner = { warnings.add(it) },
        )

        store.append(JsonlEncoder.encodeEvent(eventOf("trail_test_a")))
        store.close()

        assertEquals(0, store.fileCount())
        assertEquals(1, warnings.size)
        assertTrue(warnings.single().contains("dir unavailable"))
    }

    @Test
    fun nonPositiveLimitsDropEverythingWithoutTouchingDisk() {
        val store = newStore(maxFileBytes = 0L, maxFiles = 0)

        store.append(JsonlEncoder.encodeEvent(eventOf("trail_test_a")))
        store.close()

        assertEquals(0, store.fileCount())
        assertEquals(0, store.eventCount())
    }

    @Test
    fun customSinkReceivesEveryLine() {
        val seen = mutableListOf<String>()
        val store = JsonlFileStore(
            dir = tmp.newFolder("trail-data"),
            maxFileBytes = 10_000,
            maxFiles = 3,
            sinkFactory = { file ->
                object : Writer() {
                    override fun write(text: String) {
                        seen.add(text)
                    }

                    override fun write(cbuf: CharArray, off: Int, len: Int) {
                        seen.add(String(cbuf, off, len))
                    }

                    override fun flush() {}

                    override fun close() {}
                }
            },
        )

        val line = JsonlEncoder.encodeEvent(eventOf("trail_test_a"))
        store.append(line)
        store.close()

        assertEquals(listOf(line, "\n"), seen)
    }

    private fun newStore(maxFileBytes: Long, maxFiles: Int): JsonlFileStore =
        JsonlFileStore(
            dir = tmp.newFolder("trail-data"),
            maxFileBytes = maxFileBytes,
            maxFiles = maxFiles,
        )

    private fun smallRollLimit(): Long =
        JsonlEncoder.encodeEvent(eventOf("trail_test_x")).toByteArray(Charsets.UTF_8).size + 1L

    private fun linesOf(store: JsonlFileStore): List<String> =
        store.filesOldestFirst().flatMap { file -> file.readLines() }

    private fun eventOf(name: String): LogEvent =
        LogEvent(
            ts = 1_000L,
            traceId = null,
            tag = "widget",
            name = name,
            audience = Audience.AI,
            level = Level.INFO,
            attrs = emptyMap(),
            tid = null,
        )
}
