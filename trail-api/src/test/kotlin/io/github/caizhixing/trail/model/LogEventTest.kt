package io.github.caizhixing.trail.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogEventTest {

    @Test
    fun `constructor assigns all eight fields`() {
        val event = LogEvent(
            ts = 1730390400000L,
            traceId = "t_0123456789abcdef",
            tag = "widget",
            name = "bind_start",
            audience = Audience.BOTH,
            level = Level.INFO,
            attrs = mapOf("widgetId" to "weather_1", "host" to "launcher"),
            tid = "main",
        )

        assertEquals(1730390400000L, event.ts)
        assertEquals("t_0123456789abcdef", event.traceId)
        assertEquals("widget", event.tag)
        assertEquals("bind_start", event.name)
        assertEquals(Audience.BOTH, event.audience)
        assertEquals(Level.INFO, event.level)
        assertEquals(mapOf("widgetId" to "weather_1", "host" to "launcher"), event.attrs)
        assertEquals("main", event.tid)
    }

    @Test
    fun `traceId and tid accept null`() {
        val event = LogEvent(
            ts = 1L,
            traceId = null,
            tag = "network",
            name = "http_error",
            audience = Audience.AI,
            level = Level.ERROR,
            attrs = emptyMap(),
            tid = null,
        )

        assertNull(event.traceId)
        assertNull(event.tid)
        assertEquals(emptyMap<String, String>(), event.attrs)
    }
}
