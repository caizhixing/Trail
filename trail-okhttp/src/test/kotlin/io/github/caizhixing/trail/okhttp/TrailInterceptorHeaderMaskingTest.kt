package io.github.caizhixing.trail.okhttp

import io.github.caizhixing.trail.Trail
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * Behavior 7.6: the headerAttrs extension point copies opted-in request
 * headers into attrs; sensitive keys (P-4 list) are masked at the source.
 * Decision 5: headerAttrs are attached uniformly to every network event
 * (http_request / http_response / http_error), so masking and truncation
 * are asserted across all emitted events, and P-4 case-insensitivity is
 * locked with all-caps / all-lowercase forms.
 */
class TrailInterceptorHeaderMaskingTest {

    @Before
    fun resetFakeTrail() {
        Trail.reset()
    }

    @Test
    fun headerAttrsAttachedAndMaskedOnRequestAndResponseEvents() {
        val request = trailTestRequest().newBuilder()
            .header("Authorization", "Bearer trail_test_secret_token")
            .header("X-Request-Id", "trail_test_req_42")
            .build()
        val config = TrailInterceptorConfig(headerAttrs = listOf("Authorization", "X-Request-Id"))

        TrailInterceptor(config).intercept(FakeChain(request, trailTestResponse(200, request = request)))

        val events = Trail.recordedEvents()
        assertEquals(2, events.size)
        assertEquals("http_request", events[0].name)
        assertEquals("http_response", events[1].name)
        for (event in events) {
            assertEquals("***", event.attrs.getValue("Authorization"))
            assertEquals("trail_test_req_42", event.attrs.getValue("X-Request-Id"))
        }
    }

    @Test
    fun headerAttrsAttachedAndMaskedOnRequestAndErrorEvents() {
        val request = trailTestRequest().newBuilder()
            .header("Authorization", "Bearer trail_test_secret_token")
            .build()
        val config = TrailInterceptorConfig(headerAttrs = listOf("Authorization"))

        assertThrows(IOException::class.java) {
            TrailInterceptor(config).intercept(FakeChain(request, scriptedError = IOException("trail_test_boom")))
        }

        val events = Trail.recordedEvents()
        assertEquals(2, events.size)
        assertEquals("http_request", events[0].name)
        assertEquals("http_error", events[1].name)
        for (event in events) {
            assertEquals("***", event.attrs.getValue("Authorization"))
        }
    }

    @Test
    fun allCapsAuthorizationNameIsMasked() {
        val request = trailTestRequest().newBuilder()
            .header("authorization", "Bearer trail_test_secret_token")
            .build()
        val config = TrailInterceptorConfig(headerAttrs = listOf("AUTHORIZATION"))

        TrailInterceptor(config).intercept(FakeChain(request, trailTestResponse(200, request = request)))

        val events = Trail.recordedEvents()
        assertEquals(2, events.size)
        for (event in events) {
            assertEquals("***", event.attrs.getValue("AUTHORIZATION"))
        }
    }

    @Test
    fun allLowercaseAuthorizationNameIsMasked() {
        val request = trailTestRequest().newBuilder()
            .header("AUTHORIZATION", "Bearer trail_test_secret_token")
            .build()
        val config = TrailInterceptorConfig(headerAttrs = listOf("authorization"))

        TrailInterceptor(config).intercept(FakeChain(request, trailTestResponse(200, request = request)))

        val events = Trail.recordedEvents()
        assertEquals(2, events.size)
        for (event in events) {
            assertEquals("***", event.attrs.getValue("authorization"))
        }
    }

    @Test
    fun nonSensitiveHeaderValueIsTruncatedTo512CharsOnAllEvents() {
        val longValue = "trail_test_long_header_value_" + "x".repeat(600)
        val request = trailTestRequest().newBuilder()
            .header("X-Long-Value", longValue)
            .build()
        val config = TrailInterceptorConfig(headerAttrs = listOf("X-Long-Value"))

        TrailInterceptor(config).intercept(FakeChain(request, trailTestResponse(200, request = request)))

        val events = Trail.recordedEvents()
        assertEquals(2, events.size)
        for (event in events) {
            val attrValue = event.attrs.getValue("X-Long-Value")
            assertEquals(512, attrValue.length)
            assertEquals(longValue.take(512), attrValue)
        }
    }

    @Test
    fun configuredButAbsentHeaderIsOmitted() {
        val config = TrailInterceptorConfig(headerAttrs = listOf("Authorization"))

        TrailInterceptor(config).intercept(FakeChain(trailTestRequest(), trailTestResponse(200)))

        val events = Trail.recordedEvents()
        assertEquals(2, events.size)
        for (event in events) {
            assertFalse(event.attrs.containsKey("Authorization"))
        }
    }

    @Test
    fun defaultConfigNeverCopiesHeaders() {
        val request = trailTestRequest().newBuilder()
            .header("Authorization", "Bearer trail_test_secret_token")
            .build()

        TrailInterceptor().intercept(FakeChain(request, trailTestResponse(200, request = request)))

        val events = Trail.recordedEvents()
        assertEquals(2, events.size)
        for (event in events) {
            assertFalse(event.attrs.containsKey("Authorization"))
        }
    }
}
