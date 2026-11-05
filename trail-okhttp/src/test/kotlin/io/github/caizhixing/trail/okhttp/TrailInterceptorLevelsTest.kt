package io.github.caizhixing.trail.okhttp

import io.github.caizhixing.trail.Trail
import io.github.caizhixing.trail.model.Level
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertThrows

/** Behavior 7.2: level mapping 2xx=INFO / non-2xx=WARN / failure=ERROR. */
class TrailInterceptorLevelsTest {

    @Before
    fun resetFakeTrail() {
        Trail.reset()
    }

    @Test
    fun boundaryTwoHundredNinetyNineIsInfoAndThreeHundredIsWarn() {
        TrailInterceptor().intercept(FakeChain(trailTestRequest(), trailTestResponse(299)))
        assertEquals(Level.INFO, Trail.recordedEvents().last().level)

        Trail.reset()

        TrailInterceptor().intercept(FakeChain(trailTestRequest(), trailTestResponse(300)))
        assertEquals(Level.WARN, Trail.recordedEvents().last().level)
    }

    @Test
    fun nonTwoHundredResponseIsWarn() {
        TrailInterceptor().intercept(FakeChain(trailTestRequest(), trailTestResponse(503)))

        val responseEvent = Trail.recordedEvents().last()
        assertEquals("http_response", responseEvent.name)
        assertEquals(Level.WARN, responseEvent.level)
        assertEquals("503", responseEvent.attrs.getValue("status"))
    }

    @Test
    fun ioExceptionEmitsHttpErrorWithClassNameAndMessageAndRethrows() {
        val error = IOException("trail_test_connection_reset")
        val chain = FakeChain(trailTestRequest(), scriptedError = error)

        val thrown = assertThrows(IOException::class.java) { TrailInterceptor().intercept(chain) }
        assertSame(error, thrown)

        val events = Trail.recordedEvents()
        assertEquals(2, events.size)
        val errorEvent = events[1]
        assertEquals("http_error", errorEvent.name)
        assertEquals(Level.ERROR, errorEvent.level)
        assertEquals("GET", errorEvent.attrs.getValue("method"))
        assertEquals(TRAIL_TEST_URL, errorEvent.attrs.getValue("url"))
        assertEquals("java.io.IOException: trail_test_connection_reset", errorEvent.attrs.getValue("error"))
        assertTrue(errorEvent.attrs.getValue("error").length <= 256)
    }

    @Test
    fun errorAttrIsTruncatedTo256Chars() {
        val chain = FakeChain(trailTestRequest(), scriptedError = IOException("x".repeat(500)))

        assertThrows(IOException::class.java) { TrailInterceptor().intercept(chain) }

        val errorAttr = Trail.recordedEvents().last().attrs.getValue("error")
        assertTrue(errorAttr.startsWith("java.io.IOException: "))
        assertEquals(256, errorAttr.length)
    }
}
