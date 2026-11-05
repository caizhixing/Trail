package io.github.caizhixing.trail.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LevelTest {

    @Test
    fun `entries are exactly info warn error in declaration order`() {
        assertEquals(3, Level.entries.size)
        assertEquals(
            listOf(Level.INFO, Level.WARN, Level.ERROR),
            Level.entries.toList(),
        )
        assertEquals(listOf("INFO", "WARN", "ERROR"), Level.entries.map { it.name })
    }
}
