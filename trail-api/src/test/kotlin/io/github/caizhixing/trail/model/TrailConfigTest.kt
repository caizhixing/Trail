package io.github.caizhixing.trail.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailConfigTest {

    @Test
    fun `defaults match frozen values`() {
        val config = TrailConfig()

        assertEquals(2000, config.ringCapacity)
        assertEquals(5242880L, config.maxFileBytes)
        assertEquals(3, config.maxFiles)
        assertEquals(512, config.attrValueMaxLength)
        assertFalse(config.logcatEcho)
    }

    @Test
    fun `explicit constructor overrides every default`() {
        val config = TrailConfig(
            ringCapacity = 64,
            maxFileBytes = 1024L,
            maxFiles = 1,
            attrValueMaxLength = 16,
            logcatEcho = true,
        )

        assertEquals(64, config.ringCapacity)
        assertEquals(1024L, config.maxFileBytes)
        assertEquals(1, config.maxFiles)
        assertEquals(16, config.attrValueMaxLength)
        assertTrue(config.logcatEcho)
    }
}
