package io.github.caizhixing.trail.debug.engine

import io.github.caizhixing.trail.model.LogEvent
import io.github.caizhixing.trail.model.TrailConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import java.io.Writer

/**
 * Capture pipeline orchestrator (behaviors 3.1 / 3.3 / 3.10 / 3.11 / 3.12).
 *
 * The calling thread only sanitizes-none-touch-IO: `submit` adds the event to
 * the in-memory ring, optionally echoes it, and enqueues it on a bounded
 * channel. All disk IO happens in the consumer coroutine running on the
 * injected [ioDispatcher]; when the queue is full the oldest pending event is
 * dropped and a single WARN is emitted for the whole session.
 *
 * [sinkFactory] exists so tests can probe where IO actually runs (behavior 3.1).
 */
internal class CaptureEngine(
    config: TrailConfig,
    dir: File,
    ioDispatcher: CoroutineDispatcher,
    logger: EngineLogger,
    echoSink: ((String) -> Unit)? = null,
    sinkFactory: ((File) -> Writer)? = null,
) : TrailSnapshotStore {

    private val ring = RingBuffer(config.ringCapacity)
    private val encoder = JsonlEncoder
    private val overflowWarner = OnceWarner(logger)
    private val ioWarner = OnceWarner(logger)
    private val store = JsonlFileStore(
        dir = dir,
        maxFileBytes = config.maxFileBytes,
        maxFiles = config.maxFiles,
        sinkFactory = sinkFactory ?: { file -> file.bufferedWriter(Charsets.UTF_8) },
        ioWarner = ioWarner::warn,
    )
    private val echoSink = echoSink

    @Volatile
    private var closed = false

    private val channel = Channel<LogEvent>(capacity = maxOf(1, config.ringCapacity))

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    init {
        scope.launch {
            for (event in channel) {
                store.append(encoder.encodeEvent(event))
            }
        }
    }

    /** Enqueue-and-return entry point for the calling thread (behavior 3.1). */
    fun submit(event: LogEvent) {
        if (closed) {
            return
        }
        ring.add(event)
        echoSink?.invoke(echoLine(event))
        if (channel.trySend(event).isFailure) {
            // Behavior 3.3: drop the oldest queued item to make room and warn once.
            channel.tryReceive()
            channel.trySend(event)
            overflowWarner.warn("persist queue full; dropped the oldest pending event")
        }
    }

    /** Behavior 3.10: newest `count` ring events as ascending JSONL text. */
    fun dumpRecent(count: Int): String {
        if (count <= 0) {
            return ""
        }
        val snapshot = ring.snapshot()
        val fromIndex = maxOf(0, snapshot.size - count)
        return snapshot.subList(fromIndex, snapshot.size)
            .joinToString(separator = "\n") { encoder.encodeEvent(it) }
    }

    /** Behavior 3.11: `[Trail] tag name k=v k=v …` capped at [ECHO_MAX_LENGTH]. */
    internal fun echoLine(event: LogEvent): String {
        val summary = event.attrs.entries.joinToString(separator = " ") { "${it.key}=${it.value}" }
        val head = if (summary.isEmpty()) {
            "[Trail] ${event.tag} ${event.name}"
        } else {
            "[Trail] ${event.tag} ${event.name} $summary"
        }
        return if (head.length <= ECHO_MAX_LENGTH) {
            head
        } else {
            head.take(ECHO_MAX_LENGTH - ECHO_ELLIPSIS.length) + ECHO_ELLIPSIS
        }
    }

    fun shutdown() {
        closed = true
        channel.close()
        scope.cancel()
        store.close()
    }

    override fun memoryEvents(): List<LogEvent> = ring.snapshot()

    override fun fileEvents(offset: Int, limit: Int): List<LogEvent> = store.readEvents(offset, limit)

    override fun fileEventCount(): Int = store.eventCount()

    override fun fileCount(): Int = store.fileCount()

    private companion object {
        const val ECHO_MAX_LENGTH = 100
        const val ECHO_ELLIPSIS = "..."
    }
}
