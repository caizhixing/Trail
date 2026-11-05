package io.github.caizhixing.trail

import org.junit.Assert.assertTrue
import org.junit.Test

class TrailGateEnabledTest {

    @Test
    fun debugArtifactGateIsEnabled() {
        assertTrue(TrailGate.ENABLED)
    }
}
