package io.github.caizhixing.trail.debug.trace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TraceIdsTest {

    @Test
    fun newIdMatchesContractFormat() {
        val id = TraceIds.newId()

        assertTrue(
            "expected t_ + 16 hex chars, got: $id",
            TRAIL_ID_FORMAT.matches(id),
        )
    }

    @Test
    fun newIdsAreUniqueAcrossLargeBatch() {
        val ids = List(10_000) { TraceIds.newId() }

        assertEquals("ids must be unique", 10_000, ids.distinct().size)
    }

    private companion object {
        // DECISIONS.md P-7: traceId = t_ + 16 hex
        val TRAIL_ID_FORMAT = Regex("t_[0-9a-f]{16}")
    }
}
