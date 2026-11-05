package io.github.caizhixing.trail.debug.engine

import io.github.caizhixing.trail.model.Audience
import io.github.caizhixing.trail.model.Level
import io.github.caizhixing.trail.model.LogEvent
import io.github.caizhixing.trail.model.TrailConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Behaviors 3.1 / 3.3 / 3.10 / 3.11 / 3.12 at the engine level. Dispatcher,
 * logger, echo sink and directory are all injected; the manual pump proves
 * where the IO actually happens.
 */
class CaptureEngineTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class RecordingLogger : EngineLogger {
        val warnings = mutableListOf<String>()
        override fun warn(message: String) {
            warnings.add(message)
        }
    }

    @Test
    fun submitDoesNoIoOnTheCallingThread() {
        val dispatcher = ManualPumpDispatcher()
        val logger = RecordingLogger()
        val ioThreads = mutableListOf<Thread>()
        var opened = 0
        val engine = CaptureEngine(
            config = TrailConfig(),
            dir = tmp.newFolder("trail-data"),
            ioDispatcher = dispatcher,
            logger = logger,
            echoSink = null,
            sinkFactory = { file ->
                opened++
                ioThreads.add(Thread.currentThread())
                file.bufferedWriter(Charsets.UTF_8)
            },
        )

        engine.submit(eventOf("trail_test_caller_thread"))

        assertEquals("calling thread must not touch the disk before the pump", 0, opened)
        assertEquals(0, engine.fileCount())
        assertEquals(1, engine.memoryEvents().size)

        dispatcher.pump()

        assertEquals(1, opened)
        assertEquals("IO must run inside the pumped consumer, not the submit path", 1, ioThreads.size)
        assertEquals(Thread.currentThread(), ioThreads.single())
        assertEquals(1, engine.fileCount())
        assertEquals(1, engine.fileEventCount())
        engine.shutdown()
    }

    @Test
    fun queueFullDropsTheOldestAndWarnsExactlyOnce() {
        val dispatcher = ManualPumpDispatcher()
        val logger = RecordingLogger()
        val engine = CaptureEngine(
            config = TrailConfig(ringCapacity = 2),
            dir = tmp.newFolder("trail-data"),
            ioDispatcher = dispatcher,
            logger = logger,
        )

        for (index in 1..4) {
            engine.submit(eventOf("trail_test_$index"))
        }
        engine.submit(eventOf("trail_test_5"))

        assertEquals(listOf("trail_test_4", "trail_test_5"), engine.memoryEvents().map { it.name })
        assertEquals(1, logger.warnings.size)
        assertTrue(logger.warnings.single().contains("queue full"))

        dispatcher.pump()

        // channel capacity 2: e1/e2 queued, e3 forced e1 out, e4 forced e2 out, e5 forced e3 out
        assertEquals(listOf("trail_test_4", "trail_test_5"), engine.fileEvents(0, 10).map { it.name })
        assertEquals("later overflow must not spam", 1, logger.warnings.size)
        engine.shutdown()
    }

    @Test
    fun dumpRecentSlicesTheRingAscending() {
        val engine = newEngine(TrailConfig(ringCapacity = 3))

        for (index in 1..5) {
            engine.submit(eventOf("trail_test_$index"))
        }

        val expectedLastTwo = engine.memoryEvents().drop(1).joinToString("\n") { JsonlEncoder.encodeEvent(it) }
        val expectedAll = engine.memoryEvents().joinToString("\n") { JsonlEncoder.encodeEvent(it) }

        assertEquals(expectedLastTwo, engine.dumpRecent(2))
        assertEquals(expectedAll, engine.dumpRecent(10))
        assertEquals(3, engine.dumpRecent(10).lines().size)
        assertEquals("", engine.dumpRecent(0))
        assertEquals("", engine.dumpRecent(-3))
        engine.shutdown()
    }

    @Test
    fun echoLineFollowsTheDocumentedFormat() {
        val engine = newEngine(TrailConfig())

        assertEquals(
            "[Trail] widget bind_start slot=search lang=zh",
            engine.echoLine(eventOf(name = "bind_start", attrs = linkedMapOf("slot" to "search", "lang" to "zh"))),
        )
        assertEquals("[Trail] widget bind_start", engine.echoLine(eventOf(name = "bind_start")))
        assertEquals(
            100,
            engine.echoLine(eventOf(name = "bind_start", attrs = mapOf("blob" to "x".repeat(300)))).length,
        )
        assertTrue(
            engine.echoLine(eventOf(name = "bind_start", attrs = mapOf("blob" to "x".repeat(300)))).endsWith("..."),
        )
        engine.shutdown()
    }

    @Test
    fun echoSinkReceivesEverySubmittedEvent() {
        val echoed = mutableListOf<String>()
        val engine = CaptureEngine(
            config = TrailConfig(ringCapacity = 8),
            dir = tmp.newFolder("trail-data"),
            ioDispatcher = ManualPumpDispatcher(),
            logger = RecordingLogger(),
            echoSink = { echoed.add(it) },
        )

        engine.submit(eventOf("trail_test_a"))
        engine.submit(eventOf("trail_test_b"))

        assertEquals(2, echoed.size)
        assertTrue(echoed[0].startsWith("[Trail] widget trail_test_a"))
        assertTrue(echoed[1].startsWith("[Trail] widget trail_test_b"))
        engine.shutdown()
    }

    @Test
    fun fileEventsRoundTripAfterThePump() {
        val dispatcher = ManualPumpDispatcher()
        val engine = newEngine(TrailConfig(ringCapacity = 8), dispatcher)

        engine.submit(eventOf("trail_test_a"))
        engine.submit(eventOf("trail_test_b"))
        dispatcher.pump()

        assertEquals(engine.memoryEvents(), engine.fileEvents(0, 10))
        assertEquals(2, engine.fileEventCount())
        assertEquals(1, engine.fileCount())
        engine.shutdown()
    }

    @Test
    fun tenThousandSubmitsKeepRingAndFileLimits() {
        val dispatcher = ManualPumpDispatcher()
        val engine = newEngine(
            TrailConfig(ringCapacity = 2000, maxFileBytes = 4L * 1024, maxFiles = 3),
            dispatcher,
        )

        for (index in 1..10_000) {
            engine.submit(eventOf("trail_test_%05d".format(index)))
        }

        val dump = engine.dumpRecent(2000)
        assertEquals(2000, dump.lines().size)
        assertTrue(dump.lines().first().contains("trail_test_08001"))
        assertTrue(dump.lines().last().contains("trail_test_10000"))

        dispatcher.pump()

        assertEquals(3, engine.fileCount())
        assertTrue("retained history must be non-empty", engine.fileEventCount() > 0)
        assertTrue("history fits within the retained window", engine.fileEventCount() <= engine.fileCount() * 4 * 1024)
        engine.shutdown()
    }

    @Test
    fun submitAfterShutdownIsDroppedWithoutCrashing() {
        val engine = newEngine(TrailConfig())

        engine.shutdown()
        engine.submit(eventOf("trail_test_after_shutdown"))

        assertEquals(0, engine.memoryEvents().size)
    }

    private fun newEngine(config: TrailConfig, dispatcher: ManualPumpDispatcher = ManualPumpDispatcher()): CaptureEngine =
        CaptureEngine(
            config = config,
            dir = tmp.newFolder("trail-data"),
            ioDispatcher = dispatcher,
            logger = RecordingLogger(),
        )

    private fun eventOf(
        name: String = "trail_test_event",
        attrs: Map<String, String> = emptyMap(),
    ): LogEvent =
        LogEvent(
            ts = 1_000L,
            traceId = null,
            tag = "widget",
            name = name,
            audience = Audience.AI,
            level = Level.INFO,
            attrs = attrs,
            tid = null,
        )
}
