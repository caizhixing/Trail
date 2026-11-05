package io.github.caizhixing.trail.okhttp

import io.github.caizhixing.trail.Trail
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/** Behavior 7.5: proceed exactly once; failures recorded first, rethrown unchanged. */
class TrailInterceptorProceedOnceTest {

    @Before
    fun resetFakeTrail() {
        Trail.reset()
    }

    @Test
    fun happyPathCallsProceedExactlyOnce() {
        val chain = FakeChain(trailTestRequest(), trailTestResponse(200))

        TrailInterceptor().intercept(chain)

        assertEquals(1, chain.proceedCalls)
    }

    @Test
    fun errorPathCallsProceedOnceRecordsThenRethrowsSameInstance() {
        val boom = IOException("trail_test_boom")
        val chain = FakeChain(trailTestRequest(), scriptedError = boom)

        val thrown = assertThrows(IOException::class.java) { TrailInterceptor().intercept(chain) }

        assertSame(boom, thrown)
        assertEquals(1, chain.proceedCalls)
        val events = Trail.recordedEvents()
        assertEquals(2, events.size)
        assertEquals("http_request", events[0].name)
        assertEquals("http_error", events[1].name)
    }
}
