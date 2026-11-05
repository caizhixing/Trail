package io.github.caizhixing.trail.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AudienceTest {

    @Test
    fun `entries are exactly human ai both in declaration order`() {
        assertEquals(3, Audience.entries.size)
        assertEquals(
            listOf(Audience.HUMAN, Audience.AI, Audience.BOTH),
            Audience.entries.toList(),
        )
        assertEquals(listOf("HUMAN", "AI", "BOTH"), Audience.entries.map { it.name })
    }
}
