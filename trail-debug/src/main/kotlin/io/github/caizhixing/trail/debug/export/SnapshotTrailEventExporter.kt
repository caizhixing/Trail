package io.github.caizhixing.trail.debug.export

import io.github.caizhixing.trail.debug.engine.ExportMeta
import io.github.caizhixing.trail.debug.engine.JsonlEncoder
import io.github.caizhixing.trail.debug.engine.TrailSnapshotStore
import io.github.caizhixing.trail.debug.viewer.ViewerPaging
import io.github.caizhixing.trail.model.LogEvent
import io.github.caizhixing.trail.model.TrailSchema
import java.io.File
import java.io.IOException
import java.io.Writer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Store-backed exporter (behaviors 6.2 / 6.3 / 6.6).
 *
 * The exported data is exactly what the capture pipeline already stored —
 * sanitized and truncated at write time (issue #3) — read through the same
 * snapshot surface as the viewer and re-encoded with #3's [JsonlEncoder]:
 * no second sanitization pass and no bypass around the stored values
 * (behavior 6.6).
 *
 * All injectable seams: snapshot store, target directory, clock, zone and the
 * meta providers, so the whole pipeline runs deterministically on the JVM.
 * [export] blocks; the caller (ViewerViewModel) dispatches it to IO.
 */
internal class SnapshotTrailEventExporter(
    private val store: TrailSnapshotStore?,
    private val exportsDir: File,
    private val appProvider: () -> String?,
    private val versionProvider: () -> String?,
    private val deviceProvider: () -> String?,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : TrailEventExporter {

    /**
     * Runs one export: collect + filter + sort, build the frozen six-key meta
     * line and stream the file line by line. A failure anywhere (store read,
     * provider blowup, IO) collapses into [ResultState.Error] instead of
     * crashing the viewer.
     */
    override fun export(scope: ExportScope): ResultState<ExportSharePayload> = try {
        val nowMs = clock()
        val events = collectEvents(scope, nowMs)
        val meta = ExportMeta(
            schema = TrailSchema.VERSION,
            app = providedOrUnknown(appProvider),
            version = providedOrUnknown(versionProvider),
            device = ExportMeta.deviceOrUnknown(deviceProvider),
            exportedAt = exportedAt(nowMs),
            filter = scope.summary(nowMs),
        )
        val file = File(exportsDir, ExportFileNamePolicy.fileName(nowMs, java.util.TimeZone.getTimeZone(zone.id)))
        val eventCount = writeToFile(meta, events, file)
        ResultState.Success(ExportSharePayload(file = file, eventCount = eventCount))
    } catch (e: IOException) {
        ResultState.Error(message = "export failed: ${e.message}", throwable = e)
    } catch (e: RuntimeException) {
        ResultState.Error(message = "export failed: ${e.message}", throwable = e)
    }

    /**
     * Behavior 6.3: exactly one [Writer.write] call per line — meta first,
     * then one line per event — so a counting writer observes
     * `calls == events + 1`. Visible for the streaming tests.
     */
    internal fun writeExport(meta: ExportMeta, events: List<LogEvent>, writer: Writer): Int {
        writer.write(JsonlEncoder.encodeMeta(meta) + NEWLINE)
        events.forEach { event ->
            writer.write(JsonlEncoder.encodeEvent(event) + NEWLINE)
        }
        return events.size
    }

    /**
     * Merged source = live ring ∪ full on-disk history (DECISIONS.md P-2),
     * narrowed by the scope predicate and sorted ascending. The sort is
     * stable: equal timestamps keep the merged emission order (ring order
     * first, then history line order).
     */
    private fun collectEvents(scope: ExportScope, nowMs: Long): List<LogEvent> {
        val currentStore = store ?: return emptyList()
        val memory = currentStore.memoryEvents()
        val history = readAllHistory(currentStore)
        val predicate = scope.toPredicate(nowMs)
        return mergeSources(memory, history)
            .filter(predicate)
            .sortedBy(LogEvent::ts)
    }

    private fun readAllHistory(store: TrailSnapshotStore): List<LogEvent> {
        val all = ArrayList<LogEvent>()
        val total = store.fileEventCount()
        var offset = 0
        while (offset < total) {
            val page = store.fileEvents(offset, minOf(ViewerPaging.PAGE_SIZE, total - offset))
            if (page.isEmpty()) {
                break // defensive: the store was truncated concurrently
            }
            all += page
            offset += page.size
        }
        return all
    }

    /**
     * Cross-source dedupe (main-agent ruling ④, same semantics as the
     * viewer): one emission lives both in the ring and in the file history,
     * so the overlap collapses by these six fields. The history side is never
     * deduped against itself — same-millisecond repeated emissions are
     * distinct lines and all survive.
     */
    private fun mergeSources(memory: List<LogEvent>, history: List<LogEvent>): List<LogEvent> {
        if (history.isEmpty()) {
            return memory
        }
        val ringIdentities = memory.mapTo(HashSet()) { it.identity() }
        return memory + history.filter { event -> event.identity() !in ringIdentities }
    }

    private fun LogEvent.identity(): EventIdentity = EventIdentity(
        ts = ts,
        traceId = traceId,
        tag = tag,
        name = name,
        attrs = attrs,
        tid = tid,
    )

    private data class EventIdentity(
        val ts: Long,
        val traceId: String?,
        val tag: String,
        val name: String,
        val attrs: Map<String, String>,
        val tid: String?,
    )

    /**
     * Meta string providers degrade to "unknown" instead of failing the
     * export: metadata problems must never block a debug artifact. The device
     * provider goes through #3's [ExportMeta.deviceOrUnknown].
     */
    private fun providedOrUnknown(provider: () -> String?): String =
        runCatching(provider).getOrNull()?.takeIf { it.isNotBlank() } ?: ExportMeta.UNKNOWN_DEVICE

    /** ISO-8601 with timezone offset (PRD §2.5), derived from the injected clock. */
    private fun exportedAt(nowMs: Long): String =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(Instant.ofEpochMilli(nowMs).atZone(zone))

    private fun writeToFile(meta: ExportMeta, events: List<LogEvent>, file: File): Int {
        val dir = file.parentFile
        if (dir != null && !dir.exists()) {
            dir.mkdirs()
        }
        return file.bufferedWriter(Charsets.UTF_8).use { writer ->
            writeExport(meta, events, writer)
        }
    }

    private companion object {
        const val NEWLINE = "\n"
    }
}
