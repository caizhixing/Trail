package io.github.caizhixing.trail

import android.content.Context
import io.github.caizhixing.trail.debug.engine.AndroidBridges
import io.github.caizhixing.trail.debug.engine.AttrSanitizer
import io.github.caizhixing.trail.debug.engine.CaptureEngine
import io.github.caizhixing.trail.debug.engine.EngineLogger
import io.github.caizhixing.trail.debug.engine.MaskingPolicy
import io.github.caizhixing.trail.debug.engine.RingBuffer
import io.github.caizhixing.trail.debug.engine.TrailSnapshotStore
import io.github.caizhixing.trail.debug.trace.TraceBridge
import io.github.caizhixing.trail.debug.trace.TraceContext
import io.github.caizhixing.trail.debug.trace.TraceIds
import io.github.caizhixing.trail.model.Audience
import io.github.caizhixing.trail.model.Level
import io.github.caizhixing.trail.model.LogEvent
import io.github.caizhixing.trail.model.TrailConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Debug-artifact facade: capture engine (issue #3 — attr sanitization, pre-init
 * routing, engine lifecycle) and live trace propagation (issue #4 — events built
 * while a trace is active carry its id).
 */
@PublishedApi
internal object TrailImpl {

    @Volatile
    private var engine: CaptureEngine? = null

    @Volatile
    private var sanitizer: AttrSanitizer = defaultSanitizer()

    // Decision D-3: events observed before init stay in this bounded ring for
    // debug snapshots only; once init happens they are NOT flushed to disk.
    private val preInitRing = RingBuffer(TrailConfig().ringCapacity)

    internal fun init(context: Context, config: TrailConfig) {
        installEngine(
            config = config,
            dir = File(context.filesDir, "trail"),
            ioDispatcher = Dispatchers.IO,
            logger = AndroidBridges.logcatLogger,
            echoSink = resolveEchoSink(config, AndroidBridges.logcatEchoSink),
        )
    }

    /** Fully injected assembly seam for unit tests and internal callers. */
    internal fun installEngine(
        config: TrailConfig,
        dir: File,
        ioDispatcher: CoroutineDispatcher,
        logger: EngineLogger,
        echoSink: ((String) -> Unit)? = null,
    ) {
        synchronized(this) {
            if (engine != null) {
                // Behavior 3.9: init is idempotent — never rebuild, never drop data.
                return
            }
            engine = CaptureEngine(config, dir, ioDispatcher, logger, echoSink)
            sanitizer = AttrSanitizer(MaskingPolicy.default(), config.attrValueMaxLength)
        }
    }

    /** Behavior 3.11: echo is opt-in via TrailConfig.logcatEcho (default off). */
    internal fun resolveEchoSink(
        config: TrailConfig,
        logcatSink: (String) -> Unit,
    ): ((String) -> Unit)? = if (config.logcatEcho) logcatSink else null

    @PublishedApi
    internal fun buildEvent(
        tag: String,
        name: String,
        audience: Audience,
        level: Level,
        attrs: Map<String, String>,
    ): LogEvent =
        LogEvent(
            ts = System.currentTimeMillis(),
            traceId = currentTraceId(),
            tag = tag,
            name = name,
            audience = audience,
            level = level,
            attrs = attrs,
            tid = Thread.currentThread().name,
        )

    @PublishedApi
    internal fun event(e: LogEvent) {
        val cleaned = withSanitizedAttrs(e)
        val currentEngine = engine
        if (currentEngine == null) {
            preInitRing.add(cleaned)
        } else {
            currentEngine.submit(cleaned)
        }
    }

    /**
     * Runs [block] under a fresh trace id. [name] only labels the scope for
     * future span tooling — v1 has no span tree (NG6). The id travels in the
     * coroutine context (structured concurrency) and is mirrored onto every
     * thread the coroutine runs on, so the non-suspending [currentTraceId]
     * resolves it anywhere inside the block.
     */
    internal suspend fun <T> withTrace(name: String, block: suspend () -> T): T {
        val traceId = TraceIds.newId()
        return withContext(TraceContext(traceId) + TraceBridge.coroutineElement(traceId)) {
            block()
        }
    }

    internal fun currentTraceId(): String? = TraceBridge.currentTraceId()

    /**
     * Manual bridge for callback / Java call sites: pushes a fresh id onto the
     * per-thread manual stack; close pops it. Shadowed whenever a coroutine
     * trace is active.
     */
    internal fun beginTrace(name: String): AutoCloseable = TraceBridge.beginManualTrace()

    internal fun dumpRecent(count: Int): String {
        if (count <= 0) {
            return ""
        }
        // Behavior 3.10: before init there is no engine slice to dump.
        return engine?.dumpRecent(count) ?: ""
    }

    /** Read-only snapshot surface for the viewer (issue #5); null before init. */
    internal fun snapshotStore(): TrailSnapshotStore? = engine

    /** Observation point covering both the pre-init ring and the engine ring. */
    internal fun debugEvents(): List<LogEvent> =
        engine?.memoryEvents() ?: preInitRing.snapshot()

    internal fun resetForTest() {
        synchronized(this) {
            engine?.shutdown()
            engine = null
            sanitizer = defaultSanitizer()
            preInitRing.clear()
        }
    }

    private fun withSanitizedAttrs(e: LogEvent): LogEvent {
        if (e.attrs.isEmpty()) {
            return e
        }
        val sanitized = sanitizer.sanitize(e.attrs)
        return if (sanitized == e.attrs) e else e.copy(attrs = sanitized)
    }

    private fun defaultSanitizer(): AttrSanitizer =
        AttrSanitizer(MaskingPolicy.default(), TrailConfig().attrValueMaxLength)
}
