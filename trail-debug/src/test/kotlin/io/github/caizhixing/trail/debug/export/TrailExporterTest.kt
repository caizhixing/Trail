package io.github.caizhixing.trail.debug.export

import io.github.caizhixing.trail.debug.engine.ExportMeta
import io.github.caizhixing.trail.debug.engine.JsonlDecoder
import io.github.caizhixing.trail.debug.engine.JsonlEncoder
import io.github.caizhixing.trail.debug.engine.TrailSnapshotStore
import io.github.caizhixing.trail.debug.viewer.EventFilter
import io.github.caizhixing.trail.model.Audience
import io.github.caizhixing.trail.model.Level
import io.github.caizhixing.trail.model.LogEvent
import java.io.File
import java.io.Writer
import java.nio.file.Files
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviors 6.2 / 6.3 / 6.6: the export file is the meta line followed by the
 * selected events in ascending `ts` order (empty selection = meta only), the
 * write is streamed with exactly one write call per line, and the exported
 * values are the stored (already sanitized and truncated) ones re-encoded by
 * #3's JsonlEncoder — never re-sanitized, never bypassed.
 */
class TrailExporterTest {

    private class FakeSnapshotStore(
        var memory: List<LogEvent> = emptyList(),
        var file: List<LogEvent> = emptyList(),
        var failReads: Boolean = false,
    ) : TrailSnapshotStore {

        override fun memoryEvents(): List<LogEvent> {
            if (failReads) {
                throw IllegalStateException("simulated store blow-up")
            }
            return memory
        }

        override fun fileEvents(offset: Int, limit: Int): List<LogEvent> =
            file.drop(offset).take(limit)

        override fun fileEventCount(): Int = file.size

        override fun fileCount(): Int = 1
    }

    /** Hand-written fake (no mock libraries): counts write calls and keeps lines. */
    private class CountingWriter : Writer() {
        val lines = mutableListOf<String>()

        override fun write(str: String) {
            lines.add(str)
        }

        override fun write(cbuf: CharArray, off: Int, len: Int) {
            lines.add(String(cbuf, off, len))
        }

        override fun flush() = Unit

        override fun close() = Unit
    }

    private val tempDir: File = Files.createTempDirectory("trail_export_test").toFile()

    @After
    fun cleanUp() {
        tempDir.deleteRecursively()
    }

    private fun event(
        ts: Long,
        traceId: String? = null,
        tag: String = "widget",
        name: String = "e$ts",
        attrs: Map<String, String> = emptyMap(),
    ): LogEvent = LogEvent(
        ts = ts,
        traceId = traceId,
        tag = tag,
        name = name,
        audience = Audience.AI,
        level = Level.INFO,
        attrs = attrs,
        tid = "main",
    )

    private fun meta(filter: String = "trace=t_a") = ExportMeta(
        schema = 1,
        app = "com.example.app",
        version = "1.2.3",
        device = "Pixel 9",
        exportedAt = "2026-11-05T12:34:56.123Z",
        filter = filter,
    )

    private fun exporter(
        store: TrailSnapshotStore?,
        dir: File = tempDir,
    ): SnapshotTrailEventExporter = SnapshotTrailEventExporter(
        store = store,
        exportsDir = dir,
        appProvider = { "com.example.app" },
        versionProvider = { "1.2.3" },
        deviceProvider = { "Pixel 9" },
        clock = { CLOCK_MS },
        zone = ZoneId.of("UTC"),
    )

    @Test
    fun exportFileIsMetaLineThenEventsInAscendingTsOrder() {
        val store = FakeSnapshotStore(
            memory = listOf(event(30, traceId = "t_a"), event(10, traceId = "t_a"), event(20, traceId = "t_b")),
            file = listOf(event(5, traceId = "t_a")),
        )
        val payload = exporter(store).export(ExportScope.Trace("t_a")) as ResultState.Success

        val lines = payload.data.file.readLines()
        assertEquals("meta first, then the three selected events", 4, lines.size)
        assertEquals(JsonlEncoder.encodeMeta(meta()), lines[0])
        assertEquals(
            "selected events sorted ascending regardless of source order",
            listOf(5L, 10L, 30L),
            lines.drop(1).map { JsonlDecoder.decodeEvent(it)?.ts },
        )
        assertEquals(3, payload.data.eventCount)
    }

    @Test
    fun emptySelectionExportsExactlyOneMetaLine() {
        val store = FakeSnapshotStore(memory = listOf(event(10, traceId = "t_b")))
        val payload = exporter(store).export(ExportScope.Trace("t_missing")) as ResultState.Success

        val lines = payload.data.file.readLines()
        assertEquals("empty result: meta only", 1, lines.size)
        assertEquals(JsonlEncoder.encodeMeta(meta(filter = "trace=t_missing")), lines[0])
        assertEquals(0, payload.data.eventCount)
        assertNull("the meta line is not an event line", JsonlDecoder.decodeEvent(lines[0]))
    }

    @Test
    fun streamingWritesExactlyOneCallPerLine() {
        val events = (1..5).map { event(it.toLong() * 10, traceId = "t_a") }
        val store = FakeSnapshotStore(memory = events)
        val writer = CountingWriter()

        val count = exporter(store).writeExport(meta(), events, writer)

        assertEquals("events count returned", 5, count)
        assertEquals("behavior 6.3: write calls == events + 1 (meta)", 6, writer.lines.size)
        writer.lines.forEach { line ->
            assertTrue("one write per line, newline only as the terminator", line.endsWith("\n"))
            assertEquals("no embedded line breaks", 1, line.count { it == '\n' })
        }
        assertEquals(
            JsonlEncoder.encodeMeta(meta()),
            writer.lines.first().trimEnd('\n'),
        )
    }

    @Test
    fun exportedValuesAreTheStoredSanitizedTruncatedOnesVerbatim() {
        // What the pipeline stored: masked keys are already `***`, values are
        // already truncated to the cap; note the unmasked-looking key-less
        // content `password=hunter2` is data, not a second masking trigger.
        val longValue = "x".repeat(512)
        val stored = event(
            ts = 42,
            traceId = "t_a",
            attrs = mapOf(
                "token" to "***",
                "authorization" to "***",
                "note" to "password=hunter2",
                "body" to longValue,
            ),
        )
        val store = FakeSnapshotStore(memory = listOf(stored))
        val payload = exporter(store).export(ExportScope.Trace("t_a")) as ResultState.Success

        val line = payload.data.file.readLines()[1]
        assertEquals(
            "behavior 6.6: the line is byte-for-byte #3's encoding of the stored event",
            JsonlEncoder.encodeEvent(stored),
            line,
        )
        assertTrue(line.contains("\"token\":\"***\""))
        assertTrue(line.contains("password=hunter2"))
        assertTrue(line.contains(longValue))
    }

    @Test
    fun ringAndHistoryOverlapIsDedupedWhileHistoryDuplicatesSurvive() {
        val ringEvent = event(30, traceId = "t_a")
        val store = FakeSnapshotStore(
            memory = listOf(ringEvent),
            file = listOf(event(11, traceId = "t_a"), ringEvent, event(12, traceId = "t_a")),
        )
        val payload = exporter(store).export(ExportScope.Trace("t_a")) as ResultState.Success

        assertEquals(
            "one emission present in both sources is exported once",
            listOf(11L, 12L, 30L),
            payload.data.file.readLines().drop(1).map { JsonlDecoder.decodeEvent(it)?.ts },
        )

        val duplicated = event(10, traceId = "t_a")
        val sameMillisecondStore = FakeSnapshotStore(
            memory = emptyList(),
            file = listOf(duplicated, duplicated),
        )
        val duplicatePayload = exporter(sameMillisecondStore).export(ExportScope.Trace("t_a")) as ResultState.Success
        assertEquals(
            "same-millisecond repeated emissions are distinct history lines: both kept",
            2,
            duplicatePayload.data.eventCount,
        )
    }

    @Test
    fun nullStoreBehavesAsAnEmptySource() {
        val payload = exporter(store = null).export(ExportScope.RecentHours(1)) as ResultState.Success

        assertEquals(1, payload.data.file.readLines().size)
        assertEquals(0, payload.data.eventCount)
    }

    @Test
    fun fullExportCarriesTheFrozenMetaKeysAndInjectableClock() {
        val store = FakeSnapshotStore(memory = listOf(event(10, traceId = "t_a")))
        val payload = exporter(store).export(ExportScope.Trace("t_a")) as ResultState.Success
        val metaLine = payload.data.file.readLines().first()

        assertTrue("meta starts with the schema key", metaLine.startsWith("{\"schema\":1,\"app\":"))
        assertTrue(metaLine.contains("\"version\":\"1.2.3\""))
        assertTrue(metaLine.contains("\"device\":\"Pixel 9\""))
        assertTrue(metaLine.contains("\"filter\":\"trace=t_a\""))
        val exportedAt = metaLine.substringAfter("\"exported_at\":\"").substringBefore("\"")
        assertEquals(
            "ISO-8601 with timezone derived from the injected clock",
            CLOCK_MS,
            OffsetDateTime.parse(exportedAt).toInstant().toEpochMilli(),
        )
        assertEquals(
            "payload file name follows the frozen policy",
            "trail_export_20261105_123456.jsonl",
            payload.data.file.name,
        )
    }

    @Test
    fun failingMetaProvidersCollapseToUnknownWithoutFailingTheExport() {
        val exporter = SnapshotTrailEventExporter(
            store = FakeSnapshotStore(memory = listOf(event(10, traceId = "t_a"))),
            exportsDir = tempDir,
            appProvider = { throw IllegalStateException("no package") },
            versionProvider = { "  " },
            deviceProvider = { throw IllegalStateException("no device") },
            clock = { CLOCK_MS },
            zone = ZoneId.of("UTC"),
        )

        val payload = exporter.export(ExportScope.Trace("t_a")) as ResultState.Success
        val metaLine = payload.data.file.readLines().first()

        assertTrue(metaLine.contains("\"app\":\"unknown\""))
        assertTrue(metaLine.contains("\"version\":\"unknown\""))
        assertTrue(metaLine.contains("\"device\":\"unknown\""))
    }

    @Test
    fun unwritableTargetDirectoryYieldsAnErrorStateNotACrash() {
        val notADir = File(tempDir, "blocked")
        assertTrue(notADir.createNewFile())
        val exporter = exporter(store = FakeSnapshotStore(memory = listOf(event(10))), dir = notADir)

        val result = exporter.export(ExportScope.RecentHours(1))

        assertTrue(result is ResultState.Error)
        assertTrue((result as ResultState.Error).message.startsWith("export failed:"))
    }

    @Test
    fun aFailingSnapshotStoreYieldsAnErrorStateNotACrash() {
        val exporter = exporter(store = FakeSnapshotStore(failReads = true))

        val result = exporter.export(ExportScope.CurrentFilter(EventFilter()))

        assertTrue(result is ResultState.Error)
        assertTrue((result as ResultState.Error).message.startsWith("export failed:"))
    }

    @Test
    fun recentHoursWindowUsesTheInjectedClock() {
        val before = Instant.ofEpochMilli(CLOCK_MS - 3_600_000L - 1).toEpochMilli()
        val store = FakeSnapshotStore(
            memory = listOf(event(before), event(CLOCK_MS - 3_600_000L), event(CLOCK_MS), event(CLOCK_MS + 1)),
        )

        val payload = exporter(store).export(ExportScope.RecentHours(1)) as ResultState.Success

        assertEquals(
            "window [now-1h, now] against the injected clock; future excluded",
            listOf(CLOCK_MS - 3_600_000L, CLOCK_MS),
            payload.data.file.readLines().drop(1).map { JsonlDecoder.decodeEvent(it)?.ts },
        )
    }

    private companion object {
        const val CLOCK_MS = 1_793_882_096_123L // 2026-11-05T12:34:56.123Z
    }
}
