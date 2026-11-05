package io.github.caizhixing.trail.okhttp

import io.github.caizhixing.trail.Trail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/**
 * Behavior 7.6: the headerAttrs extension point copies opted-in request
 * headers into attrs; sensitive keys (P-4 list) are masked at the source.
 */
class TrailInterceptorHeaderMaskingTest {

    @Before
    fun resetFakeTrail() {
        Trail.reset()
    }

    @Test
    fun configuredHeaderAttrsCopiedWithAuthorizationMasked() {
        val request = trailTestRequest().newBuilder()
            .header("Authorization", "Bearer trail_test_secret_token")
            .header("X-Request-Id", "trail_test_req_42")
            .build()
        val config = TrailInterceptorConfig(headerAttrs = listOf("Authorization", "X-Request-Id"))

        TrailInterceptor(config).intercept(FakeChain(request, trailTestResponse(200, request = request)))

        val requestEvent = Trail.recordedEvents()[0]
        assertEquals("***", requestEvent.attrs.getValue("Authorization"))
        assertEquals("trail_test_req_42", requestEvent.attrs.getValue("X-Request-Id"))
    }

    @Test
    fun configuredButAbsentHeaderIsOmitted() {
        val config = TrailInterceptorConfig(headerAttrs = listOf("Authorization"))

        TrailInterceptor(config).intercept(FakeChain(trailTestRequest(), trailTestResponse(200)))

        assertFalse(Trail.recordedEvents()[0].attrs.containsKey("Authorization"))
    }

    @Test
    fun defaultConfigNeverCopiesHeaders() {
        val request = trailTestRequest().newBuilder()
            .header("Authorization", "Bearer trail_test_secret_token")
            .build()

        TrailInterceptor().intercept(FakeChain(request, trailTestResponse(200, request = request)))

        assertFalse(Trail.recordedEvents()[0].attrs.containsKey("Authorization"))
    }
}
