package io.github.caizhixing.trail

import io.github.caizhixing.trail.model.Audience
import io.github.caizhixing.trail.model.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TrailEventDeliveryTest {

    @Before
    fun resetCollector() {
        TrailImpl.resetForTest()
    }

    @Test
    fun eventLambdaOverloadDeliversCompleteAiInfoEvent() {
        Trail.event("widget", "trail_test_bind_start") { mapOf("slot" to "search") }

        val events = TrailImpl.debugEvents()
        assertEquals(1, events.size)
        val event = events[0]
        assertEquals("widget", event.tag)
        assertEquals("trail_test_bind_start", event.name)
        assertEquals(Audience.AI, event.audience)
        assertEquals(Level.INFO, event.level)
        assertEquals(mapOf("slot" to "search"), event.attrs)
        assertTrue("ts must be epoch millis > 0", event.ts > 0L)
        assertTrue("ts must not be in the future", event.ts <= System.currentTimeMillis())
        assertEquals(Thread.currentThread().name, event.tid)
        assertNull(event.traceId)
    }

    @Test
    fun eventLambdaOverloadDefaultsAttrsToEmptyMap() {
        Trail.event("widget", "trail_test_no_attrs")

        val events = TrailImpl.debugEvents()
        assertEquals(1, events.size)
        assertEquals(emptyMap<String, String>(), events[0].attrs)
    }

    @Test
    fun flowLambdaOverloadIsHumanInfo() {
        Trail.flow("search", "trail_test_query_submitted") { mapOf("query_length" to "3") }

        val event = TrailImpl.debugEvents().single()
        assertEquals("search", event.tag)
        assertEquals("trail_test_query_submitted", event.name)
        assertEquals(Audience.HUMAN, event.audience)
        assertEquals(Level.INFO, event.level)
        assertEquals(mapOf("query_length" to "3"), event.attrs)
    }

    @Test
    fun flowLambdaOverloadDefaultsAttrsToEmptyMap() {
        Trail.flow("search", "trail_test_flow_no_attrs")

        val event = TrailImpl.debugEvents().single()
        assertEquals(emptyMap<String, String>(), event.attrs)
    }

    @Test
    fun explicitAudienceLevelOverloadPassesValuesThrough() {
        Trail.event("network", "trail_test_retry_scheduled", Audience.BOTH, Level.WARN) { mapOf("attempt" to "2") }

        val event = TrailImpl.debugEvents().single()
        assertEquals(Audience.BOTH, event.audience)
        assertEquals(Level.WARN, event.level)
        assertEquals("network", event.tag)
        assertEquals("trail_test_retry_scheduled", event.name)
        assertEquals(mapOf("attempt" to "2"), event.attrs)
    }

    @Test
    fun explicitOverloadDefaultsAttrsToEmptyMap() {
        Trail.event("network", "trail_test_explicit_no_attrs", Audience.AI, Level.ERROR)

        val event = TrailImpl.debugEvents().single()
        assertEquals(Audience.AI, event.audience)
        assertEquals(Level.ERROR, event.level)
        assertEquals(emptyMap<String, String>(), event.attrs)
    }

    @Test
    fun eventMapOverloadBehavesLikeLambdaOverload() {
        Trail.event("widget", "trail_test_map_overload", mapOf("lang" to "java"))

        val event = TrailImpl.debugEvents().single()
        assertEquals("widget", event.tag)
        assertEquals("trail_test_map_overload", event.name)
        assertEquals(Audience.AI, event.audience)
        assertEquals(Level.INFO, event.level)
        assertEquals(mapOf("lang" to "java"), event.attrs)
        assertNull(event.traceId)
    }

    @Test
    fun eventMapOverloadWithAudienceLevelPassesValuesThrough() {
        Trail.event("network", "trail_test_map_explicit", Audience.HUMAN, Level.ERROR, mapOf("source" to "legacy"))

        val event = TrailImpl.debugEvents().single()
        assertEquals(Audience.HUMAN, event.audience)
        assertEquals(Level.ERROR, event.level)
        assertEquals(mapOf("source" to "legacy"), event.attrs)
    }

    @Test
    fun flowMapOverloadIsHumanInfo() {
        Trail.flow("search", "trail_test_flow_map", mapOf("lang" to "java"))

        val event = TrailImpl.debugEvents().single()
        assertEquals(Audience.HUMAN, event.audience)
        assertEquals(Level.INFO, event.level)
        assertEquals(mapOf("lang" to "java"), event.attrs)
    }

    @Test
    fun attrsLambdaEvaluatedExactlyOnceLazily() {
        var evaluations = 0

        Trail.event("widget", "trail_test_lazy_attrs") {
            evaluations++
            mapOf("evaluation" to "lazy")
        }

        assertEquals(1, evaluations)
        assertEquals(mapOf("evaluation" to "lazy"), TrailImpl.debugEvents().single().attrs)
    }

    @Test
    fun collectorKeepsInsertionOrder() {
        Trail.flow("search", "trail_test_first")
        Trail.event("widget", "trail_test_second")

        val events = TrailImpl.debugEvents()
        assertEquals(2, events.size)
        assertEquals("trail_test_first", events[0].name)
        assertEquals("trail_test_second", events[1].name)
        assertTrue(events[1].ts >= events[0].ts)
    }
}
