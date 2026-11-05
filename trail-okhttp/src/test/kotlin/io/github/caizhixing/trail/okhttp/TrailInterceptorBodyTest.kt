package io.github.caizhixing.trail.okhttp

import io.github.caizhixing.trail.Trail
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/** Behavior 7.3: body recording defaults, opt-out and non-textual handling. */
class TrailInterceptorBodyTest {

    @Before
    fun resetFakeTrail() {
        Trail.reset()
    }

    @Test
    fun bodyLargerThanFourKbIsTruncatedTo4096Chars() {
        TrailInterceptor().intercept(
            FakeChain(
                trailTestRequest(),
                trailTestResponse(200, bodyText = "a".repeat(10_000), contentType = "text/plain".toMediaType()),
            ),
        )

        assertEquals(4096, Trail.recordedEvents().last().attrs.getValue("body").length)
    }

    @Test
    fun recordBodiesFalseOmitsBodyAttr() {
        val config = TrailInterceptorConfig(recordBodies = false)
        TrailInterceptor(config).intercept(
            FakeChain(
                trailTestRequest(),
                trailTestResponse(200, bodyText = "hello", contentType = "text/plain".toMediaType()),
            ),
        )

        assertFalse(Trail.recordedEvents().last().attrs.containsKey("body"))
    }

    @Test
    fun nonTextualContentTypeRecordsOmittedMarker() {
        TrailInterceptor().intercept(
            FakeChain(
                trailTestRequest(),
                trailTestResponse(200, bodyText = "bin", contentType = "application/octet-stream".toMediaType()),
            ),
        )

        assertEquals("<omitted>", Trail.recordedEvents().last().attrs.getValue("body"))
    }

    @Test
    fun imageContentTypeRecordsOmittedMarker() {
        TrailInterceptor().intercept(
            FakeChain(
                trailTestRequest(),
                trailTestResponse(200, bodyText = "png", contentType = "image/png".toMediaType()),
            ),
        )

        assertEquals("<omitted>", Trail.recordedEvents().last().attrs.getValue("body"))
    }

    @Test
    fun responseWithoutBodyOmitsBodyAttr() {
        TrailInterceptor().intercept(FakeChain(trailTestRequest(), trailTestResponse(204)))

        assertFalse(Trail.recordedEvents().last().attrs.containsKey("body"))
    }

    @Test
    fun missingContentTypeIsTreatedAsTextual() {
        TrailInterceptor().intercept(FakeChain(trailTestRequest(), trailTestResponse(200, bodyText = "plain")))

        assertEquals("plain", Trail.recordedEvents().last().attrs.getValue("body"))
    }
}
