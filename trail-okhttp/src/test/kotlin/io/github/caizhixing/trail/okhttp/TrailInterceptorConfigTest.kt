package io.github.caizhixing.trail.okhttp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Behavior 7.7: the config data class freezes the PRD §2.6 / P-6 defaults. */
class TrailInterceptorConfigTest {

    @Test
    fun defaultsMatchFrozenContract() {
        val config = TrailInterceptorConfig()

        assertTrue(config.recordBodies)
        assertEquals(4096, config.maxBodyBytes)
        assertEquals(emptyList<String>(), config.headerAttrs)
    }
}
