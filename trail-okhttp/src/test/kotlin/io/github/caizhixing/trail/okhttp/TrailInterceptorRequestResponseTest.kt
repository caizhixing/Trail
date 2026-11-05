package io.github.caizhixing.trail.okhttp

import io.github.caizhixing.trail.Trail
import io.github.caizhixing.trail.model.Audience
import io.github.caizhixing.trail.model.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Behavior 7.1: one request emits http_request + http_response. */
class TrailInterceptorRequestResponseTest {

    @Before
    fun resetFakeTrail() {
        Trail.reset()
    }

    @Test
    fun twoHundredRequestEmitsRequestAndResponseEvents() {
        TrailInterceptor().intercept(FakeChain(trailTestRequest(), trailTestResponse(200, bodyText = "ok")))

        val events = Trail.recordedEvents()
        assertEquals(2, events.size)

        val requestEvent = events[0]
        assertEquals("network", requestEvent.tag)
        assertEquals("http_request", requestEvent.name)
        assertEquals(Audience.AI, requestEvent.audience)
        assertEquals(Level.INFO, requestEvent.level)
        assertEquals(mapOf("method" to "GET", "url" to TRAIL_TEST_URL), requestEvent.attrs)

        val responseEvent = events[1]
        assertEquals("network", responseEvent.tag)
        assertEquals("http_response", responseEvent.name)
        assertEquals(Audience.AI, responseEvent.audience)
        assertEquals(Level.INFO, responseEvent.level)
        assertEquals("GET", responseEvent.attrs.getValue("method"))
        assertEquals(TRAIL_TEST_URL, responseEvent.attrs.getValue("url"))
        assertEquals("200", responseEvent.attrs.getValue("status"))
        assertEquals("ok", responseEvent.attrs.getValue("body"))
        val durationMs = responseEvent.attrs.getValue("duration_ms").toLongOrNull()
        assertTrue(
            "duration_ms must parse and be >= 0, got ${responseEvent.attrs["duration_ms"]}",
            durationMs != null && durationMs >= 0L,
        )
    }
}
