package io.github.caizhixing.trail.okhttp

import io.github.caizhixing.trail.Trail
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Behavior 7.4: trailTraced() captures the call-site trace id into the request
 * tag; the interceptor emits both events with that explicit traceId, absence
 * of the tag yields traceId=null. The trace scope comes from the test-source
 * Trail facade (see its KDoc): withTrace scopes a generated id exactly like
 * the debug contract (4.1 + P-7) without any semantic fork.
 */
class TrailTracedTest {

    @Before
    fun resetFakeTrail() {
        Trail.reset()
    }

    @Test
    fun trailTracedInsideTraceCapturesCurrentTraceId() = runBlocking {
        val (expectedTraceId, request) = Trail.withTrace("trail_test_traced_builder") {
            val traceId = Trail.currentTraceId()
            traceId to trailTestRequest().newBuilder().trailTraced().build()
        }

        assertNotNull(expectedTraceId)
        val tag = request.tag(TrailTraceTag::class.java)
        assertNotNull(tag)
        assertEquals(expectedTraceId, tag?.traceId)
    }

    @Test
    fun interceptorEmitsBothEventsWithCapturedTraceId() = runBlocking {
        val request = Trail.withTrace("trail_test_traced_intercept") {
            trailTestRequest().newBuilder().trailTraced().build()
        }
        val capturedTraceId = request.tag(TrailTraceTag::class.java)?.traceId
        TrailInterceptor().intercept(
            FakeChain(request, trailTestResponse(200, bodyText = "ok", contentType = "application/json".toMediaType(), request = request)),
        )

        val events = Trail.recordedEvents()
        assertEquals(2, events.size)
        assertNotNull(capturedTraceId)
        assertEquals(capturedTraceId, events[0].traceId)
        assertEquals(capturedTraceId, events[1].traceId)
    }

    @Test
    fun requestWithoutTrailTracedEmitsEventsWithNullTraceId() {
        TrailInterceptor().intercept(FakeChain(trailTestRequest(), trailTestResponse(200)))

        val events = Trail.recordedEvents()
        assertEquals(2, events.size)
        assertNull(events[0].traceId)
        assertNull(events[1].traceId)
    }

    @Test
    fun trailTracedOutsideTraceWritesNoTag() {
        val request = trailTestRequest().newBuilder().trailTraced().build()

        assertNull(request.tag(TrailTraceTag::class.java))
    }
}
