package io.github.caizhixing.trail

import io.github.caizhixing.trail.debug.engine.EngineLogger
import io.github.caizhixing.trail.debug.engine.ManualPumpDispatcher
import io.github.caizhixing.trail.debug.engine.TrailSnapshotStore
import io.github.caizhixing.trail.model.TrailConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * TrailImpl → engine wiring (orchestration layer, ≥90% target):
 * behaviors 3.9 (init idempotence + pre-init drop), 3.10 (dumpRecent),
 * 3.6/3.7 through the public entry, 3.11 echo wiring, snapshot store lifecycle.
 */
class TrailImplEngineWiringTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = ManualPumpDispatcher()

    private class RecordingLogger : EngineLogger {
        val warnings = mutableListOf<String>()
        override fun warn(message: String) {
            warnings.add(message)
        }
    }

    @Before
    fun reset() {
        TrailImpl.resetForTest()
    }

    @Test
    fun beforeInitEventsStayInRingButNeitherDumpNorDisk() {
        Trail.event("widget", "trail_test_pre_a")
        Trail.event("widget", "trail_test_pre_b")
        Trail.event("widget", "trail_test_pre_c")

        // Decision D-3: bounded pre-init ring keeps debug snapshots alive…
        assertEquals(3, TrailImpl.debugEvents().map { it.name }.count { it.startsWith("trail_test_pre") })
        assertEquals(
            listOf("trail_test_pre_a", "trail_test_pre_b", "trail_test_pre_c"),
            TrailImpl.debugEvents().map { it.name },
        )
        // …but dumpRecent stays empty (behavior 3.10) and nothing hits disk.
        assertEquals("", Trail.dumpRecent(10))

        TrailImpl.installEngine(
            config = TrailConfig(),
            dir = tmp.newFolder("trail-data"),
            ioDispatcher = dispatcher,
            logger = RecordingLogger(),
        )
        dispatcher.pump()

        assertEquals("pre-init events are dropped from the file pipeline (D-3)", 0, engineStore().fileEventCount())
        engineStoreShutdown()
    }

    @Test
    fun initIsIdempotentAndNeverDropsData() {
        val firstDir = tmp.newFolder("trail-first")
        TrailImpl.installEngine(
            config = TrailConfig(ringCapacity = 5),
            dir = firstDir,
            ioDispatcher = dispatcher,
            logger = RecordingLogger(),
        )
        Trail.event("widget", "trail_test_keep_me")

        TrailImpl.installEngine(
            config = TrailConfig(ringCapacity = 5),
            dir = tmp.newFolder("trail-second"),
            ioDispatcher = dispatcher,
            logger = RecordingLogger(),
        )

        Trail.event("widget", "trail_test_second_event")
        dispatcher.pump()

        assertEquals(
            listOf("trail_test_keep_me", "trail_test_second_event"),
            TrailImpl.debugEvents().map { it.name },
        )
        assertEquals(2, Trail.dumpRecent(10).lines().size)
        engineStoreShutdown()
    }

    @Test
    fun dumpRecentWiredToTheEngineRing() {
        install()
        Trail.event("widget", "trail_test_dump_target")
        dispatcher.pump()

        val dump = Trail.dumpRecent(10)

        assertEquals(1, dump.lines().size)
        assertTrue(dump.contains("trail_test_dump_target"))
        assertTrue(dump.contains("\"tag\":\"widget\""))
        engineStoreShutdown()
    }

    @Test
    fun dumpRecentRejectsZeroAndNegativeCounts() {
        install()

        assertEquals("", Trail.dumpRecent(0))
        assertEquals("", Trail.dumpRecent(-1))
        engineStoreShutdown()
    }

    @Test
    fun sanitizeWiredThroughThePublicEntry() {
        install(TrailConfig(attrValueMaxLength = 512))

        Trail.event("network", "trail_test_http_response") {
            mapOf(
                "password" to "super-secret",
                "note" to "a".repeat(600),
            )
        }

        val stored = TrailImpl.debugEvents().single()
        assertEquals("***", stored.attrs["password"])
        assertEquals(512, stored.attrs["note"]?.length)
        engineStoreShutdown()
    }

    @Test
    fun echoIsOptInPerConfig() {
        val sink: (String) -> Unit = { }

        assertNull(TrailImpl.resolveEchoSink(TrailConfig(), sink))
        assertEquals(sink, TrailImpl.resolveEchoSink(TrailConfig(logcatEcho = true), sink))
    }

    @Test
    fun echoSinkReceivesEventsWhenInstalled() {
        val echoed = mutableListOf<String>()
        TrailImpl.installEngine(
            config = TrailConfig(),
            dir = tmp.newFolder("trail-data"),
            ioDispatcher = dispatcher,
            logger = RecordingLogger(),
            echoSink = { echoed.add(it) },
        )

        Trail.event("widget", "trail_test_echoed")

        assertEquals(listOf("[Trail] widget trail_test_echoed"), echoed)
        engineStoreShutdown()
    }

    @Test
    fun snapshotStoreLifecycleAndFilePaging() {
        assertNull(TrailImpl.snapshotStore())

        install()
        assertNotNull(TrailImpl.snapshotStore())

        Trail.event("widget", "trail_test_page_a")
        Trail.event("widget", "trail_test_page_b")
        dispatcher.pump()

        val store = engineStore()
        assertEquals(2, store.fileEventCount())
        assertEquals(
            listOf("trail_test_page_a", "trail_test_page_b"),
            store.fileEvents(0, 50).map { it.name },
        )
        assertEquals(1, store.fileCount())
        engineStoreShutdown()
    }

    private fun install(config: TrailConfig = TrailConfig()) {
        TrailImpl.installEngine(
            config = config,
            dir = tmp.newFolder("trail-data"),
            ioDispatcher = dispatcher,
            logger = RecordingLogger(),
        )
    }

    private fun engineStore(): TrailSnapshotStore =
        TrailImpl.snapshotStore() ?: error("engine must be installed before reading the store")

    private fun engineStoreShutdown() {
        TrailImpl.resetForTest()
    }
}
